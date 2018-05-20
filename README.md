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
- [x] Audio recording/encoding
- [x] Stream recorded audio back to camera (door station)
- [x] Process ring events
- [x] Open door
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

# APP11

There two types of messages in APP11 tag:

````
ff eb 00 14 4d 58 53 00  02 01 00 00 80 3e 00 00   ....MXS. .....>..
20 50 31 36 01 01

ff eb 06 56 4d 58 41 00  02 01 00 00 1e a0 b0 87   ...VMXA. ........
3f 6b 05 00 50 c3 00 00
````

It seems that first message is audio type / features and second one - audio itself.

Audio type:
TODO

Audio data:

* `ff eb` - APP11 marker
* `06 56` - tag size
* `4D 58 41 00` - audio data marker (?)
* `02 01 00 00` - ?
* `1e a0 b0 87 3f 6b 05 00` - timestamp of first sample in data in us from 1 January 1970 (little endian)
* `50 c3 00 00` - duration of block in us (little endian)

# Events and data from client

Events and data from client are sent in different ways. It seems that all events are plain json string ended with 0x0A 0x00.
But audio data is sent in the same stream using same tags as in APP11. I've found same two type of tags: MXS and MXA.
But now it seems there is 'sound on' and 'sound off' MXS tag:

````
ff eb 00 14 4d 58 53 00  01 81 27 c1 80 3e 00 00   ....MXS. ..'..>..
20 50 31 36 01 01

ff eb 00 14 4d 58 53 00  01 01 00 00 80 3e 00 00   ....MXS. .....>..
20 50 31 36 01 01

ff eb 00 56 4d 58 41 00  01 61 62 69 6f 6e 69 78   ...VMXA. .abionix
5f 61 78 76 69 65 77 65
````

Also AxViewer fills some data fields with garbage (audio data timestamp, duration).

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
