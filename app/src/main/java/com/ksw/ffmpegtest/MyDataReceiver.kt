package com.ksw.ffmpegtest

import android.util.Log

/**
 *  Created by kwoksiuwang on 3/18/21!!!
 */
class MyDataReceiver :DataReceiver {
    var TAG = "MyDataReceiver"
    override fun receiver1() {
        Log.d(DataReceiver.TAG, "receiver1 call")
    }

    override fun receiver2(bytes: ByteArray?) {
        Log.d(DataReceiver.TAG, "receiver2 call, bytes length : " + bytes!!.size)
    }
}