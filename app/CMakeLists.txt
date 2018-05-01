cmake_minimum_required(VERSION 3.4.1)

get_filename_component(ffmpeg ${CMAKE_SOURCE_DIR}/../ffmpeg ABSOLUTE)

add_library(lib_avcodec SHARED IMPORTED)
set_target_properties(lib_avcodec PROPERTIES IMPORTED_LOCATION ${ffmpeg}/lib/${ANDROID_ABI}/libavcodec.so)

add_library(lib_avutil SHARED IMPORTED)
set_target_properties(lib_avutil PROPERTIES IMPORTED_LOCATION ${ffmpeg}/lib/${ANDROID_ABI}/libavutil.so)

set(SOURCE_FILES src/main/cpp/mxpeg_native.cpp src/main/cpp/mxpeg_renderer.cpp)

add_library(mobell SHARED ${SOURCE_FILES})

include_directories(${ffmpeg}/include)

target_link_libraries(mobell log lib_avutil lib_avcodec GLESv2)
