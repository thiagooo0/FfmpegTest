package com.ehang.video_audio_codec;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.ehang.commonutils.debug.Log;
import com.ehang.commonutils.io.SingleThreadExecutor;
import com.ehang.commonutils.ui.ToastUtil;
import com.ehang.commonutils.ui.TomApplication;
import com.ehang.video_audio_codec.been.VideoSettings;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.content.Context.CAMERA_SERVICE;


public class CameraCodec {
//    private String videoCodecType = MediaFormat.MIMETYPE_VIDEO_HEVC;//MediaFormat.MIMETYPE_VIDEO_MPEG4;//
    private String videoCodecType = MediaFormat.MIMETYPE_VIDEO_AVC;//
    private final int width = 640;//3840;//
    private final int height = 360;//2160;//
    private int video_bit_rate = 1000000;
    private int frame_rate = 24;
    private int key_i_frame = 1;
    private int cameraType = 1;
    private Handler mHandler;
    private MediaCodec mEncoder;
    private CameraDevice cameraDevice;
    private int mCount = 0;
    private BlockingQueue<byte[]> decodeQueue = new LinkedBlockingQueue<>(30);
    private boolean running = true;
    private HandlerThread mThreadHandler;
    private Surface encoderSurface;
    private CameraCaptureSession session;
    private byte[] information;
    private SingleThreadExecutor singleThreadExecutor;
    private Surface previewSurface;
    private OnVideoDataAvailableListener videoDataAvailableListener;
    private OnErrorListener errorListener;

    public void log(String content) {
        Log.d("Chat " + " CameraCodec", content);
    }

    public void logE(String content) {
        Log.e("CameraCodec", content);
    }

    public CameraCodec() {
        mThreadHandler = new HandlerThread("CAMERA2");
        mThreadHandler.start();
        mHandler = new Handler(mThreadHandler.getLooper());
//        initParams();
    }

    public boolean switchCamera(SwitchCameraListener switchCameraListener) {
        if (cameraType == 1) {
            cameraType = 0;
        } else {
            cameraType = 1;
        }
        releaseEncodeSource();
        return initCamera(new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                try {
                    encoderSurface = startEncoder(videoDataAvailableListener);
                    CaptureRequest.Builder mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    if (previewSurface != null) {
                        mPreviewBuilder.addTarget(previewSurface);
                    }
                    mPreviewBuilder.addTarget(encoderSurface);
                    camera.createCaptureSession(previewSurface == null ? Collections.singletonList(encoderSurface) : Arrays.asList(previewSurface, encoderSurface), new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            CameraCodec.this.session = session;
                            try {
                                session.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler); //null
                                switchCameraListener.onSuccess();
                            } catch (CameraAccessException e) {
                                errorListener.onError("相机无法使用");
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            errorListener.onError("相机初始化失败");
                            log("init camera " + " failed:");
                        }
                    }, mHandler);
                } catch (CameraAccessException e) {
                    errorListener.onError("相机初始化失败" + e);
                    logE("相机初始化失败" + e);
                } catch (Throwable throwable) {
                    errorListener.onError("编码器创建失败" + throwable);
                    logE("编码器创建失败" + throwable);
                }

            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                log("camera onDisconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                errorListener.onError("相机错误");
                log("camera onError ： " + error);
            }
        });
    }

    /**
     * 开始预览相机视图并编码
     *
     * @param previewSurface             预览相机的surface
     * @param videoDataAvailableListener 编码以后的视频数据
     * @param errorListener              错误监听，包括相机或编码器初始化失败的错误
     * @return true 相机可用，否则不可用
     */
    public boolean startPreviewAndEncode(Surface previewSurface, OnVideoDataAvailableListener videoDataAvailableListener, @NonNull OnErrorListener errorListener) {
        this.previewSurface = previewSurface;
        this.videoDataAvailableListener = videoDataAvailableListener;
        this.errorListener = errorListener;
        return initCamera(new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                try {
                    encoderSurface = startEncoder(videoDataAvailableListener);
                    CaptureRequest.Builder mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    if (previewSurface != null) {
                        mPreviewBuilder.addTarget(previewSurface);
                    }
                    mPreviewBuilder.addTarget(encoderSurface);
                    camera.createCaptureSession(previewSurface == null ? Collections.singletonList(encoderSurface) : Arrays.asList(previewSurface, encoderSurface), new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            CameraCodec.this.session = session;
                            try {
                                log("建立和相机的seesion");
                                session.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler); //null
                            } catch (CameraAccessException e) {
                                errorListener.onError("相机无法使用");
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            errorListener.onError("相机初始化失败");
                            log("init camera " + " failed:");
                        }
                    }, mHandler);
                } catch (CameraAccessException e) {
                    errorListener.onError("相机初始化失败");
                    log("相机初始化失败" + e);
                } catch (Throwable throwable) {
                    if (throwable instanceof MediaCodec.CodecException) {
                        boolean isRecoverable = ((MediaCodec.CodecException) throwable).isRecoverable();
                        boolean isTransient = ((MediaCodec.CodecException) throwable).isTransient();
                        errorListener.onError("编码器创建失败 " + throwable + " isRecoverable:" + isRecoverable + " isTransient:" + isTransient);
                    } else {
                        errorListener.onError("编码器创建失败 " + throwable);
                    }
                    log("编码器创建失败" + throwable);
                }

            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                log("camera onDisconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                errorListener.onError("相机错误");
                log("camera onError ： " + error);
            }
        });
    }

    /**
     * 调整比特率
     *
     * @return 调整后的比特率
     */
    public int judgeBitrate(float percent) {
        if (mEncoder != null) {
            Bundle param = new Bundle();
            video_bit_rate = (int) (percent * video_bit_rate);
            param.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, video_bit_rate);
            mEncoder.setParameters(param);
            log("调整比特率 ： " + percent + ", " + video_bit_rate);
            return video_bit_rate;
        }
        return 0;
    }

    /**
     * 调整比特率
     */
    public void judgeBitrate(int bitrate) {
        if (mEncoder != null) {
            Bundle param = new Bundle();
            video_bit_rate = bitrate;
            param.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
            try {
                mEncoder.setParameters(param);
            } catch (Throwable e) {
                //多线程时会设置一个已经关闭的编码器
                log("设置比特率时编码器已关闭");
            }
            log("调整比特率 ： " + bitrate);
        }
    }

    /**
     * 开始解码并发送到surface显示
     */
    public void startDecode(Surface friendSurface, @NonNull OnErrorListener errorListener) {
        try {
            //根据需要解码的类型创建解码器
            MediaCodec decoder = MediaCodec.createDecoderByType(videoCodecType);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(videoCodecType, width, height);
            decoder.configure(mediaFormat, friendSurface, null, 0); //直接解码送surface显示
            startDecodeThread(decoder, errorListener);
            //开始解码
            decoder.start();
        } catch (Throwable e) {
            log("创建解码器失败" + e);
            errorListener.onError("创建解码器失败");
        }
    }

    /**
     * 先调用{@link CameraCodec#startDecode(Surface, OnErrorListener)}开始解码，然后依次传入帧数据即可在surface中显示
     */
    public void putFrameData(byte[] data) {
        //这里采用offer方式，当解码队列满了后扔掉部分帧
        decodeQueue.offer(data);
    }

    public void stop(boolean needStopThreadHandler) {
        log("CameraCodec stop");
//        mEncoder.signalEndOfInputStream();
        running = false;
        try {
            if (singleThreadExecutor != null) {
                singleThreadExecutor.shutdownNow();
                singleThreadExecutor = null;
            }
            releaseEncodeSource();
            log("CameraCodec stop success");
        } catch (Throwable e) {
            log("stop CameraCodec 出错" + e);
        }
    }

    private void releaseEncodeSource() {
        if (session != null) {
            log("close session");
            session.close();
        }
        if (cameraDevice != null) {
            log("close cameraDevice");
            cameraDevice.close();
        }
        if (encoderSurface != null) {
            log("release encoderSurface");
            encoderSurface.release();
        }
        if (mEncoder != null) {
            log("stop mEncoder");
            mEncoder.release();
        }
        session = null;
        cameraDevice = null;
        encoderSurface = null;
        mEncoder = null;
        mHandler.removeCallbacksAndMessages(null);

        if (mThreadHandler!=null) {
            mThreadHandler.quitSafely();
        }
    }

    /**
     * 获取相机列表，并打开相机，默认打开后置，无后置则打开前置
     *
     * @param cameraStateCallback 相机状态回调
     * @return true 相机可用，否则返回false
     */
    private boolean initCamera(@NonNull CameraDevice.StateCallback cameraStateCallback) {
        try {
            mThreadHandler = new HandlerThread("CAMERA2");
            mThreadHandler.start();
            mHandler = new Handler(mThreadHandler.getLooper());
            CameraManager cameraManager = (CameraManager) TomApplication.getContext().getSystemService(CAMERA_SERVICE);
            if (cameraManager != null) {
                String[] cameraList = cameraManager.getCameraIdList();
                if (cameraList.length == 0 ||
                        ActivityCompat.checkSelfPermission(TomApplication.getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    log("无相机权限或者相机列表为空");
                    return false;
                }
                cameraManager.openCamera(cameraType > 0 && cameraList.length > 1 ? cameraList[1] : cameraList[0], cameraStateCallback, mHandler);
            }
        } catch (CameraAccessException e) {
            ToastUtil.showShortToast("相机未初始化成功");
        }
        return true;
    }

    public long tempsize = 0;
    public long size = 0;

    /**
     * 开始视频编码
     *
     * @param videoDataAvailableListener 回调编码数据
     * @return 编码器的surface
     */
    private Surface startEncoder(OnVideoDataAvailableListener videoDataAvailableListener) throws Throwable {
        try {
            mEncoder = MediaCodec.createEncoderByType(videoCodecType);
        } catch (Throwable e) {
            videoCodecType = MediaFormat.MIMETYPE_VIDEO_MPEG4;
            mEncoder = MediaCodec.createEncoderByType(videoCodecType);
            ToastUtil.showLongToast("该手机不支持H265编码，已自动切换为H264");
            log("该手机不支持H265编码，已自动切换为H264");
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(videoCodecType,
                width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, video_bit_rate);
        mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, key_i_frame);

        try{
            mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }catch (MediaCodec.CodecException e){
            boolean isRecoverable = e.isRecoverable();
            boolean isTransient = e.isTransient();
            log("CodecException来了!! isRecoverable:" + isRecoverable + " isTransient:" + isTransient);
            if(isRecoverable){
                //尝试关闭再重新配置。
                log("尝试重新配置encoder");
                mEncoder.stop();
                mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }else if(isTransient){
                log("重新再来吧1");
                throw e;
            }else {
                log("重新再来吧2");
                throw e;
            }
        }
        mEncoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo bufferInfo) {
                try {
                    if (mEncoder == null) {
                        log("编码器关闭，不再读取数据");
                        return;
                    }
                    ByteBuffer outputBuffer = mEncoder.getOutputBuffer(index);

//                    tempsize = outputBuffer.remaining();
//                    size += tempsize;
//                   log("get video buffer size:" + tempsize + " totalsize:" + size);

                    if (outputBuffer != null) {
                        if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                            //sps pps信息
                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);
                            information = outData;

                            videoDataAvailableListener.onAvailableRaw(outputBuffer, bufferInfo);
                        } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                            //关键帧
                            byte[] outData = new byte[bufferInfo.size + information.length];
                            System.arraycopy(information, 0, outData, 0, information.length);
                            outputBuffer.get(outData, information.length, bufferInfo.size);
                            if (videoDataAvailableListener != null) {
                                videoDataAvailableListener.onAvailable(outData);
                                videoDataAvailableListener.onAvailableRaw(outputBuffer, bufferInfo);
                            }
                        } else {
                            //普通帧
                            //添加将要发送的视频数据
                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);
                            if (videoDataAvailableListener != null) {
                                videoDataAvailableListener.onAvailable(outData);
                                videoDataAvailableListener.onAvailableRaw(outputBuffer, bufferInfo);
                            }
                        }
                    }
                    mEncoder.releaseOutputBuffer(index, false);
                } catch (Throwable e) {
                    log("视频编码错误" + e);
                    errorListener.onError(e.toString());
                }

            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                log("编码器错误 " + e);
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

            }
        }, mHandler);
        Surface surface = mEncoder.createInputSurface();
        mEncoder.start();
        return surface;
    }

    private int queueInputBuffer(MediaCodec codec, ByteBuffer[] inputBuffers, int index) {
        ByteBuffer buffer = inputBuffers[index];
        buffer.clear();
        int size = buffer.limit();
        byte[] zeroes = new byte[size];
        buffer.put(zeroes);//测试时没有使用有效数据，实际应该put编码前有效的音视频数据
        codec.queueInputBuffer(index, 0 /* offset */, size, 0 /* timeUs */, 0);
        return size;
    }

    private void initParams() {
//        VideoSettings settings = VideoSettings.getInstance();
//        height = settings.getResolution().getHeight();
//        width = settings.getResolution().getWidth();
//        video_bit_rate = settings.getBitRateInBit();
//        videoCodecType = settings.getCodecType().getType();
//       log("initParams:" + height + "/" + width + " "
//                + video_bit_rate + " "
//                + videoCodecType);
    }

    private MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();

    /**
     * 以队列的方式解码视频帧数据
     */
    private void startDecodeThread(@NonNull final MediaCodec decoder, @NonNull OnErrorListener errorListener) {
        decodeQueue.clear();
        singleThreadExecutor = new SingleThreadExecutor();
        running = true;
        singleThreadExecutor.execute(() -> {
            while (running) {
                try {
                    byte[] data = decodeQueue.take();
                    //-1表示一直等待；0表示不等待；其他大于0的参数表示等待微秒数
                    int inputBufferIndex = decoder.dequeueInputBuffer(0);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        assert inputBuffer != null;
                        inputBuffer.clear();
                        inputBuffer.put(data, 0, data.length);
//                        log("传入"+data.length+"长的数据");
                        //解码
                        long timestamp = mCount * 1000000 / frame_rate;
                        decoder.queueInputBuffer(inputBufferIndex, 0, data.length, timestamp, 0);
                        mCount++;
                    } else {
//                        log("decode failure inputBufferIndex:" + inputBufferIndex);
                        continue;
                    }

                    int outputBufferIndex = decoder.dequeueOutputBuffer(decodeBufferInfo, 0); //10
                    //循环解码，直到数据全部解码完成
                    while (outputBufferIndex >= 0) {
//                        log("解码完成");
                        decoder.releaseOutputBuffer(outputBufferIndex, true);
                        outputBufferIndex = decoder.dequeueOutputBuffer(decodeBufferInfo, 0);
                    }
                } catch (InterruptedException e) {
                    logE("解码线程已停止");
                    running = false;
                } catch (Throwable e1) {
                    errorListener.onError("视频解码失败" + e1);
                    log("视频解码失败" + e1);
                }
            }
            log("stop decode");
            decodeQueue.clear();
            try {
                decoder.release();
            } catch (Throwable e) {
                log("decoder release 失败" + e);
            }
        });
    }

    public MediaFormat getOutFormat() {
        return mEncoder.getOutputFormat();
    }

    public interface OnVideoDataAvailableListener {
        void onAvailable(byte[] sendbuf);

        void onAvailableRaw(ByteBuffer sendbuf, MediaCodec.BufferInfo bufferInfo);
    }

    /**
     * 前后摄像头切换的监听器
     */
    public interface SwitchCameraListener {
        void onSuccess();
    }
}
