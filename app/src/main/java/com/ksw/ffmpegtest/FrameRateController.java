package com.ksw.ffmpegtest;

import com.ehang.commonutils.debug.Log;
import com.ehang.commonutils.io.SingleThreadExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author tom
 */
public class FrameRateController {
    private boolean running = true;
    private BlockingQueue<FrameData> frames = new LinkedBlockingQueue<>();
    private OnFrameValidListener frameValidListener;
    private SingleThreadExecutor frameControlThread;
    /**
     * 帧率控制时间，如果视频过多时则加快20ms播放
     */
    private final static int FRAME_TIME_INTERVAL = 20;
    /**
     * 最低需要的缓存帧数，低于这个数值则暂停播放
     */
    private final static int MIN_FRAMES = 5;
    /**
     * 第一个阈值，超过{@link #MIN_FRAMES} +该阈值时，加快视频播放
     */
    private final static int FIRST_THRESHOLD = 5;
    /**
     * 第二个阈值，超过{@link #MIN_FRAMES} + 该阈值时，丢弃多余的帧
     */
    private final static int SECOND_THRESHOLD = 10;
    private long lastFrameTime = 0;
    private final static int THREAD_SLEEP_TIME = 10;

    public void start() {
        Log.d("帧率控制");
        frameControlThread = new SingleThreadExecutor();
        running = true;
        lastFrameTime = 0;
        frameControlThread.execute(() -> {
            while (running) {
//                Log.d("frames size = " + frames.size());
                if (frames.size() > MIN_FRAMES) {
//                    while (frames.size() > MIN_FRAMES + SECOND_THRESHOLD) {
//                        frames.poll();
//                    }
                    FrameData frameData = frames.poll();
                    if (lastFrameTime != 0) {
                        int sleepTime;
                        int timeInterval = (int) Math.max(20, Math.min(80, frameData.time - lastFrameTime));
                        if (frames.size() > MIN_FRAMES + FIRST_THRESHOLD) {
                            sleepTime = timeInterval - FRAME_TIME_INTERVAL;
                        } else {
                            sleepTime = timeInterval;
                        }
                        try {
                            int threadSleepTime = sleepTime - THREAD_SLEEP_TIME > 0 ? sleepTime - THREAD_SLEEP_TIME : 0;
                            Thread.sleep(threadSleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    lastFrameTime = frameData.time;
                    if (frameValidListener != null) {
                        frameValidListener.onFrameValid(frameData);
                    }
                }
                try {
                    Thread.sleep(THREAD_SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void stop() {
        running = false;
        if (frameControlThread != null) {
            frameControlThread.shutdownNow();
            frameControlThread = null;
        }
        frames.clear();
    }

    public void putFrame(FrameData frame) {
        frames.add(frame);
    }

    public void setOnFrameValidListener(OnFrameValidListener frameValidListener) {
        this.frameValidListener = frameValidListener;
    }

    public interface OnFrameValidListener {
        void onFrameValid(FrameData frameData);
    }
}
