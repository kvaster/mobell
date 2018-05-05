# MoBell
Android intercom (client) for mobotix T24/T25 cameras

The goal of this project is to create intercom software running on Android and using Mobotix's eventstream protocol.
Also intercom should operate without internet.

Unfortunatelly eventstream protocol is not documented and can be used only via reverse engineering.

This project is in really starting stage.

# Compile and run
Since there is no UI currently in project, you should provide host of your mobotix camera
and credentials. Currently this is done via local.properties file (it is not under git control).
You should add following lines to your local.properties file:

`
mobotix.host="<mobotix_ip_or_name>"
mobotix.login="<login>"
mobotix.pass="<password>"
`

# TODO

- [x] Simple streaming
- [x] Video decoding and playing
- [x] Audio decoding and playing
- [ ] Audio recording/encoding
- [ ] Stream recorded audio back to camera (door station)
- [ ] Process ring events
- [ ] Open door
- [ ] Nice UI

# Should I buy mobotix door stations ?

I have one at my home, but I will never buy one more.

# Mobotix eventstream

Mobotix has eventstream SDK, but it is pre-compiled for linux, windows and macosx
and there is no any documentation. But it have basic docs about mxpeg frames: http://developer.mobotix.com/docs/mxpeg_frame.html

Some time ago Abionix company released AxViewer application for Android. And this application is using eventstream.
Unfortunatelly AxViewer is slow and crashes - it is not suitable to use it as intercom for door.
But it helps me a lot - tcpdump + wireshark shows that eventstream protocol is almost the same as described in mxpeg frames.
So, eventstream is mxpeg stream (jpeg tags) with some additional data.

In docs mxpeg stream uses APP13 for audio data and encodes audio with alaw codec by default,
but eventstream uses APP11 tag for audio and encodes audio as PCM16 16Khz.
Also APP12 tag is used to encode events in JSON format.

# FFmpeg
This software uses code of <a href=http://ffmpeg.org>FFmpeg</a> licensed under the <a href=http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>LGPLv2.1</a>
and its source can be downloaded <a href=https://github.com/ffmpeg/ffmpeg>here</a>.

This project contains compiled versions of FFmpeg libraries.
Libraries were compiled against following git commit: dc7d5f9f1904faebab73f5de60f2c360c8255333.
You can find configure script at ffmpeg/build.sh

# License
Copyright 2018 Viktor Kuzmin

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
