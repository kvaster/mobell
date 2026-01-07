# FFmpeg

We need to compile FFmpeg with all options disabled except mxpeg support.

Prepare sources

```
cd ffmpeg
git clone --depth 1 --branch n8.0.1 https://github.com/FFmpeg/FFmpeg.git
```

Compile

```
cd FFmpeg
sh ../build.sh
```
