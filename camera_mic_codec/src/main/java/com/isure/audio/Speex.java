package com.isure.audio;

public class Speex {

    /* quality
     * 1 : 4kbps (very noticeable artifacts, usually intelligible)
     * 2 : 6kbps (very noticeable artifacts, good intelligibility)
     * 4 : 8kbps (noticeable artifacts sometimes)
     * 6 : 11kpbs (artifacts usually only noticeable with headphones)
     * 8 : 15kbps (artifacts not usually noticeable)
     *
     *    speex_echo_state_init
     *    speex_echo_cancellation    发送方
     *
     *    这两个是speex实现回声消除的算法接口
     */
    private static final int DEFAULT_COMPRESSION = 8;

    public Speex() {
    }

    public int init() {
        int ret;
        ret = open(DEFAULT_COMPRESSION);
        return ret;
    }

    static {
        System.loadLibrary("speex");
    }

    public native int open(int compression);

    public native int getFrameSize();

    public native int decode(byte encoded[], short lin[], int size);

    public native int encode(short lin[], int offset, byte encoded[], int size);

    public native void close();


}