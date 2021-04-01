package com.ksw.ffmpegtest;

import android.util.Log;

/**
 * Created by kwoksiuwang on 3/18/21!!!
 */
public interface DataReceiver {
    public static String TAG = "DataReceiver";

    public void receiver1();

    public void receiver2(byte[] bytes);
}
