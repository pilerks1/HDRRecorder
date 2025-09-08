# Overview:

This is a 10 bit video recording app for android using the cameraX SDK. Your device must be running a minimum of android 13, support a 10 bit recording stream, and your OEM must allow 3rd party apps to access low level camera2 operations.

Android 13 is required for many parts of the app, most importantly for producing a 10 bit stream. Google revampled and standardized 10 bit video capture and display in android 13 which makes this app possible. If you'd like to make this work on older android version, the source code is yours to change.

# Motivation:

I record videos for a youtube channel I run using my phone (currently Samsung S24). I like to take full advantage of my phone's hardware, including maximizing the video resolutuion, framerate, and bit depth to produce the highest quality videos possible. The problem is Samsung's own camera/video app does not take advantage of the hardware of the phone, probablty for the sake of simplicity and maintaining a clean UI. For example, the phone is capable of recording 4000x3000 (the max sensor size) at 10 bit 60fps. Samsung does not allow 4:3 aspect ratio video recording at all, and the highest 16:9 resolution avalible is UHD (3840x2160). Using samsungs camera app means I loose about 200 horizontal pixels and 900 vertical pixels. I also do not have access to other settings or information like bitrate control or dropped frames.

My first thought would be to look for 3rd party apps on google play store or via APK. Many of these "pro" video apps that truly allow for manual control of the video stream cost money or do not support 10 bit recording, sometimes both. The few that do are either overkill, or do not properly produce a 10 bit stream (incorrect gamma profile, improper encoding, etc), and cost quite a lot for what I am trying to achive. I decided to make my own app for free and publish the source code. There is no other free and open source video recording app for 10 bit streams avalible on the google play store to my knowledge.

# Install:

The latest .apk download is avalible in the releases. I will make this avalible on google play store soon.

# Discussion:

If you have questions about a function of the app, a bug report, or a feature request, use the discussions page. Please list your device model if making a bug report.

# Known Bugs:

This app is in its infancy and I already have some bugs to sort and a list of features to add.

While the app works perfectly on my S24 (snapdragon), it does not produce a preview or a recording on a Samsung S22 Ultra (snapdragon). This is under investigation. I have not tested on any other devices

App rotation is buggy in many ways, including UI in vertical mode, and the video stream flipping upside down when rotating the phone 180 degrees

Recording timer does not accuratly handle pause/resume during recording

Auto exposure meter point does not align with finger press

# Planned Features:

Compatibility check with debug information

Custom bitrate

Save presets

Import a custom 1D LUT

Integration of the thermal and performance API

Focus slider

Remaining storage indecator
