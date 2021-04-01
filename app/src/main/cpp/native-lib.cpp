#include <jni.h>
#include <string>

extern "C"

{
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <libavfilter/avfilter.h>
#include <libavcodec/avcodec.h>
//封装格式处理
#include <libavformat/avformat.h>
//像素处理
#include <libswscale/swscale.h>
#include <unistd.h>

#include <libavutil/imgutils.h>
#include <libavcodec/jni.h>

#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, "(>_<)", format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  "(^_^)", format, ##__VA_ARGS__)


JNIEXPORT void JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_render(JNIEnv *env, jobject instance, jstring url_,
                                      jobject surface) {
    const char *url = env->GetStringUTFChars(url_, 0);

    // 注册。
    av_register_all();
    // 打开地址并且获取里面的内容  avFormatContext是内容的一个上下文
    AVFormatContext *avFormatContext = avformat_alloc_context();
    avformat_open_input(&avFormatContext, url, NULL, NULL);
    avformat_find_stream_info(avFormatContext, NULL);

    // 找出视频流
    int video_index = -1;
    for (int i = 0; i < avFormatContext->nb_streams; ++i) {
        if (avFormatContext->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
            video_index = i;
        }
    }
    // 解码  转换  绘制
    // 获取解码器上下文
    AVCodecContext *avCodecContext = avFormatContext->streams[video_index]->codec;
    // 获取解码器
    AVCodec *avCodec = avcodec_find_decoder(avCodecContext->codec_id);
    // 打开解码器
    if (avcodec_open2(avCodecContext, avCodec, NULL) < 0) {
        // 打开失败。
        return;
    }
    // 申请AVPacket和AVFrame，
    // 其中AVPacket的作用是：保存解码之前的数据和一些附加信息，如显示时间戳（pts）、解码时间戳（dts）、数据时长，所在媒体流的索引等；
    // AVFrame的作用是：存放解码过后的数据。
    AVPacket *avPacket = (AVPacket *) av_malloc(sizeof(AVPacket));
    av_init_packet(avPacket);
    // 分配一个AVFrame结构体,AVFrame结构体一般用于存储原始数据，指向解码后的原始帧
    AVFrame *avFrame = av_frame_alloc();
    //分配一个AVFrame结构体，指向存放转换成rgb后的帧
    AVFrame *rgb_frame = av_frame_alloc();
    // rgb_frame是一个缓存区域，所以需要设置。
    // 缓存区
    uint8_t *out_buffer = (uint8_t *) av_malloc(
            avpicture_get_size(AV_PIX_FMT_RGBA, avCodecContext->width, avCodecContext->height));
    // 与缓存区相关联，设置rgb_frame缓存区
    avpicture_fill((AVPicture *) rgb_frame, out_buffer, AV_PIX_FMT_RGBA, avCodecContext->width,
                   avCodecContext->height);
    // 原生绘制，需要ANativeWindow
    ANativeWindow *pANativeWindow = ANativeWindow_fromSurface(env, surface);
    if (pANativeWindow == 0) {
        // 获取native window 失败
        return;
    }
    SwsContext *swsContext = sws_getContext(
            avCodecContext->width,
            avCodecContext->height,
            avCodecContext->pix_fmt,
            avCodecContext->width,
            avCodecContext->height,
            AV_PIX_FMT_RGBA,
            SWS_BICUBIC,
            NULL,
            NULL,
            NULL);
    // 视频缓冲区
    ANativeWindow_Buffer native_outBuffer;
    // 开始解码了。
    int frameCount;
    while (av_read_frame(avFormatContext, avPacket) >= 0) {
        if (avPacket->stream_index == video_index) {
            LOGI("av_read_frame");
            avcodec_decode_video2(avCodecContext, avFrame, &frameCount, avPacket);
            // 当解码一帧成功过后，我们转换成rgb格式并且绘制。
            if (frameCount) {
                ANativeWindow_setBuffersGeometry(pANativeWindow, avCodecContext->width,
                                                 avCodecContext->height, WINDOW_FORMAT_RGBA_8888);
                // 上锁
                ANativeWindow_lock(pANativeWindow, &native_outBuffer, NULL);
                // 转换为rgb格式
                sws_scale(swsContext, (const uint8_t *const *) avFrame->data, avFrame->linesize, 0,
                          avFrame->height, rgb_frame->data, rgb_frame->linesize);
                uint8_t *dst = (uint8_t *) native_outBuffer.bits;
                int destStride = native_outBuffer.stride * 4;
                uint8_t *src = rgb_frame->data[0];
                int srcStride = rgb_frame->linesize[0];
                for (int i = 0; i < avCodecContext->height; ++i) {
                    memcpy(dst + i * destStride, src + i * srcStride, srcStride);
                }
                ANativeWindow_unlockAndPost(pANativeWindow);
//                usleep(1000 * 16);
            }
        }
        av_free_packet(avPacket);

    }

    ANativeWindow_release(pANativeWindow);
    av_frame_free(&avFrame);
    av_frame_free(&rgb_frame);
    avcodec_close(avCodecContext);
    avformat_free_context(avFormatContext);


    env->ReleaseStringUTFChars(url_, url);
}

JNIEXPORT jstring JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_urlprotocolinfo(JNIEnv *env, jobject instance) {
    char info[40000] = {0};
    av_register_all();
    struct URLProtocol *pup = NULL;
    struct URLProtocol **p_temp = &pup;
    avio_enum_protocols((void **) p_temp, 0);
    while ((*p_temp) != NULL) {
        sprintf(info, "%sInput: %s\n", info, avio_enum_protocols((void **) p_temp, 0));
    }
    pup = NULL;
    avio_enum_protocols((void **) p_temp, 1);
    while ((*p_temp) != NULL) {
        sprintf(info, "%sInput: %s\n", info, avio_enum_protocols((void **) p_temp, 1));
    }

    return env->NewStringUTF(info);
}

JNIEXPORT jstring JNICALL
Java_com_wepon_ffmpeg4cmake_MainActivity_avformatinfo(JNIEnv *env, jobject instance) {

    char info[40000] = {0};

    av_register_all();

    AVInputFormat *if_temp = av_iformat_next(NULL);
    AVOutputFormat *of_temp = av_oformat_next(NULL);
    while (if_temp != NULL) {
        sprintf(info, "%sInput: %s\n", info, if_temp->name);
        if_temp = if_temp->next;
    }
    while (of_temp != NULL) {
        sprintf(info, "%sOutput: %s\n", info, of_temp->name);
        of_temp = of_temp->next;
    }
    return env->NewStringUTF(info);
}

JNIEXPORT jstring JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_avcodecinfo(JNIEnv *env, jobject instance) {
    char info[40000] = {0};

    av_register_all();

    AVCodec *c_temp = av_codec_next(NULL);

    while (c_temp != NULL) {
        if (c_temp->decode != NULL) {
            sprintf(info, "%sdecode:", info);
        } else {
            sprintf(info, "%sencode:", info);
        }
        switch (c_temp->type) {
            case AVMEDIA_TYPE_VIDEO:
                sprintf(info, "%s(video):", info);
                break;
            case AVMEDIA_TYPE_AUDIO:
                sprintf(info, "%s(audio):", info);
                break;
            default:
                sprintf(info, "%s(other):", info);
                break;
        }
        sprintf(info, "%s[%10s]\n", info, c_temp->name);
        c_temp = c_temp->next;
    }

    return env->NewStringUTF(info);
}

JNIEXPORT jstring JNICALL
Java_com_wepon_ffmpeg4cmake_MainActivity_avfilterinfo(JNIEnv *env, jobject instance) {
    char info[40000] = {0};
    avfilter_register_all();

    AVFilter *f_temp = (AVFilter *) avfilter_next(NULL);
    while (f_temp != NULL) {
        sprintf(info, "%s%s\n", info, f_temp->name);
        f_temp = f_temp->next;
    }
    return env->NewStringUTF(info);
}

JNIEXPORT jint JNICALL Java_com_ksw_ffmpegtest_FFMPEG_play
        (JNIEnv *env, jobject obj, jstring input_jstr, jstring file_path, jobject surface,
         jobject data_receiver) {
    LOGI("play");
    // sd卡中的视频文件地址,可自行修改或者通过jni传入
    const char *file_name = env->GetStringUTFChars(input_jstr, NULL);
    LOGI("file_name:%s\n", file_name);
    //4。0之后，不需要调用此方法进行注册了。
    av_register_all();
    LOGI("av_register_all");

    AVFormatContext *pFormatCtx = avformat_alloc_context();
    LOGI("avformat_alloc_context");

    // Open video file
    if (avformat_open_input(&pFormatCtx, file_name, NULL, NULL) != 0) {

        LOGE("Couldn't open file:%s\n", file_name);
        return -1; // Couldn't open file
    }
    LOGI("avformat_open_input");

    // Retrieve stream information
    // 这一步大概是多余的？
    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGE("Couldn't find stream information.");
        return -1;
    }
    LOGI("avformat_find_stream_info");

    // Find the first video stream
    //找到第一个视频流，因为里面的流还有可能是音频流或者其他的，我们摄像头只关心视频流
    int videoStream = -1, i;
    for (i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO
            && videoStream < 0) {
            videoStream = i;
            break;
        }
    }
    if (videoStream == -1) {
        LOGE("Didn't find a video stream or audio steam.");
        return -1; // Didn't find a video stream
    }
//    pFormatCtx->probesize = 16 * 512;
    LOGI("找到视频流");
    AVCodecParameters *pCodecPar = pFormatCtx->streams[videoStream]->codecpar;
    //查找解码器
    //获取一个合适的编码器pCodec find a decoder for the video stream
    //AVCodec *pCodec = avcodec_find_decoder(pCodecPar->codec_id);
    AVCodec *pCodec;
    switch (pCodecPar->codec_id) {
        case AV_CODEC_ID_H264:
            pCodec = avcodec_find_decoder_by_name("h264");
//            pCodec = avcodec_find_decoder_by_name("h264_mediacodec");//硬解码264
            if (pCodec == NULL) {
                LOGE("Couldn't find Codec.\n");
                return -1;
            }
            LOGI("硬解码264");
            break;
        case AV_CODEC_ID_MPEG4:
            pCodec = avcodec_find_decoder_by_name("mpeg4");//硬解码mpeg4
            if (pCodec == NULL) {
                LOGE("Couldn't find Codec.\n");
                return -1;
            }
            LOGI("硬解码mpeg4");
            break;
        case AV_CODEC_ID_HEVC:
            pCodec = avcodec_find_decoder_by_name("hevc");//硬解码265
            if (pCodec == NULL) {
                LOGE("Couldn't find Codec.\n");
                return -1;
            }
            LOGI("硬解码265");
            break;
        default:
            pCodec = avcodec_find_decoder(pCodecPar->codec_id);//软解
            if (pCodec == NULL) {
                LOGE("Couldn't find Codec.\n");
                return -1;
            }
            LOGI("软解");
            break;
    }

    LOGI("获取解码器");
    //打开这个编码器，pCodecCtx表示编码器上下文，里面有流数据的信息
    // Get a pointer to the codec context for the video stream
    AVCodecContext *pCodecCtx = avcodec_alloc_context3(pCodec);

    // Copy context
    if (avcodec_parameters_to_context(pCodecCtx, pCodecPar) != 0) {
        fprintf(stderr, "Couldn't copy codec context");
        return -1; // Error copying codec context
    }

    LOGI("视频流帧率：%d fps\n", pFormatCtx->streams[videoStream]->r_frame_rate.num /
                           pFormatCtx->streams[videoStream]->r_frame_rate.den);
    int64_t fps = pFormatCtx->streams[videoStream]->r_frame_rate.num /
                  pFormatCtx->streams[videoStream]->r_frame_rate.den;

    int iTotalSeconds = (int) pFormatCtx->duration / 1000000;
    int iHour = iTotalSeconds / 3600;//小时
    int iMinute = iTotalSeconds % 3600 / 60;//分钟
    int iSecond = iTotalSeconds % 60;//秒
    LOGI("持续时间：%02d:%02d:%02d\n", iHour, iMinute, iSecond);

    LOGI("视频时长：%lld微秒\n", pFormatCtx->streams[videoStream]->duration);
    LOGI("持续时间：%lld微秒\n", pFormatCtx->duration);
    LOGI("获取解码器SUCESS");
    if (avcodec_open2(pCodecCtx, pCodec, NULL) < 0) {
        LOGE("Could not open codec.");
        return -1; // Could not open codec
    }
    LOGI("获取native window");
    // 获取native window
    ANativeWindow *nativeWindow = ANativeWindow_fromSurface(env, surface);
    // 获取视频宽高
    int videoWidth = pCodecCtx->width;
    int videoHeight = pCodecCtx->height;
    LOGI("获取视频宽高 %d : %d", videoWidth, videoHeight);
    LOGI("设置native window的buffer大小,可自动拉伸");
    // 设置native window的buffer大小,可自动拉伸
    ANativeWindow_setBuffersGeometry(nativeWindow, videoWidth, videoHeight,
                                     WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer windowBuffer;
    LOGI("Allocate video frame");
    // Allocate video frame
    AVFrame *pFrame = av_frame_alloc();
    LOGI("用于渲染");
    // 用于渲染
    AVFrame *pFrameRGBA = av_frame_alloc();
    if (pFrameRGBA == NULL || pFrame == NULL) {
        LOGE("Could not allocate video frame.");
        return -1;
    }
    LOGI("Determine required buffer size and allocate buffer");
    // Determine required buffer size and allocate buffer
    int numBytes = av_image_get_buffer_size(AV_PIX_FMT_RGBA, pCodecCtx->width, pCodecCtx->height,
                                            1);
    uint8_t *buffer = (uint8_t *) av_malloc(numBytes * sizeof(uint8_t));
    av_image_fill_arrays(pFrameRGBA->data, pFrameRGBA->linesize, buffer, AV_PIX_FMT_RGBA,
                         pCodecCtx->width, pCodecCtx->height, 1);
    LOGI("由于解码出来的帧格式不是RGBA的,在渲染之前需要进行格式转换");
    // 由于解码出来的帧格式不是RGBA的,在渲染之前需要进行格式转换
    struct SwsContext *sws_ctx = sws_getContext(pCodecCtx->width/*视频宽度*/, pCodecCtx->height/*视频高度*/,
                                                pCodecCtx->pix_fmt/*像素格式*/,
                                                pCodecCtx->width/*目标宽度*/,
                                                pCodecCtx->height/*目标高度*/, AV_PIX_FMT_RGBA/*目标格式*/,
                                                SWS_BICUBIC/*图像转换的一些算法*/, NULL, NULL, NULL);
    if (sws_ctx == NULL) {
        LOGE("Cannot initialize the conversion context!\n");
        return -1;
    }
    LOGI("格式转换成功");
    LOGE("开始播放");
    int ret;
    int receiveFrameResult;
    AVPacket packet;

    jclass dataReceiver = env->GetObjectClass(data_receiver);
    jmethodID receiver2 = env->GetMethodID(dataReceiver, "receiver2", "([B)V");

    const char *cfile_path = env->GetStringUTFChars(file_path, JNI_FALSE);
    FILE *fp = fopen(cfile_path, "w+");

//    AVBitStreamFilterContext *h264bsfc = av_bitstream_filter_init("h264_mp4toannexb");
    const AVBitStreamFilter *absFilter = av_bsf_get_by_name("h264_mp4toannexb");
    AVBSFContext *absCtx = NULL;
    av_bsf_alloc(absFilter, &absCtx);
    AVCodecParameters *codecpar = pFormatCtx->streams[videoStream]->codecpar;
    avcodec_parameters_copy(absCtx->par_in, codecpar);
    av_bsf_init(absCtx);

    //当前时间
    struct timeval tv;
    gettimeofday(&tv, NULL);
    int64_t lastTime = (int64_t) tv.tv_sec * 1000 + tv.tv_usec / 1000;
    int64_t currentTime = (int64_t) tv.tv_sec * 1000 + tv.tv_usec / 1000;
    int64_t lastPts = 0;
    int64_t sleepTime = 0;

    while (av_read_frame(pFormatCtx, &packet) >= 0) {
        // Is this a packet from the video stream?
        if (packet.stream_index == videoStream) {
//            av_bitstream_filter_filter(h264bsfc, pFormatCtx->streams[packet.stream_index]->codec,
//                                       NULL, &packet.data, &packet.size, packet.data, packet.size,
//                                       0);
            if (av_bsf_send_packet(absCtx, &packet) != 0) {
                LOGI("av_bsf_send_packet error");
            }
//            LOGI("av_bsf_receive_packet length ");
            while (av_bsf_receive_packet(absCtx, &packet) == 0) {
                gettimeofday(&tv, NULL);
                currentTime = (int64_t) tv.tv_sec * 1000 + tv.tv_usec / 1000;
                if (lastPts == 0) {
                    lastPts = packet.pts;
                }
                sleepTime =
                        (packet.pts - lastPts - (currentTime - lastTime - sleepTime / 1000)) * 1000;
                if (sleepTime > 0) {
                    usleep(sleepTime);
                }
                lastPts = packet.pts;
                lastTime = currentTime;
//                usleep(fps*1000);
//                fwrite(packet.data, packet.size, 1, fp);
                jbyteArray bytedata = env->NewByteArray(packet.size);
                env->SetByteArrayRegion(bytedata, 0, packet.size,
                                        reinterpret_cast<const jbyte *>(packet.data));
                LOGI("av_bsf_receive_packet pts:%lld  dts:%lld  currentTime:%lld  sleepTime:%lld",
                     packet.pts,
                     packet.dts, currentTime, sleepTime);
                env->CallVoidMethod(data_receiver, receiver2, bytedata);
            }
//            usleep(50000);


            //该楨位置
            float timestamp = packet.pts * av_q2d(pFormatCtx->streams[videoStream]->time_base);
//            LOGI("timestamp=%f", timestamp);
            // 解码，-11表示解码器没填充满，需要继续填充。
//            ret = avcodec_send_packet(pCodecCtx, &packet);
//            LOGI("avcodec_send_packet ret=%d", ret);

            if (ret < 0) {
                if (ret == AVERROR_EOF) {
                    LOGI("break");
                    break;
                }

            }


//            receiveFrameResult = avcodec_receive_frame(pCodecCtx, pFrame);
////            LOGI("avcodec_receive_frame receiveFrameResult=%d", receiveFrameResult);
//            while (receiveFrameResult == 0) {//绘图
////                LOGI("avcodec_receive_frame print");
//                // lock native window buffer
//                ANativeWindow_lock(nativeWindow, &windowBuffer, 0);
//                // 格式转换
//                sws_scale(sws_ctx, (uint8_t const *const *) pFrame->data,
//                          pFrame->linesize, 0, pCodecCtx->height,
//                          pFrameRGBA->data, pFrameRGBA->linesize);
//                // 获取stride
//                uint8_t *dst = (uint8_t *) windowBuffer.bits;
//                int dstStride = windowBuffer.stride * 4;
//                uint8_t *src = pFrameRGBA->data[0];
//                int srcStride = pFrameRGBA->linesize[0];
//
//                // 由于window的stride和帧的stride不同,因此需要逐行复制
//                int h;
//                for (h = 0; h < videoHeight; h++) {
//                    memcpy(dst + h * dstStride, src + h * srcStride, srcStride);
//                }
//                ANativeWindow_unlockAndPost(nativeWindow);
//                receiveFrameResult = avcodec_receive_frame(pCodecCtx, pFrame);
//            }
        }
        av_packet_unref(&packet);
    }
    LOGE("播放完成");

//    fclose(fp);
//    env->ReleaseStringUTFChars(file_path, cfile_path);

    av_free(buffer);
    av_free(pFrameRGBA);

    av_bsf_free(&absCtx);
    absCtx = NULL;
//    av_bitstream_filter_close(h264bsfc);

    // Free the YUV frame
    av_free(pFrame);

    // Close the codecs
    avcodec_close(pCodecCtx);

    // Close the video file
    avformat_close_input(&pFormatCtx);
    return 0;
}
jint JNI_OnLoad(JavaVM *vm, void *reserved)//这个类似android的生命周期，加载jni的时候会自己调用
{
    LOGI("ffmpeg JNI_OnLoad");
    av_jni_set_java_vm(vm, reserved);
    return JNI_VERSION_1_6;
}
}extern "C"
extern "C"
JNIEXPORT void JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_test(JNIEnv *env, jobject thiz, jobject data_receiver) {
    jclass dataReceiver = env->GetObjectClass(data_receiver);
    jmethodID receiver1 = env->GetMethodID(dataReceiver, "receiver1", "()V");
    env->CallVoidMethod(data_receiver, receiver1);
    LOGE("CallVoidMethod receiver1");

    jmethodID receiver2 = env->GetMethodID(dataReceiver, "receiver2", "([B)V");
    uint8_t *extended_data;
    char chars[] = {};
    jbyteArray bytedata = env->NewByteArray(0);
    env->SetByteArrayRegion(bytedata, 0, 0, reinterpret_cast<const jbyte *>(extended_data));
    env->CallVoidMethod(data_receiver, receiver2, bytedata);
}extern "C"
JNIEXPORT void JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_connect(JNIEnv *env, jobject thiz, jstring url) {
}extern "C"
JNIEXPORT jint JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_readBytes(JNIEnv *env, jobject thiz, jbyteArray data) {
    // TODO: implement readBytes()
}extern "C"
JNIEXPORT void JNICALL
Java_com_ksw_ffmpegtest_FFMPEG_disConnect(JNIEnv *env, jobject thiz) {
    // TODO: implement disConnect()
}