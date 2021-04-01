package com.ksw.ffmpegtest;

/**
 * Created by tom on 2018/3/24.
 */

public class MediaPacket {
    /**
     * 0:video,1:audio,2:ping
     */
    public Type type;
    public Object data;

    public MediaPacket(Type type, Object data) {
        this.type = type;
        this.data = data;
    }

    public enum Type {
        VIDEO,
        AUDIO,
        PING,
        BITRATE
    }
}
