# Java-based Real Time Streaming Protocol (RTSP) Server

This RTSP server was primarily designed to provide a high-security alternative to existing RTSP implementations.

Usage scenarios include:

- re-publishing an IP camera's (unencrypted) RTSP stream as a secure RTSP stream over the internet
- media file streaming (e.g., streaming a movie file to a mobile device that runs [VLC](https://www.videolan.org/))
- Jukebox or Internet Radio Station

Even though [VLC](https://www.videolan.org/) does not support RTSPS, it does at least support SRTP and SRTCP for encrypting  
all audio and video data that is being transmitted.  
Other clients like [GStreamer](https://gstreamer.freedesktop.org/) and [FFmpeg](https://ffmpeg.org/) do support RTSPS.  
See [docs/test_matrix.md](docs/test_matrix.md) for more details.

Explainer:

- *RTSP* is a protocol for controlling streaming media servers. But it does not transmit audio and video data - that is done using *RTP*
- *RTSPS* uses SSL/TLS for secure communication over the internet and therefore encrypts all *RTSP* commands being  
	sent between the client and the server. Since *SRTP/SRTCP* require the exchange of encryption keys between the client  
	and the server, using *RTSPS* is the only way to truly ensure nobody can eavesdrop on the audio and video data being transmitted
- *RTP* is a protocol for transmitting audio and video data
- *SRTP* uses encryption to protect the audio and video data being transmitted
- *RTCP* is a protocol for transmitting control messages between the client and the server regarding the *RTP* stream
- *SRTCP* uses encryption to protect the control messages being transmitted


## Features

- UDP and TCP transport modes support
- SSL/TLS support (RTSPS) for encrypting all RTSP commands.  
	If TCP transport mode is being used, then audio and video data will also be SSL/TLS encrypted
- SRTP and SRTCP support for encrypting all audio and video data that is being transmitted (for UDP and TCP transport modes).  
	Both the legacy SDES (e.g. for FFmpeg) and the modern MIKEY (e.g. for VLC and GStreamer) key management protocols are supported.  
	Re-keying mid-session is also supported for both SDES and MIKEY

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
	- 'Jukebox' for audio files in a folder (and its sub-folders)
	- other RTSP streams (if they use supported codecs)
	- raw elementary sub-stream files
		- raw H264/H265 files
		- raw AAC/AC-3/PCM files
		- raw MJPEG files
		- raw Opus/VP8 files (uses a proprietary file format though)
	- proprietary Message Queues, e.g. for Foscam IP Cameras

- seeking is supported only for file containers
- streams can be configured during runtime

**Notes:**  

- streams that use a 'Jukebox' sub-stream as input will recursively read all audio files in the configured folder  
	and convert them to a configurable format (AAC, AC-3, MP2, MP3, Opus, PCM) before sending the audio to the client.  
	The audio files will be played back in random order.
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

See [docs/streams_config.md](docs/streams_config.md) for more details.


## Media Files, SSL Certificates & Co.

All media files and server's SSL certificate and key must be either directly in the  
directory `data/` or inside a subdirectory of `data/`.  
You can use symlinks if you prefer to store the directories elsewhere.


## Sample Media Files

All sample media files are derived from: [blender.org Big Buck Bunny](https://download.blender.org/demo/movies/BBB/bbb_sunflower_1080p_30fps_normal.mp4.zip)


## Server Architecture

To learn more about the server's architecture and how the internal multicasting works, please see [here](docs/architecture.md).


## Running the Application

### From Source

The application can be launched with Gradle and requires Java 25 or higher.  
There is only one argument that needs to be passed: the path to the configuration file:

```
$ ./gradlew run --args="config/sample-config-no_ssl.json"
```

### From a Binary Release

You can download a binary release from the [Releases](https://github.com/tsitle/ts_rtsp_server/releases) page.  
The binary releases come with the sample configuration and media files.  
No external dependencies are required.

Once you have extracted the binary release, you can simply run the application like this:

```
$ cd ts_rtsp_server-1.1.6-lx-x64-bin
$ ./ts_rtsp_server.sh config/sample-config-no_ssl.json
```
