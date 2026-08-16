# Java RTSP Server

This Java-based RTSP server was primarily designed for providing a high-security RTSP server for IP cameras  
that ship with a not so secure RTSP implementation.  
I have written a C++ application that uses Foscam's encrypted binary protocol to connect to Foscam IP cameras  
and then forwards the audio and video data into encrypted Message Queues. However, the C++ application is not open source (yet).  
But even without that C++ application, this RTSP server can, for example, still be used to connect to an (insecure) IP camera via  
the camera's built-in RTSP server over the LAN and then re-publish the A/V stream as a secure RTSP stream.

## Features

- UDP and TCP transport modes support
- SSL/TLS support (RTSPS) for encrypting all RTSP commands.  
	If TCP transport mode is being used, then audio and video data will also be encrypted.
- SRTP and SRTCP support for encrypting all audio and video data that is being transmitted (for UDP and TCP transport modes).  
	Both the legacy SDES (e.g. for FFmpeg) and the modern MIKEY (e.g. for VLC and GStreamer) key management protocols are supported.  
	Re-keying mid-session is also supported for both SDES and MIKEY.

- supported video codecs:
	- H264
	- H265
	- MJPEG
	- VP8
- supported audio codecs:
	- AAC (mono, stereo, surround)
	- AC-3 (mono, stereo, surround)
	- MP2 aka MPEG-1/2 Layer II (mono, stereo)
	- MP3 aka MPEG-1/2 Layer III (mono, stereo)
	- Opus (mono, stereo)
	- PCM A-Law (mono, stereo, surround)
	- PCM Mu-Law (mono, stereo, surround)
	- PCM Linear unsigned 8-bits (mono, stereo, surround)
	- PCM Linear signed 16-bits (mono, stereo, surround)

- supported inputs:
	- file containers:
		- audio/video: MKV, MP4, MOV, WEBM
		- audio: AAC, AC-3, MP2, MP3, Ogg(-Opus), Opus, WAV
	- other RTSP streams (if they use supported codecs)
	- raw elementary sub-stream files
		- raw H264/H265 files
		- raw AAC/AC-3/PCM files
		- raw MJPEG files
		- raw Opus/VP8 files (uses a proprietary file format though)
	- proprietary Message Queues, e.g. for Foscam IP Cameras

- streams can be configured during runtime

**Notes:**  

- raw elementary sub-stream files are mainly intended for testing purposes. But they can also be used  
	as regular inputs if you know how to create the files correctly (FFmpeg and Audacity are your friends)
- when using raw elementary sub-stream files or proprietary Message Queues, the A/V data needs to be in a specific format:  
	- H264/H265 NAL Units must be AnnexB-prefixed
	- AAC frames must have an ADTS header
	- MJPEG images should use 'standard Huffman tables' and a maximum image size of 2040x2040 px.  
		Optimized tables and larger images can also be used, but will result in a heavy CPU load
- keep in mind that RTSP streams are intended for real-time streaming. That implies that the A/V data needs to be  
	small enough so it can be transmitted in a timely manner. Sending 4k video with a high bitrate will hardly be feasible.  
	MJPEG is especially bad for this reason. It will work just fine for small resolutions and low bitrates though


## Configuration

See the sample configuration file [config/sample-config.json](config/sample-config.json) and the  
'Streams Configuration' files in `config/sample-streams-config1/` and `config/sample-streams-config2/`.

The files in the 'Streams Configuration' directories can be edited while the application is running.  
The changes will take effect immediately.

## Media Files, SSL Certificates & Co.

All media files and server's SSL certificate and key must be either directly in the  
directory `data/` or inside a subdirectory of `data/`.

## Running the Application

The application can be launched with Gradle and requires Java 25 or higher.  
There is only one argument that needs to be passed: the path to the configuration file:

```
./gradlew run --args="config/sample-config.json"
```
