package com.ksw.ffmpegtest

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaMuxer
import android.os.Environment
import android.view.Surface
import com.ehang.commonutils.codec.CodecUtil
import com.ehang.commonutils.debug.Log
import com.ehang.commonutils.ui.ToastUtil
import com.ehang.commonutils.ui.TomApplication
import com.ehang.video_audio_codec.CameraCodec
import com.ehang.video_audio_codec.EhAudioCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

typealias ErrorListener = (errorMessage: String) -> Unit

/**
 * 把音视频的处理都放在这里了。本质上只需要给外部提供四个方法，分别是开始和停止视频聊天，开始和停止录像。
 *  Created by kwoksiuwang on 12/30/20!!!
 */
object ChatManager : DataReceiver {
    private val TAG = "ChatManager"
    private val myScope = MainScope() + CoroutineName("ChatService")

    private val audioCoder: EhAudioCodec by lazy { EhAudioCodec() }
    private val cameraCodec: CameraCodec by lazy { CameraCodec() }

    /**
     * 是否正在视频聊天
     */
    private val isChatting = AtomicBoolean(false)

    /**
     * 是否正在录像
     */
    private val isRecording = AtomicBoolean(false)

    private val isClosingCoder = AtomicBoolean(false)

    /**
     * ---
     * 视频聊天相关
     */
    private val chatLock = ReentrantLock()

    private val videoFrameRateController: FrameRateController by lazy { FrameRateController() }
    private val audioFrameRateController: FrameRateController by lazy { FrameRateController() }

    private val mediaPackets = Channel<MediaPacket>(200)

    private var firstTimeInterval: Long = 0
    private var lastResetConnectTime: Long = 0
    private var lastSendBitrateTime = 0L

    private val TYPE_AUDIO: Byte = 0
    private val TYPE_VIDEO: Byte = 1

    /**
     * 发送数据包的线程
     */
    private var sendMediaPacketJob: Job? = Job()

    /**
     * 上次收到数据的时间
     */
    private var lastReceiveDataTime: Long = 0

    private var checkReceiveDataJob: Job? = Job()

    /**
     * ------
     * 录像相关
     */

    /**
     * 开始视频聊天方法中用的锁
     */
    private val recordLock = ReentrantLock()

    private lateinit var muxer: MediaMuxer
    private var muxerStartLock = ReentrantLock()
    private var isInitVideoTrack = false
    private var isInitAudioTrack = false
    private var videoTrackId = -1
    private var audioTrackId = -1

    /**
     * 录像地址
     */
    private val recordPath =
        TomApplication.getContext().getExternalFilesDir("video")?.absolutePath!!
    private var recordFilePath = ""
    private var recordStartTime = 0L

    /**
     * 保持视频时候用的时间格式~。~
     */
    private val timeFormat: SimpleDateFormat by lazy {
        SimpleDateFormat(
            "yyyy:MM:dd_HH:mm:ss",
            Locale.CHINA
        )
    }

    private var chatErrorListener: ErrorListener? = null
    private var recordErrorListener: ErrorListener? = null

    private lateinit var arrayMerge: ArrayMerge

    init {
        myScope.launch { initRecordPath() }
    }

    /**
     * 开始视频聊天
     */
    fun startChat(surface: Surface, errorListener: ErrorListener) {
        chatLock.lock()
        if (!isChatting.get()) {
            Log.d(TAG, "[startChat] 开始视频聊天")
            chatErrorListener = errorListener
            if (!isRecording.get()) {
                //如果没有在录像，那就开启音视频的编码
//                startVideoAndAudioEncode()
            }

            recordFilePath =
                recordPath + "/javavideo_${timeFormat.format(System.currentTimeMillis())}.264"
            val file = File(recordFilePath)
            if (!file.exists()) {
                file.createNewFile()
            }
            outputStream = file.outputStream()

            //只有在视频聊天中，需要接收音视频数据解码。
            startVideoAudioReceiveAndDecode(surface)
            isChatting.set(true)
        } else {
            Log.d(TAG, "[startChat] 已经在视频聊天中")
        }
        chatLock.unlock()
    }

    lateinit var outputStream: OutputStream

    /**
     * 停止视频聊天
     */
    fun stopChat() {
        if (isChatting.getAndSet(false)) {
            Log.d(TAG, "[stopChat] 开始")
            chatErrorListener = null

            videoFrameRateController.stop()
            audioFrameRateController.stop()

            (TomApplication.getContext()
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager).stopBluetoothSco()

            lastReceiveDataTime = 0
            firstTimeInterval = 0
            lastSendBitrateTime = 0



            if (!isRecording.get()) {
                stopCoder()
                Log.d(TAG, "[stopChat] 没有正在录像，已经停止音视频编码")
            } else {
                Log.d(TAG, "[stopChat] 正在录像，不需要停止音视频编码")
            }
            sendMediaPacketJob?.cancel()
            checkReceiveDataJob?.cancel()
        } else {
            Log.d(TAG, "[stopChat] 并没有在视频聊天中，不需要停止")
        }
    }


    /**
     * 开始录像
     */
    fun startRecord(errorListener: ErrorListener? = null) {
        myScope.launch {
            recordLock.lock()
            if (!isRecording.get()) {
                recordLock.lock()
                Log.d(TAG, "[startRecord] 开始")
                recordErrorListener = errorListener

                val timeStamp = System.currentTimeMillis() / 1000
                val timeStampMd5 =
                    CodecUtil.md5("E${timeStamp}H".toByteArray()).toLowerCase(Locale.ROOT)
                Log.d(TAG, "TimeStamp: $timeStamp md5 : $timeStampMd5")

                //做好读写文件的准备
                calcAndCleanSpace()

                withContext(Dispatchers.IO) {
                    recordFilePath =
                        recordPath + "/video_${timeFormat.format(System.currentTimeMillis())}.mp4"
                    muxer = MediaMuxer(recordFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    //修正录像之后视频的方向。
                    muxer.setOrientationHint(270)
                }

                if (!isChatting.get()) {
                    //如果没有在聊天，那就开启音视频的编码
                    startVideoAndAudioEncode()
                }
                recordStartTime = System.currentTimeMillis()
                isInitVideoTrack = false
                isInitAudioTrack = false

                isRecording.set(true)
                Log.d(TAG, "[startRecord] 开始")
            } else {
                Log.d(TAG, "[startRecord] 反复进入")
            }
            recordLock.unlock()
        }
    }

    /**
     * 停止录像
     */
    fun stopRecord() {
        if (isRecording.getAndSet(false)) {
            Log.d(TAG, "[startRecord] 开始")
            recordErrorListener = null
            isInitAudioTrack = false
            isInitVideoTrack = false

            if (!isChatting.get()) {
                stopCoder()
            }
            try {
                muxer.stop()
                muxer.release()
            } catch (e: Exception) {
                Log.d(TAG, "[startRecord] muxer stop fail $e")
            }
            Log.d(TAG, "[startRecord] 完成, 录像文件地址:$recordFilePath")
        } else {
            Log.d(TAG, "[stopRecord] 反复进入")
        }
    }

    private fun stopCoder() {
        //防止多次进入
        if (!isClosingCoder.getAndSet(true)) {
            Log.d(TAG, "[stopCoder] 开始")
            try {
                cameraCodec.stop(false)
            } catch (e: Exception) {
                Log.d(TAG, "[stopCoder] 停止视频编解码器失败 : $e")
            }
            try {
                audioCoder.stop()
            } catch (e: Exception) {
                Log.d(TAG, "[stopCoder] 停止音频编解码器失败 : $e")
            }
            Log.d(TAG, "[stopCoder] 结束")
            isClosingCoder.set(false)
        } else {
            Log.d(TAG, "[stopCoder] 正在多次进入")
        }
    }

    /**
     * 停止所有
     */
    private fun stopAll() {
        stopChat()
        stopRecord()
    }


    /**
     * 开启音视频编码
     */
    private fun startVideoAndAudioEncode() {
        Log.d(TAG, "[startVideoAndAudioEncode] 开启音视频编码")
        cameraCodec.startPreviewAndEncode(null, object : CameraCodec.OnVideoDataAvailableListener {
            override fun onAvailable(date: ByteArray?) {
                if (isChatting.get()) {
//                    Log.d(TAG, "[startVideoAndAudioEncode][onAvailable]")
                    mediaPackets.offer(MediaPacket(MediaPacket.Type.VIDEO, date))
                }
            }

            override fun onAvailableRaw(sendbuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
                //写入文件
                if (isRecording.get()) {
                    if (!isInitVideoTrack) {
                        muxerStartLock.lock()
                        videoTrackId = muxer.addTrack(cameraCodec.outFormat)
                        if (isInitAudioTrack) {
                            muxer.start()
                            isInitVideoTrack = true
                            Log.d(TAG, "[startVideoAndAudioEncode][camera] muxer start")
                        } else {
                            isInitVideoTrack = true
                            Log.d(TAG, "[startVideoAndAudioEncode][camera] muxer not start")
                        }
                        muxerStartLock.unlock()
                    } else if (isInitAudioTrack) {
//                            Log.d(TAG, "write video")
                        muxer.writeSampleData(videoTrackId, sendbuf, bufferInfo)
                    }
//                        Log.d(TAG, "BUFFER INFO ${info.flags} ${info.presentationTimeUs}")

                }
            }

        }) {
            Log.d(TAG, "[startVideoAndAudioEncode] error : $it")
//            stopAll()
            sendErrorMsg(it)
        }

        audioCoder.setOnErrorListener {
            Log.d(TAG, "audio error $it")
            Log.flush()
            sendErrorMsg(it)
        }

        audioCoder.startRecordAndEncoder(object : EhAudioCodec.OnAudioDataAvailableListener {
            override fun onAvailable(sendbuf: ByteArray) {
                if (isChatting.get()) {
                    mediaPackets.offer(
                        MediaPacket(
                            MediaPacket.Type.AUDIO,
                            sendbuf
                        )
                    )
                }
            }

            override fun onMediaCodecDataAvailable(
                sendbuf: ByteBuffer,
                bufferInfo: MediaCodec.BufferInfo
            ) {
                //写入文件
                if (isRecording.get()) {
                    if (!isInitAudioTrack) {
                        muxerStartLock.lock()
                        audioTrackId = muxer.addTrack(audioCoder.outputFormt)
                        if (isInitVideoTrack) {
                            muxer.start()
                            isInitAudioTrack = true
                            Log.d(TAG, "[startVideoAndAudioEncode][audio] muxer start")
                        } else {
                            isInitAudioTrack = true
                            Log.d(TAG, "[startVideoAndAudioEncode][audio] muxer not start")

                        }
                        muxerStartLock.unlock()
                    } else if (isInitVideoTrack) {
                        muxer.writeSampleData(audioTrackId, sendbuf, bufferInfo)

                    }
                }
            }

        })
    }


    private fun startMediaPacketSendThread() {
    }

    private fun startCheckDataConnect() {
        lastReceiveDataTime = System.currentTimeMillis()
        checkReceiveDataJob = myScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (isChatting.get() && lastReceiveDataTime != 0L && System.currentTimeMillis() - lastReceiveDataTime > 12000) {
                    Log.d(TAG, "12秒收不到消息，断开")
                    sendErrorMsg("视频通话超时")
                }
                delay(1000)
            }
        }
    }


    /**
     * 开始接收音视频数据，并且接收音视频解码
     */
    private fun startVideoAudioReceiveAndDecode(surface: Surface) {
        cameraCodec.startDecode(surface) {
            Log.d(TAG, "camera Decode fall $it")
            Log.flush()
            sendErrorMsg(it)
        }

        arrayMerge = ArrayMerge(byteArrayOf(0, 0, 0, 1))
        arrayMerge.setOnDataAvailableListener {
//            Log.d("setOnDataAvailableListener ${it.size}")
            cameraCodec.putFrameData(it)
        }

//        myScope.launch(Dispatchers.IO) {
////            val f = File(recordPath + "/1.h264")
////            val f = File(recordPath + "/javavideo_2021:03:19_11:40:04.264")
//            val f = File(recordPath + "/javavideo_2021:03:23_10:33:51.264")
//            val inputStream = f.inputStream()
//            while (true) {
//                val byteArray = ByteArray(1024)
//                val result = inputStream.read(byteArray)
//                if (result == -1) {
//                    arrayMerge.stop()
//                    stopChat()
//                    break
//                }
//                arrayMerge.putBytes(byteArray)
//                Log.d("send bytes : ${byteArray.size}")
//                delay(40)
//            }
//        }

//        initVideoChatReceiveListener()

        audioCoder.startDecoder(false)
    }

    /**
     * 视频聊天中的音视频接收在此初始化。
     */
    private fun initVideoChatReceiveListener() {
        Log.d(TAG, "[initVideoChatReceiveListener] 初始化视频聊天监听")
        audioFrameRateController.setOnFrameValidListener { frameData ->
            myScope.launch {
                audioCoder.putDecodeAudioBytes(
                    Arrays.copyOfRange(
                        frameData.data,
                        1,
                        frameData.data.size
                    )
                )
            }
        }
        audioFrameRateController.start()
        videoFrameRateController.setOnFrameValidListener { frameData ->
            myScope.launch {
                cameraCodec.putFrameData(
                    Arrays.copyOfRange(
                        frameData.data,
                        1,
                        frameData.data.size
                    )
                )
            }
        }
        videoFrameRateController.start()
    }

    private fun jointData(type: Byte, data: ByteArray): ByteArray {
        val result = ByteArray(data.size + 1)
        result[0] = type
        System.arraycopy(data, 0, result, 1, data.size)
        return result
    }


    private suspend fun calcAndCleanSpace() {
        withContext(Dispatchers.IO) {
            val space = Environment.getExternalStorageDirectory().freeSpace
//        Log.d("剩余存储空间 - $space")
            //空间小于1G时，删除最早的一个文件
            if (space < 5L * 1024 * 1024 * 1024) {
                val file = File(recordPath)
                val fileList = file.listFiles().toList()
                if (fileList.isNotEmpty()) {
                    Collections.sort(fileList)
                    fileList[0].delete()
                    Log.d("${fileList}中删除${fileList[0]}")
                }
            }
        }

    }

    private fun sendErrorMsg(msg: String) {
        chatErrorListener?.let { it(msg) }
        recordErrorListener?.let { it(msg) }
    }

    /**
     * 初始化录像存放文件夹
     */
    private suspend fun initRecordPath() =
        withContext(Dispatchers.IO) {
            try {
                val file = File(recordPath)
                if (!file.exists()) {
                    Log.d(TAG, "创建录像文件夹 ${file.absolutePath}")
                    file.mkdirs()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }

    override fun receiver1() {
    }

    override fun receiver2(bytes: ByteArray) {
        myScope.launch {
            if (isChatting.get()) {
                outputStream.write(bytes)
                cameraCodec.putFrameData(bytes)
//                Log.d("receiver2 ${bytes.size}")
//                arrayMerge.putBytes(bytes)
            }
        }
    }
}