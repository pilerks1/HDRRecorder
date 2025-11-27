# Overview:

This is a 10 bit video recording app for android using the cameraX SDK. Your device must be running a minimum of android 13, support a 10 bit recording stream, and your OEM must allow 3rd party apps to access low level camera2 operations. A basic compatibility check is included in the settings to determine what combinations of resolution and framerate your phone supports. If every table cell shows "none", it is unlikely your phone supports all the nessessary low level hardware permissions needed for this app to run.

Android 13 is required for many parts of the app, most importantly for producing a 10 bit stream. Google revampled and standardized 10 bit video capture and display in android 13 which makes this app possible.

# Motivation:

I record videos for a youtube channel I run using my phone (currently Samsung S24). I like to take full advantage of my phone's hardware, including maximizing the video resolutuion, framerate, and bit depth to produce the highest quality videos possible. Samsung's own camera/video app does not take advantage of the hardware of the phone, probablty for the sake of simplicity and maintaining a clean UI. For example, the phone is capable of recording 4000x3000 (the max sensor size) at 10 bit 60fps. Samsung does not allow 4:3 aspect ratio video recording at all, and the highest 16:9 resolution avalible is UHD (3840x2160). Using samsungs camera app means I loose about 200 horizontal pixels and 900 vertical pixels. I also do not have access to other settings or information like bitrate control or dropped frames.

There are numerous 3rd party apps on google play store or via APK. Many of these "pro" video apps that truly allow for manual control of the video stream cost money or do not support 10 bit recording, sometimes both. The few that do are either overkill, or do not properly produce a 10 bit stream (incorrect gamma profile, improper encoding, etc), and cost quite a lot for what I am trying to achive. I decided to make my own app for free and publish the source code. There is no other free and open source video recording app for 10 bit streams avalible to my knowledge.

I am using cameraX as opposed to camera2 laregly for compatibility reasons to reach as many devices as possible, and simplicity of coding as I am a single developer doing this in free time. Additionally, google has been putting increasingly more resoures into cameraX development and documentation, I feel that it is the future, to the point now that google reccomends all new camera apps use/migrate to cameraX. Ultimately, a native camera2 app will be able to provide more customization and low level control of camera hardware if executed right, at the possible expense of compatibility. The most compareable camera2 apps are Blackmagic Camera App and mcpro24fps.

# Install:

The latest .apk download is avalible in the releases. A Google playstore listing will be sought once the app is more developed.

# Discussion:

If you have questions about a function of the app, a bug report, or a feature request, use the discussions page. Please list your device model if making a bug report.

# Known Bugs:

Recording timer does not accuratly handle pause/resume during recording

Auto exposure meter point does not behave as expected or consistently

# Planned Features:

App performance profiling

Integration of the thermal and performance API

Custom bitrate

Save presets

Import a custom static 1D LUT for recording

Develop auto-gamma curve that is scene adaptive

Manual focus, shutter speed, iso, exposure compensation

Intergration of hybrid-manual shutter speed and iso control (android 16+)

Intergration of adaptive low light boost auto exposure (android 15+)

APV lossless codec once cameraX supports (android 16+)

Remaining storage indecator
