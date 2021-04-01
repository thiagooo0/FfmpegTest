# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("ffmpegtest")


# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).由于在同个文件底下，所以直呼其名即可，不需加路径。
        native-lib.cpp
        timer.c)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)


# 定义变量，这里填写libs跟cmakelist的相对位置。
set(distribution_DIR ../../../../libs)

# 添加库——外部引入的库
# 库名称：avcodec（不需要包含前缀lib）
# 库类型：SHARED，表示动态库，后缀为.so（如果是STATIC，则表示静态库，后缀为.a）
# IMPORTED表明是外部引入的库
add_library(avcodec
        SHARED
        IMPORTED)
# 设置目标属性
# 设置avcodec目标库的IMPORTED_LOCATION属性，用于说明引入库的位置
# 还可以设置其他属性，格式：PROPERTIES key value
set_target_properties(avcodec
        PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/${ANDROID_ABI}/libavcodec.so)
add_library(avfilter
        SHARED
        IMPORTED)
set_target_properties(avfilter
        PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/${ANDROID_ABI}/libavfilter.so)

add_library(avformat
        SHARED
        IMPORTED)
set_target_properties(avformat
        PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/${ANDROID_ABI}/libavformat.so)

add_library(avutil
        SHARED
        IMPORTED)
set_target_properties(avutil
        PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/${ANDROID_ABI}/libavutil.so)

add_library(swresample
        SHARED
        IMPORTED)
set_target_properties(swresample
        PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/${ANDROID_ABI}/libswresample.so)

add_library(swscale
        SHARED
        IMPORTED)
set_target_properties(swscale
        PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/${ANDROID_ABI}/libswscale.so)

# 引入头文件
include_directories(include)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
# 告诉编译器生成native-lib库需要链接的库
# native-lib库需要依赖avcodec、avfilter等库
target_link_libraries( # Specifies the target library.
        native-lib
        avcodec
        avfilter
        avformat
        avutil
        swresample
        swscale
        -landroid
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})