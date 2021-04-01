package com.ehang.video_audio_codec;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;

import com.ehang.commonutils.debug.Log;
import com.ehang.commonutils.io.SingleThreadExecutor;
import com.ehang.commonutils.ui.TomApplication;
import com.isure.audio.Speex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class EhAudioCodec {
    private static final String TAG = "EhAudioCodec";
    private final int SAMPLE_RATE = 44100;//8000;
    private static final int BIT_RATE = 64000;
    private final int IN_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private final int OUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private final int ENCODE_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private boolean recordStop = false;
    private BlockingQueue<byte[]> decodeQueue = new LinkedBlockingQueue<>(30);
    private BlockingQueue<short[]> inputShortBufferQueue = new LinkedBlockingQueue<>(30);
    private BlockingQueue<byte[]> inputBufferQueue = new LinkedBlockingQueue<>(30);
    private OnErrorListener errorListener;
    private final int SINGLE_SEND_FRAME_COUNT = 10;
    private SingleThreadExecutor singleThreadExecutor = new SingleThreadExecutor();
    private static final int PCM_MULTIPLE = 10;
    private MediaCodec mediaCodec;
    /**
     * Flag that indicate this encoder is capturing now.
     */
    protected volatile boolean mIsCapturing;
    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]

    private HandlerThread codecThread;
    private Handler codecHandler;

    public EhAudioCodec() {
        codecThread = new HandlerThread("AudioCodec");
        codecThread.start();
        codecHandler = new Handler(codecThread.getLooper());
    }

    public long tempsize = 0;
    public long size = 0;

    /**
     * 开始录音，并将录音数据通过编码器编码后回调给应用层发送出去
     *
     * @param audioDataAvailableListener 回调编码成功后的数据
     */
    public void startRecordAndEncoder(OnAudioDataAvailableListener audioDataAvailableListener) {
        try {
            Speex encoder = new Speex();
            encoder.init();

            recordStop = false;

            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
//                    Log.d("TESSST!!!", Thread.currentThread().getName());
                    if (recordStop) {
                        Log.d("已经停止录像 EhAudioCodec onInputBufferAvailable");
                        return;
                    }
                    ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    if (inputBuffer != null) {
                        try {
//                            byte[] data = inputBufferQueue.take();
//                            inputBuffer.put(data);
//                            codec.queueInputBuffer(index, 0, data.length, getPTSUs(), 0);

                            short[] data = inputShortBufferQueue.take();
                            if (!recordStop) {
                                inputBuffer.asShortBuffer().put(data);
                                codec.queueInputBuffer(index, 0, data.length * 2, getPTSUs(), 0);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }


                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    if (recordStop) {
                        Log.d("已经停止录像 EhAudioCodec onOutputBufferAvailable");
                        return;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    } else {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
//                        tempsize = outputBuffer.remaining();
//                        size += tempsize;
//                        Log.d("get audio buffer size:" + tempsize + " totalsize:" + size);
                        audioDataAvailableListener.onMediaCodecDataAvailable(outputBuffer, info);

                    }
                    codec.releaseOutputBuffer(index, info.presentationTimeUs);
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    Log.d(TAG, "onError " + e);
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

                }
            }, codecHandler);
            mediaCodec.start();
            mIsCapturing = true;
            startRecord(encoder, audioDataAvailableListener);
//            drain(audioDataAvailableListener);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 开始解码，并将解码后的数据直接播放
     */
    public void startDecoder(boolean isVoiceCall) {
        int iBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, OUT_CHANNEL_CONFIG, ENCODE_FORMAT);
        AudioTrack mPlayer = new AudioTrack(isVoiceCall ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE, OUT_CHANNEL_CONFIG, ENCODE_FORMAT, iBufferSize,
                AudioTrack.MODE_STREAM);
        mPlayer.setVolume(AudioTrack.getMaxVolume());
        mPlayer.play();
        Speex decoder = new Speex();
        decoder.init();
        startDecodeThread(mPlayer, decoder);
    }

    public void putDecodeAudioBytes(byte[] data) {
        decodeQueue.offer(data);
    }

    /**
     * 解码线程，以阻塞模式获取解码队列中的数据，交给解码器解码，解码完成后直接播放
     *
     * @param mPlayer 音频播放器
     * @param decoder 音频解码器
     */
    private void startDecodeThread(@NonNull AudioTrack mPlayer, @NonNull Speex decoder) {
        recordStop = false;
        decodeQueue.clear();
        singleThreadExecutor = new SingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            while (!recordStop) {
                try {
                    byte[] data = decodeQueue.take();
                    int frameSize = data.length / SINGLE_SEND_FRAME_COUNT;
                    short[] outData = new short[256];
                    for (int i = 0; i < SINGLE_SEND_FRAME_COUNT; i++) {
                        int size = decoder.decode(Arrays.copyOfRange(data, i * frameSize, (i + 1) * frameSize), outData, frameSize);
                        mPlayer.write(increasePCM(outData, PCM_MULTIPLE), 0, size);
                    }
                } catch (InterruptedException e1) {
                    recordStop = true;
                    Log.d("停止音频解码线程");
                } catch (Throwable e) {
                    if (errorListener != null) {
                        errorListener.onError("音频解码失败");
                    }
                    Log.e("音频解码失败", e);
                }
            }
            decodeQueue.clear();
            try {
                mPlayer.stop();
                mPlayer.release();
                decoder.close();
            } catch (Throwable e) {
                Log.e("停止播放或关闭解码器失败", e);
            }
        });
    }

    /**
     * PCM放大
     *
     * @param src      原数据
     * @param multiple 放大倍数
     * @return
     */
    public static short[] increasePCM(short[] src, int multiple) {
        for (int i = 0, dest; i < src.length; i++) {
//            Log.d("test999", String.valueOf(src[i]));
            dest = src[i] * multiple;
            //爆音处理
            dest = Math.max(-32768, Math.min(32767, dest));
            src[i] = (short) dest;
        }
        return src;
    }

    /**
     * 开始录音，并将录音数据通过编码器编码后回调给应用层发送出去
     *
     * @param encoder                    编码器
     * @param audioDataAvailableListener 回调编码以后的数据
     */
    private void startRecord(@NonNull Speex encoder, @NonNull OnAudioDataAvailableListener audioDataAvailableListener) {
        int iBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, IN_CHANNEL_CONFIG, ENCODE_FORMAT);
        ((AudioManager) TomApplication.getContext().getSystemService(Context.AUDIO_SERVICE)).startBluetoothSco();
        final AudioRecord mRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, IN_CHANNEL_CONFIG, ENCODE_FORMAT, iBufferSize);
        mRecord.startRecording();
        new Thread(() -> {
            byte[] frames = new byte[1024];
            short[] bufferShort = new short[160];
            byte[] outData = new byte[bufferShort.length * 2];
            int cursor = 0;
            while (!recordStop) {
                try {
                    int num = mRecord.read(bufferShort, 0, 160);
                    if (num > 0) {
                        short[] bufferShort2 = new short[160];
                        System.arraycopy(bufferShort, 0, bufferShort2, 0, num);
                        inputShortBufferQueue.offer(bufferShort2);

                        int size = encoder.encode(bufferShort, 0, outData, bufferShort.length);
                        System.arraycopy(outData, 0, frames, cursor, size);
                        if (cursor < size * (SINGLE_SEND_FRAME_COUNT - 1)) {
                            cursor += size;
                        } else {
                            audioDataAvailableListener.onAvailable(Arrays.copyOf(frames, cursor + size));
                            cursor = 0;
                        }

                    }
                } catch (Throwable e) {
                    if (errorListener != null) {
                        errorListener.onError("音频编码失败");
                    }
                    Log.e("音频编码失败", e);
                }
            }
            try {
                mRecord.stop();
                mRecord.release();
                encoder.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }

        }).start();
    }

    private byte[] shorts2Bytes(short[] data) {
        byte[] result = new byte[data.length * 2];
        short tmp;
        byte b1, b2;
        for (int i = 0; i < data.length; i++) {
            tmp = (short) (data[i] + 32768);
            b1 = (byte) (tmp % 256);
            b2 = (byte) (tmp / 256);
            result[i * 2] = (byte) (b1 - 128);
            result[i * 2 + 1] = (byte) (b2 - 128);
        }
        return result;
    }

    private short[] bytes2Shorts(byte[] data) {
        short[] result = new short[data.length / 2];
        short temp = 0;
        for (int i = 0; i < data.length; i++) {
            boolean isFront = i % 2 == 0;
            if (isFront) {
                temp = (short) ((data[i] & 0xff) << 8);
            } else {
                temp = (short) ((data[i] & 0xff) | temp);
                result[i / 2] = temp;
                temp = 0;
                result[i / 2] = (short) ((data[i] & 0xff) | (0xff << 8));
            }
        }
        return result;
    }

    public MediaFormat getOutputFormt() {
        return mediaCodec.getOutputFormat();
    }

    public void setOnErrorListener(OnErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public void stop() {
        Log.d("AudioCodec stop");
//        inputBufferQueue.offer(new byte[0]);
        recordStop = true;
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
        ((AudioManager) TomApplication.getContext().getSystemService(Context.AUDIO_SERVICE)).stopBluetoothSco();
        if (singleThreadExecutor != null) {
            singleThreadExecutor.shutdownNow();
            singleThreadExecutor = null;
        }
        errorListener = null;
    }

    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;

    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    public interface OnAudioDataAvailableListener {
        void onAvailable(byte[] sendbuf);

        void onMediaCodecDataAvailable(ByteBuffer sendbuf, MediaCodec.BufferInfo bufferInfo);
    }
}
