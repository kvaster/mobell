#!/bin/bash

BASEDIR=$(pwd)
TOOLCHAIN_PREFIX=/opt/android-sdk-update-manager/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
PLATFORM_PREFIX=/opt/android-sdk-update-manager/ndk-bundle/platforms/android-27/arch-arm/
SYSROOT=/opt/android-sdk-update-manager/ndk-bundle/sysroot
#CFLAGS='-O3 -Wall -pipe -std=c99 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -DANDROID'
#LDFLAGS='-Wl,-z,relro -Wl,-z,now -pie -nostdlib'
#CFLAGS='-O3 -Wall -pipe -std=c99 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -DANDROID'
#LDFLAGS='-Wl,-rpath-link=${PLATFORM_PREFIX}/usr/lib -L${PLATFORM_PREFIX}/usr/lib -nostdlib -lc -lm -ldl -llog'
CFLAGS='-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2 -fno-strict-overflow -fstack-protector-all -DANDROID'
LDFLAGS='-Wl,-z,relro'

./configure \
--prefix=$(pwd)/android/arm \
--target-os=linux \
--cross-prefix=${TOOLCHAIN_PREFIX}/bin/arm-linux-androideabi- \
--arch=arm \
--cpu=armv7-a \
--enable-runtime-cpudetect \
--sysroot=${PLATFORM_PREFIX} \
--enable-pic \
--disable-debug \
--disable-ffserver \
--disable-stripping \
--disable-version3 \
--enable-hardcoded-tables \
--disable-ffplay \
--disable-ffprobe \
--disable-gpl \
--disable-doc \
--enable-shared \
--enable-static \
--extra-cflags="-I${SYSROOT}/usr/include -I${SYSROOT}/usr/include/arm-linux-androideabi $CFLAGS" \
--extra-ldflags="-L${TOOLCHAIN_PREFIX}/lib $LDFLAGS" \
--pkg-config="./ffmpeg-pkg-config" \
--disable-everything \
--enable-decoder=pcm_alaw \
--enable-decoder=mxpeg \
--enable-demuxer=mxg \
--disable-avformat \
--disable-avdevice \
--disable-swscale \
--disable-swresample \
--disable-postproc \
--disable-avfilter \
--disable-network \
--disable-pixelutils

#--enable-static

#  --disable-dct            disable DCT code
#  --disable-dwt            disable DWT code
#  --disable-error-resilience disable error resilience code
#  --disable-lsp            disable LSP code
#  --disable-lzo            disable LZO decoder code
#  --disable-mdct           disable MDCT code
#  --disable-rdft           disable RDFT code
#  --disable-fft            disable FFT code
#  --disable-faan           disable floating point AAN (I)DCT code
#  --disable-pixelutils     disable pixel utils in libavutil

