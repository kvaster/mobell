#!/bin/bash

BASEDIR=$(pwd)

target=$1
if [[ "${target}" == "arm" ]]; then
ARCH=arm
CPU_ARCH=arm
CPU=armv7-a
TOOLCHAIN=arm-linux-androideabi
NDK_ABI=arm-linux-androideabi
PLATFORM=arm

elif [[ "${target}" == "arm64" ]]; then
ARCH=arm64
CPU_ARCH=arm64
TOOLCHAIN=aarch64-linux-android
NDK_ABI=aarch64-linux-android
PLATFORM=arm64

elif [[ "${target}" == "x86" ]]; then
ARCH=i686
CPU_ARCH=i686
TOOLCHAIN=x86
NDK_ABI=i686-linux-android
PLATFORM=x86

elif [[ "${target}" == "x86_64" ]]; then
ARCH=x86-64
CPU_ARCH=x86_64
TOOLCHAIN=x86_64
NDK_ABI=x86_64-linux-android
PLATFORM=x86_64

else
echo "Wrong targer: ${target}"
exit 2
fi

TOOLCHAIN_PREFIX=${ANDROID_HOME}/ndk-bundle/toolchains/${TOOLCHAIN}-4.9/prebuilt/linux-x86_64
CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/${NDK_ABI}-

#TOOLCHAIN_PREFIX=${ANDROID_HOME}/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64
#CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/

PLATFORM_PREFIX=${ANDROID_HOME}/ndk-bundle/platforms/android-27/arch-${PLATFORM}/
SYSROOT=${ANDROID_HOME}/ndk-bundle/sysroot
CFLAGS='-O2 -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2 -fno-strict-overflow -fstack-protector-all -DANDROID'
LDFLAGS='-Wl,-z,relro -Wl,-z,now'

CFG="./configure"
CFG="$CFG --prefix=$(pwd)/android/${ARCH}"
CFG="$CFG --target-os=linux"
#CFG="$CFG --toolchain=clang-usan"
CFG="$CFG --cross-prefix=${CROSS_PREFIX}"
CFG="$CFG --arch=${CPU_ARCH}"
if [ ! -z "${CPU}" ]; then
  CFG="$CFG --cpu=${CPU}"
fi
CFG="$CFG --enable-runtime-cpudetect"
CFG="$CFG --sysroot=${PLATFORM_PREFIX}"
CFG="$CFG --enable-pic"
CFG="$CFG --disable-debug"
CFG="$CFG --disable-ffserver"
CFG="$CFG --disable-stripping"
CFG="$CFG --disable-version3"
CFG="$CFG --enable-hardcoded-tables"
CFG="$CFG --disable-ffplay"
CFG="$CFG --disable-ffprobe"
CFG="$CFG --disable-gpl"
CFG="$CFG --disable-doc"
CFG="$CFG --enable-shared"
CFG="$CFG --disable-static"
CFG="$CFG --extra-cflags=\"-I${SYSROOT}/usr/include -I${SYSROOT}/usr/include/${NDK_ABI} $CFLAGS\""
CFG="$CFG --extra-ldflags=\"-L${TOOLCHAIN_PREFIX}/lib $LDFLAGS\""
CFG="$CFG --pkg-config=./ffmpeg-pkg-config"
CFG="$CFG --disable-everything"
CFG="$CFG --enable-decoder=pcm_alaw"
CFG="$CFG --enable-decoder=mxpeg"
CFG="$CFG --enable-demuxer=mxg"
CFG="$CFG --disable-avformat"
CFG="$CFG --disable-avdevice"
CFG="$CFG --disable-swscale"
CFG="$CFG --disable-swresample"
CFG="$CFG --disable-postproc"
CFG="$CFG --disable-avfilter"
CFG="$CFG --disable-network"
CFG="$CFG --disable-pixelutils"

sh -c "${CFG}"

