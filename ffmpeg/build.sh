#!/bin/bash
set -euo pipefail

# --- 1. SETUP (Adjust these paths) ---
NDK_PATH=/opt/android-sdk/ndk/29.0.14206865
HOST_TAG=linux-x86_64
TOOLCHAIN=$NDK_PATH/toolchains/llvm/prebuilt/$HOST_TAG
API=23  # Min Android version

# Output directory
OUTPUT_DIR=$(pwd)/android_build

# Function to build a specific architecture
build_arch() {
    ARCH=$1       # ffmpeg arch (aarch64, arm, x86, x86_64)
    CPU=$2        # ffmpeg cpu (armv8-a, armv7-a, i686, x86_64)
    TRIPLE=$3     # Clang target triple prefix
    ABI_NAME=$4   # Android ABI name (arm64-v8a, etc)
    EXTRA_OPTS=$5 # Extra options

    echo "========================================"
    echo "BUILDING FOR: $ABI_NAME ($ARCH)"
    echo "========================================"

    # 1. Set Compiler Variables based on Architecture
    export CC=$TOOLCHAIN/bin/${TRIPLE}${API}-clang
    export CXX=$TOOLCHAIN/bin/${TRIPLE}${API}-clang++
    export AR=$TOOLCHAIN/bin/llvm-ar
    export NM=$TOOLCHAIN/bin/llvm-nm
    export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
    export STRIP=$TOOLCHAIN/bin/llvm-strip

    # 2. Configure FFmpeg
    ./configure \
        --prefix=$OUTPUT_DIR/$ABI_NAME \
        --target-os=android \
        --arch=$ARCH \
        --cpu=$CPU \
        --cc=$CC \
        --cxx=$CXX \
        --ar=$AR \
        --nm=$NM \
        --ranlib=$RANLIB \
        --strip=$STRIP \
        --enable-cross-compile \
        --sysroot=$TOOLCHAIN/sysroot \
        \
        --disable-debug \
        --disable-doc \
        --disable-ffplay \
        --disable-ffprobe \
        --disable-symver \
        --disable-network \
        --disable-everything \
        \
        --enable-decoder=pcm_alaw \
        --enable-decoder=mxpeg \
        --enable-demuxer=mxg \
        \
        --disable-avformat \
        --disable-avdevice \
        --disable-swscale \
        --disable-swresample \
        --disable-avfilter \
        --disable-pixelutils \
        \
        --disable-shared \
        --enable-static \
        --enable-pic \
        --extra-cflags="-O3 -fPIC -DPIC -DANDROID" \
        $EXTRA_OPTS

    # 3. Compile
    make clean
    make -j$(nproc)
    make install

    echo ">> INSTALLED $ABI_NAME to $OUTPUT_DIR/$ABI_NAME"
}

# --- 2. EXECUTE BUILDS ---

# ARM 64-bit (REQUIRED for Play Store & 16KB Pages)
build_arch "aarch64" "armv8-a" "aarch64-linux-android" "arm64-v8a" ""

# ARM 32-bit (Legacy / Low-end devices)
build_arch "arm" "armv7-a" "armv7a-linux-androideabi" "armeabi-v7a" ""

# x86 64-bit (Emulator / ChromeOS)
build_arch "x86_64" "generic" "x86_64-linux-android" "x86_64" "--disable-x86asm"

# x86 32-bit (Legacy Emulator)
build_arch "x86" "i686" "i686-linux-android" "x86" "--disable-x86asm"

echo "========================================"
echo "ALL BUILDS COMPLETE"
echo "========================================"
