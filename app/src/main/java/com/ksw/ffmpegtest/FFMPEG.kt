package com.ksw.ffmpegtest

import android.view.Surface

/**
 *  Created by kwoksiuwang on 1/27/21!!!
 */
object FFMPEG {
    external fun render(url: String, surface: Surface)

    external fun urlprotocolinfo(): String
}