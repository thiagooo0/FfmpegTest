package com.ksw.ffmpegtest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.TextView
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {

    val player: FFVideoPlayer by lazy { findViewById(R.id.ff_video_player) }
    val ijkplayer: IjkPlayer by lazy { findViewById(R.id.ijkplayer) }
    val myPlayer: SurfaceView by lazy { findViewById(R.id.myplayer) }
    val timeFormat = SimpleDateFormat("MM:dd-hh:mm")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val path = getExternalFilesDir("video")!!.absolutePath + "/video_${
            timeFormat.format(System.currentTimeMillis())
        }.264"
        //ffmpeg -i http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4 -c:v copy -bsf:v h264_mp4toannexb -an  1.h264
//        val videoPath = "rtsp://rtsp-v3-spbtv.msk.spbtv.com/spbtv_v3_1/214_110.sdp"
        val videoPath = "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"
        // Example of a call to a native method
        findViewById<TextView>(R.id.tv_play).setOnClickListener {
//            player.play("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov", path)
//           player.play("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4",path)
            ChatManager.startChat(myPlayer.holder.surface) {
                Log.d("MainActivity", "chat error $it")
            }
            player.play(videoPath, path, ChatManager)

//           player.play("rtmp://171.109.118.191:10085/hls/P329467500033B", path)
//           ijkplayer.setPath("rtmp://171.109.118.191:10085/hls/P329467500033B")
//            ijkplayer.setPath(videoPath)
//            ijkplayer.load()
        }

        findViewById<TextView>(R.id.btn_codec_info).setOnClickListener {
            Log.d("MainActivity", "aavcodec info : ${FFMPEG.avcodecinfo()}")
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
//            listOf(
//                "avcodec",
//                "avfilter",
//                "avformat",
//                "avutil",
//                "swresample",
//                "swscale",
//                "native-lib"
//            ).forEach {
//                System.loadLibrary(it)
//            }
            System.loadLibrary("native-lib")
        }
    }
}