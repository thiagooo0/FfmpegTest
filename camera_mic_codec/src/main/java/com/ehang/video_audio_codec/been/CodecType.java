package com.ehang.video_audio_codec.been;

import android.media.MediaFormat;
import android.text.TextUtils;

/**
 * 解码方式
 * Created by KwokSiuWang on 2018/3/19.
 */

public enum CodecType {
    H265("H.265", MediaFormat.MIMETYPE_VIDEO_HEVC),
    H264("H.264", MediaFormat.MIMETYPE_VIDEO_AVC);

    private String text;
    private String type;

    CodecType(String s, String type) {
        text = s;
        this.type = type;
    }

    @Override
    public String toString() {
        return text;
    }

    public String getType() {
        return type;
    }

    public static String[] getCodecTypeArray() {
        return new String[]{
                H265.toString(),
                H264.toString()
        };
    }

    public static CodecType getCodeType(String text) {
        if (TextUtils.equals(text, H265.toString())) {
            return H265;
        } else if (TextUtils.equals(text, H264.toString())) {
            return H264;
        } else {
            return H265;
        }
    }
}
