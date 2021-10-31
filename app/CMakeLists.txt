cmake_minimum_required(VERSION 3.4.1)
project(mobell)

get_filename_component(ffmpeg ${CMAKE_SOURCE_DIR}/../ffmpeg ABSOLUTE)

add_library(lib_avutil STATIC IMPORTED)
set_target_properties(lib_avutil PROPERTIES IMPORTED_LOCATION ${ffmpeg}/lib/${ANDROID_ABI}/libavutil.a)

add_library(lib_avcodec STATIC IMPORTED)
set_target_properties(lib_avcodec PROPERTIES IMPORTED_LOCATION ${ffmpeg}/lib/${ANDROID_ABI}/libavcodec.a)

set(SOURCE_FILES src/main/cpp/mxpeg_native.cpp src/main/cpp/mxpeg_renderer.cpp src/main/cpp/audio_recorder.cpp)

add_library(mobell SHARED ${SOURCE_FILES})

include_directories(${ffmpeg}/include)

target_link_libraries(mobell log lib_avcodec lib_avutil GLESv2 OpenSLES)
