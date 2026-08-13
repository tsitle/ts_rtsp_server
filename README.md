# Java RTSP Server

## Features

- UDP and TCP transport modes support
- SSL/TLS support (RTSPS) for encrypting all RTSP commands.  
  If TCP transport mode is being used, then audio and video data will also be encrypted.
- SRTP and SRTCP support for encrypting all audio and video data that is being transmitted (for UDP and TCP transport modes).  
  Both the legacy SDES (e.g. for FFmpeg) and the modern MIKEY (e.g. for VLC and GStreamer) key management protocols are supported.

- supported video codecs:
	- H264 (AnnexB-prefixed)
	- H265 (AnnexB-prefixed)
	- MJPEG (using 'standard Huffman tables' and a maximum image size of 2040x2040 px.  
		Optimized tables and larger images can also be used, but will result in a heavy CPU load)
	- VP8
- supported audio codecs:
	- AAC (mono, stereo, surround)
	- AC-3 (mono, stereo, surround)
	- MP3 aka MPEG-1 Layer III (mono, stereo)
	- Opus (mono, stereo)
	- PCM A-Law (mono, stereo, surround)
	- PCM Mu-Law (mono, stereo, surround)
	- PCM Linear unsigned 8-bits (mono, stereo, surround)
	- PCM Linear signed 16-bits (mono, stereo, surround)

- supported inputs:
	- raw elementary sub-stream files
		- raw H264/H265 files
		- raw AAC/AC-3/PCM files
		- raw MJPEG files
		- raw Opus/VP8 files (uses a proprietary file format though)
	- file containers:
		- audio/video: MKV, MP4, MOV, WEBM
		- audio: AAC, AC-3, MP3, Ogg(-Opus), Opus, WAV
	- other RTSP streams (if they use supported codecs)
	- proprietary Message Queues, e.g. for Foscam IP Cameras

- streams can be configured during runtime
