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

There can be multiple 'Streams Configuration' files, each defining a different set of streams.  
The `streams` and `subStreams` sections can be spread across multiple files or can be defined in a single file.

The entries in the `streams` section define which streams will be available publicly.  
Each entry in the `streams` section references one or two sub-streams.  
Each entry in the `subStreams` section defines the input source for one or two sub-streams,  
depending on the type of the `subStreams` entry:

- file containers (MKV, MP3, MP4, etc.) and other RTSP streams:  
	can produce one or two sub-streams (audio only, video only or audio+video)
- raw elementary sub-stream files and proprietary Message Queues:  
	can produce only one sub-stream (audio or video)

## Media Files, SSL Certificates & Co.

All media files and server's SSL certificate and key must be either directly in the  
directory `data/` or inside a subdirectory of `data/`.

## Running the Application

The application can be launched with Gradle and requires Java 25 or higher.  
There is only one argument that needs to be passed: the path to the configuration file:

```
./gradlew run --args="config/sample-config.json"
```

## RTSP Stream URLs

An URL for an RTSP stream consists of the following parts:

- protocol: either `rtsp://` or `rtsps://` (the latter is encrypted and requires a valid SSL certificate).  
  **Note** that not every client application supports encrypted RTSPS (`rtsps://`).  
  See [docs/test_matrix.md](docs/test_matrix.md) for more details.
- credentials \[optional\]: username and password separated by a colon, followed by an at sign (`@`).  
	**Note** that credentials should ideally only be used when using encrypted RTSPS (`rtsps://`)
- host: the hostname or IP address of the server
- port \[optional\]: the port number of the server  
	For `rtsp://` the default is 554 and for `rtsps://` the default is 322.  
	(in the sample configuration file, the ports are set to 1554 and 1322)
- path: the path to the stream on the server
- query parameters \[optional\]: the only supported parameter is `?srtp=1` to force SRTP encryption even if the client  
	application didn't request it correctly (this helps with FFmpeg/FFplay for instance).  
	See [docs/test_matrix.md](docs/test_matrix.md) for more details.

The TCP ports for `rtsp://` and `rtsps://` can be changed in the configuration file.

The `path` part of the URL is defined by the key of the entries in the `streams` section in the 'Streams Configuration' files.

Example application configuration file:

```
{
	"server": {
		"tcpPortRtsp": 1554,
		"tcpPortRtsps": 1322,
		...
	},

	"logging": { ... },

	"debugging": { ... },

	"userAccounts": {
		"admin": "ABCDEFGH",
		"somebody": "thePassword"
	},
	"userAccountGroups": {
		"grp_all_users": [ "admin", "somebody" ],
		"grp_admin_only": [ "admin" ]
	},

	"streamConfigDirectories": [ ... ]
}
```

Example 'Streams Configuration' file:

```
{
	"streams": {
		"sample-garden_camera.stream": {
			"enabled": true,
			"needsAuthentication": true,
			"allowedUserAccountGroups": [ "grp_admin_only" ],
			"needsEncryption": true,
			"subStreamIds": [ "sample_input_garden_camera" ]
		}
		"sample-webm_with_vp8_and_opus.stream": {
			"enabled": true,
			"needsAuthentication": true,
			"allowedUserAccountGroups": [ "grp_all_users" ],
			"needsEncryption": false,
			"subStreamIds": [ "sample_input_webm_vp8_opus" ]
		},
		"sample-h265_and_aac.stream": {
			"enabled": true,
			"needsAuthentication": false,
			"allowedUserAccountGroups": [ ],
			"needsEncryption": false,
			"subStreamIds": [ "sample_input_video_h265", "sample_input_audio_aac" ]
		}
	},
	"subStreams": {
		"sample_input_garden_camera": { ... },
		"sample_input_webm_vp8_opus": { ... },
		"sample_input_video_h265": { ... },
		"sample_input_audio_aac": { ... }
	}
}
```

This will yield the following RTSP URLs:

```
(allowed users: 'admin')
rtsp://admin:ABCDEFGH@localhost:1554/sample-garden_camera.stream
rtsps://admin:ABCDEFGH@localhost:1322/sample-garden_camera.stream

(allowed users: 'admin' and 'somebody')
rtsp://somebody:thePassword@localhost:1554/sample-webm_with_vp8_and_opus.stream
rtsps://somebody:thePassword@localhost:1322/sample-webm_with_vp8_and_opus.stream

(anonymous access)
rtsp://localhost:1554/sample-h265_and_aac.stream
rtsps://localhost:1322/sample-h265_and_aac.stream
```

If the TCP ports are set to their default values (RTSP `554` and RTSPS `322`), then the port number can be omitted:

```
rtsp://admin:ABCDEFGH@localhost/sample-garden_camera.stream
rtsps://admin:ABCDEFGH@localhost/sample-garden_camera.stream
```
