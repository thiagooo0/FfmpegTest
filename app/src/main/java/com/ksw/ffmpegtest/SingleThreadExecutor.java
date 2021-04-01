package com.ksw.ffmpegtest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SingleThreadExecutor {
    private ThreadPoolExecutor threadPoolExecutor;

    public SingleThreadExecutor() {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue(2);
        this.threadPoolExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, queue, Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy());
    }

    public void execute(Runnable runnable) {
        this.threadPoolExecutor.execute(runnable);
    }

    public void shutdownNow() {
        this.threadPoolExecutor.shutdownNow();
    }
}
