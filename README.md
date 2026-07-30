# Overview:

This is a 10 bit video recording app for android using the cameraX SDK. Your device must be running a minimum of android 13, support a 10 bit recording stream, and your OEM must allow 3rd party apps to access low level camera2 operations.

There are numerous 3rd party camera apps on google play store or via APK. Many of these pro video apps that truly allow for manual control of the video stream cost money or do not support 10 bit recording, sometimes both. The few that do fail to properly produce a 10 bit stream (incorrect gamma profile, improper encoding, etc), or cost money. I decided to make my own app for free and publish the source code. There is no other free and open source video recording app for 10 bit streams available to my knowledge.

I am using cameraX as opposed to camera2 largely for compatibility reasons to reach as many devices as possible, and simplicity of coding as I am a single developer doing this in free time. Additionally, google has been putting increasingly more resources into cameraX development and documentation, I feel that it is the future, to the point now that google recommends all new camera apps use/migrate to cameraX. The most comparable camera2 apps are Blackmagic Camera App and mcpro24fps.

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

4:3 and 16:9 recording aspect ratios

Shutter Speed, ISO, live framerate, live bitrate, dropped frames, and device thermal reporting

Manual control of shutter speed, ISO, focus, white balance, tint, EV

Target variable FPS range

Support for hybrid auto exposure, precise cct, night video mode, post raw ISO on supported devices

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

Multiple lense support

Continuous zoom

Complete settings presets and compatibility screen
