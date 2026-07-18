# Overview:

This is a 10 bit video recording app for android using the cameraX SDK. Your device must be running a minimum of android 13, support a 10 bit recording stream, and your OEM must allow 3rd party apps to access low level camera2 operations.

# Compatibility:

Android 13 is required for many parts of the app, most importantly for producing a 10 bit stream. Google revamped and standardized 10 bit video capture and display in android 13 which makes this app possible.

A basic compatibility check is included in the settings to determine what combinations of resolution and framerate your phone supports. If every table cell shows "none", it is unlikely your phone supports all the necessary low level hardware permissions needed for this app to run.

Samsung:

S24 or newer

Z series untested

A/M series untested (I assume it wont work)

All other brands are untested (Google, Sony, Xiaomi, etc).

# Features:

FHD/UHD recording resolution

Shutter Speed, ISO, live framerate, live bitrate, dropped frames, and device thermal reporting

Manual control of shutter speed, ISO, focus, white balance, tint, EV

Target variable FPS range

Support for hybrid auto exposure, precise cct, night video mode on supported devices

Settings presets

10 bit recording with ability to select HLG, HDR10+, Dolby

User selectable bitrate

User selectable write location, including external USB/SD card

# Install:

The latest .apk download is available in the releases. A Google playstore listing will be sought once I take the app out of alpha.

# Discussion:

If you have questions about a function of the app, a bug report, or a feature request, use the discussions page. Please list your device model if making a bug report.

# Known Bugs:

Storage path in settings does not update if an external storage device that was previously selected for write is removed

Remaining storage always shows internal storage even if external device is selected for video write

Manual gamma curves (PQ, HLG) do not look correct

# Planned Features:

4:3 and 16:9 recording

Multiple lense support

Continuous zoom

Expanded compatibility screen

# Motivation:

I record videos for a youtube channel I run using my phone (currently Samsung S24). I like to take full advantage of my phone's hardware, including maximizing the video resolution, framerate, and bit depth to produce the highest quality videos possible. Samsung's own camera/video app does not take advantage of the hardware of the phone, probably for the sake of simplicity and maintaining a clean UI. For example, the phone is capable of recording 4000x3000 (the max sensor size) at 10 bit 60fps. Samsung does not allow 4:3 aspect ratio video recording at all, and the highest 16:9 resolution available is UHD (3840x2160). Using Samsung's camera app means I loose about 200 horizontal pixels and 900 vertical pixels. I also do not have access to other settings or information like bitrate control or dropped frames.

There are numerous 3rd party apps on google play store or via APK. Many of these "pro" video apps that truly allow for manual control of the video stream cost money or do not support 10 bit recording, sometimes both. The few that do are either overkill, or do not properly produce a 10 bit stream (incorrect gamma profile, improper encoding, etc), and cost quite a lot for what I am trying to achieve. I decided to make my own app for free and publish the source code. There is no other free and open source video recording app for 10 bit streams available to my knowledge.

I am using cameraX as opposed to camera2 largely for compatibility reasons to reach as many devices as possible, and simplicity of coding as I am a single developer doing this in free time. Additionally, google has been putting increasingly more resources into cameraX development and documentation, I feel that it is the future, to the point now that google recommends all new camera apps use/migrate to cameraX. The most comparable camera2 apps are Blackmagic Camera App and mcpro24fps.

