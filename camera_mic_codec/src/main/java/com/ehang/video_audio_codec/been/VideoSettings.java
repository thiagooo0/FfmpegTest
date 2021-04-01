package com.ehang.video_audio_codec.been;


import android.content.Context;
import android.content.SharedPreferences;

/**
 * 视频聊天设置
 * 单例
 * Created by KwokSiuWang on 2018/3/19.
 */

public class VideoSettings {
    private static final String VIDEO_SETTINGS_CACHE = "videoSettingsCache";
    private static final String RESOLUTION = "resolution";
    private static final String CODE_TYPE = "codeType";
    private static final String BIT_RATE = "bitRate";

    private static VideoSettings videoSettings;

    private Resolution resolution = Resolution.r1920x1080;
    private CodecType codecType = CodecType.H265;

    /**
     * 比特率 单位：kb/s
     */
    private int bitRate = 4000;

    private VideoSettings() {
    }

    public static VideoSettings getInstance() {
        if (null == videoSettings) {
            synchronized (VideoSettings.class) {
                if (null == videoSettings) {
                    videoSettings = new VideoSettings();
                }
            }
        }
        return videoSettings;
    }

    /**
     * 更新数据
     */
    public void update(Resolution resolution, CodecType codecType, int bitRate) {
        this.resolution = resolution;
        this.codecType = codecType;
        this.bitRate = bitRate;
    }

    public void save(Context context) {
        new Thread(() -> {
            SharedPreferences.Editor editor = context.getSharedPreferences(VIDEO_SETTINGS_CACHE, Context.MODE_PRIVATE).edit();
            editor.putString(RESOLUTION, resolution.toString());
            editor.putString(CODE_TYPE, codecType.toString());
            editor.putInt(BIT_RATE, bitRate);
            editor.apply();
        }).start();
    }

    public void getCache(Context context) {
        new Thread(() -> {
            SharedPreferences sharedPreferences = context.getSharedPreferences(VIDEO_SETTINGS_CACHE, Context.MODE_PRIVATE);
            update(
                    Resolution.getResolution(sharedPreferences.getString(RESOLUTION, resolution.toString())),
                    CodecType.getCodeType(sharedPreferences.getString(CODE_TYPE, codecType.toString())),
                    sharedPreferences.getInt(BIT_RATE, bitRate));
        }).start();
    }

    public void clean(Context context) {
        new Thread(() -> {
            SharedPreferences.Editor editor = context.getSharedPreferences(VIDEO_SETTINGS_CACHE, Context.MODE_PRIVATE).edit();
            editor.remove(RESOLUTION);
            editor.remove(CODE_TYPE);
            editor.remove(BIT_RATE);
            editor.apply();
        }).start();
    }

    public Resolution getResolution() {
        return resolution;
    }

    public void setResolution(Resolution resolution) {
        this.resolution = resolution;
    }

    public CodecType getCodecType() {
        return codecType;
    }

    public void setCodecType(CodecType codecType) {
        this.codecType = codecType;
    }

    public int getBitRate() {
        return bitRate;
    }

    public int getBitRateInBit() {
        return bitRate * 1000;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }
}

