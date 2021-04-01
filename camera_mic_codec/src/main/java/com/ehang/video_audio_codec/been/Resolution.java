package com.ehang.video_audio_codec.been;

import android.text.TextUtils;

/**
 * 分辨率
 * Created by KwokSiuWang on 2018/3/19.
 */
public enum Resolution {
    r1280x720("720p", 1280, 720),
    r1920x1080("2k", 1920, 1080),
    r3840x2160("4k", 3840, 2160);

    private String text;
    private int width;
    private int height;

    Resolution(String s, int width, int height) {
        text = s;
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return text;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public static String[] getResolutionArray() {
        return new String[]{
                Resolution.r1280x720.toString(),
                Resolution.r1920x1080.toString(),
                Resolution.r3840x2160.toString(),
        };
    }

    public static Resolution getResolution(String text) {
        if (TextUtils.equals(text, r1920x1080.toString())) {
            return r1920x1080;
        } else if (TextUtils.equals(text, r3840x2160.toString())) {
            return r3840x2160;
        } else {
            return r1280x720;
        }
    }
}
