package com.ksw.ffmpegtest;

import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class ArrayMerge {
    private byte[] buffer;
    private OnDataAvailableListener onDataAvailableListener;
    private LinkedBlockingQueue<byte[]> dataDealQueue = new LinkedBlockingQueue<>(100);
    private boolean running = true;
    private byte[] headFlag;
    private SingleThreadExecutor dealDataThread = new SingleThreadExecutor();
    private static final String TAG = "MainActivityHVRActivity";

    public ArrayMerge(byte[] headFlag) {
        this.headFlag = headFlag;
        dealDataThread.execute(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        byte[] data = dataDealQueue.take();
                        dealData(data);
//                        Thread.sleep(20);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }


    public final void setOnDataAvailableListener(OnDataAvailableListener var1) {
        onDataAvailableListener = var1;
    }


    public void stop() {
        onDataAvailableListener = null;
        running = false;
        dealDataThread.shutdownNow();
        dataDealQueue.clear();
    }

    private void dealData(byte[] bytes) {
        if (buffer == null) {
            buffer = bytes;
        } else {
            byte[] temp = Arrays.copyOf(buffer, buffer.length + bytes.length);
            System.arraycopy(bytes, 0, temp, buffer.length, bytes.length);
            buffer = temp;
        }

        int startCursor = -1;
        int endCursor = -1;
        byte[] flag = headFlag;
        int i = 0;

        while (i < buffer.length - flag.length) {
            boolean isContains = true;

            for (int j = 0; j < flag.length; j++) {
                if (buffer[i + j] != flag[j]) {
                    isContains = false;
                    break;
                }
            }
//            if (isContains && (buffer[i + 4] != 0x68 && buffer[i + 4] != 0x65 && buffer[i + 4] != 0x28 && buffer[i + 4] != 0x25)) {
//            Log.d(TAG, "arrayMerge 5st byte: " + Integer.toHexString(buffer[i + 4] & 0xff) + " bufferLength:" + buffer.length);
            if (isContains && (buffer[i + 4] == 0x67 || buffer[i + 4] == 0x61 || buffer[i + 4] == 0x40 || buffer[i + 4] == 0x02 || buffer[i + 4] == 0x21 || buffer[i + 4] == 0x17 || buffer[i + 4] == 0x06)) {
//            if (isContains) {
                if (startCursor == -1) {
                    startCursor = i;
                } else {
                    endCursor = i;
                    int offset = 0;
                    byte[] availableData = new byte[endCursor - startCursor - offset];
                    System.arraycopy(buffer, startCursor + offset, availableData, 0, availableData.length);
                    Log.d(TAG, "arrayMerge " + availableData.length + "  5st byte: " + Integer.toHexString(buffer[i + 4] & 0xff));
                    if (onDataAvailableListener != null) {
                        onDataAvailableListener.onAvailable(availableData);
                    }
                    startCursor = -1;
                    --i;
                }
            }
            ++i;
        }
        if (endCursor > 0 && endCursor < buffer.length) {
            byte[] remainData = new byte[buffer.length - endCursor];
            System.arraycopy(buffer, endCursor, remainData, 0, remainData.length);
            buffer = remainData;
        }
    }

    public final void putBytes(byte[] bytes) {
        try {
            if (dataDealQueue != null) {
                dataDealQueue.offer(bytes);
            }
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    public interface OnDataAvailableListener {
        public void onAvailable(byte[] data);
    }
}
