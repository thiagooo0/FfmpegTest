package com.ksw.ffmpegtest

import android.view.Surface

/**
 *  Created by kwoksiuwang on 1/27/21!!!
 */
object FFMPEG {
    external fun render(url: String, surface: Surface)

    external fun urlprotocolinfo(): String

    external fun avcodecinfo(): String

    external fun play(url: String, filePath:String, surface: Surface,dataReceiver: DataReceiver): Int

    external fun test(dataReceiver: DataReceiver)
    external fun connect(url: String)

    external fun readBytes(data: ByteArray): Int

    external fun disConnect()
}