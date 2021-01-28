package com.ksw.ffmpegtest

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import kotlin.concurrent.thread

/**
 *  Created by kwoksiuwang on 1/27/21!!!
 */
class FFVideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    SurfaceView(context, attrs, defStyleAttr) {

    fun play(url: String) {
        thread {
            Log.d("FFVideoPlayer", "url protocol info : ${FFMPEG.urlprotocolinfo()} ")
            FFMPEG.render(url, holder.surface)
        }
    }



}