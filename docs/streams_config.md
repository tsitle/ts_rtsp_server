# Streams Configuration

## Introduction

See the sample 'Server Configuration' files

- [config/sample-config-no_ssl.json](../config/sample-config-no_ssl.json) (only `rtsp://` available)
- [config/sample-config-with_ssl.json](../config/sample-config-with_ssl.json) (both `rtsp://` and `rtsps://` available)

and the 'Stream Configuration' files in `config/sample-streams-config1/` and `config/sample-streams-config2/`.

To be able to use the 'with SSL' configuration, you need to generate your own SSL Certificate and Private Key:

- SSL Certificate \[required\]: `data/rtsps_ssl_keys/YOUR_HOSTNAME-server.crt`
- SSL Private Key \[required\]: `data/rtsps_ssl_keys/YOUR_HOSTNAME-server-private.key`
- SSL CA certificate \[optional\]: `data/rtsps_ssl_keys/YOUR_HOSTNAME-ca.crt`

There is a demo script in `data/rtsps_ssl_keys/keygen-EXAMPLE.sh` that can be used to generate the required files.  
You'll only need to edit it first and change the values of `LCFG_SERVER_HOST`, `LCFG_CERTID_xxx` and `LCFG_SAN_xxx` to your own values.

The files in the 'Stream Configuration' directories can be edited while the application is running.  
The changes will take effect immediately.

There can be multiple 'Stream Configuration' files, each defining a different set of streams.  
The `streams` and `subStreams` sections can be spread across multiple files or can be defined in a single file.

The entries in the `streams` section define which streams will be available publicly.  
Each entry in the `streams` section references one or two sub-streams.  
Each entry in the `subStreams` section defines the input source for one or two sub-streams,  
depending on the type of the `subStreams` entry:

- file containers (MKV, MP3, MP4, etc.) and other RTSP streams:  
	can produce one or two sub-streams (audio only, video only or audio+video)
- raw elementary sub-stream files and proprietary Message Queues:  
	can produce only one sub-stream (audio or video)


## RTSP Stream URLs

Example 'Server Configuration' file:

``` json
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

Example 'Stream Configuration' file:

``` json
{
	"streams": {
		"sample-garden_camera.stream": {
			"enabled": true,
			"needsAuthentication": true,
			"allowedUserAccountGroups": [ "grp_admin_only" ],
			"needsEncryption": true,
			"subStreamIds": [ "sample_input_garden_camera" ],
			"tags": { ... }
		}
		"sample-webm_with_vp8_and_opus.stream": {
			"enabled": true,
			"needsAuthentication": true,
			"allowedUserAccountGroups": [ "grp_all_users" ],
			"needsEncryption": false,
			"subStreamIds": [ "sample_input_webm_vp8_opus" ],
			"tags": { ... }
		},
		"sample-h265_and_aac.stream": {
			"enabled": true,
			"needsAuthentication": false,
			"allowedUserAccountGroups": [ ],
			"needsEncryption": false,
			"subStreamIds": [ "sample_input_video_h265", "sample_input_audio_aac" ],
			"tags": { ... }
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


## Forcing SRTP/SRTCP Encryption

The client can request that the media data (audio/video) shall be encrypted by the server - even if the server  
would not enforce encryption for a given stream.  
This can be handy for FFmpeg-based clients which do not request SRTP encryption on their own.


## 'Server Configuration' File Format

``` json
{
	"server": {
		"tcpPortRtsp": 1554,                                           | TCP Port for RTSP. Default is 554. Use -1 to disable
		"tcpPortRtsps": 1322,                                          | TCP Port for RTSPS (with SSL). Default is 322. Use -1 to disable
		"dataDir": "data",                                             | Path to the directory where the server will look for media files and SSL certificates/keys
		"sslCertificate": "rtsps_ssl_keys/YOUR_HOSTNAME-server.crt",   | SSL Certificate file path (within the dataDir)
		"sslKey": "rtsps_ssl_keys/YOUR_HOSTNAME-server-private.key",   | SSL Private Key file path (within the dataDir)
		"sslCa": "rtsps_ssl_keys/YOUR_HOSTNAME-ca.crt",                | SSL CA file path (within the dataDir)
		"threadsMaximumPlay": 20,                                      | Maximum number of threads for active client streaming connections. Default is 20
		"threadsMaximumTci": 20,                                       | Maximum number of threads for handling incoming TCP connections. Default is 20
		"threadsMaximumMq": 20,                                        | Maximum number of threads for connections to external Message Queues. Default is 20
		"threadsMaximumDmxRtsp": 20,                                   | Maximum number of threads for connections to external RTSP streams. Default is 20
		"threadsMaximumDmxJb": 20,                                     | Maximum number of threads for 'Jukebox' streams. Default is 20
		"enableRtpTransportUdp": true,                                 | Enable UDP RTP transport? Default is true
		"enableRtpTransportTcp": true                                  | Enable TCP RTP transport? Default is true
	},

	"logging": {
		"logLevel": "info",                                            | Log level. Default is "info". Options are "debug", "info", "warn", "error"
		"outputs": {
			"filename": "logs/%DATETIME%.log",                         | Template for log file name
			"enableFile": true,                                        | Enable logging to file? Default is false
			"enableConsole": true                                      | Enable logging to console? Default is false
		}
	},

	"debugging": {
		"debugPrintRtspRcvd": false,                                   | If the log level is "debug", print received RTSP messages?
		"debugPrintRtspSent": false,                                   | If the log level is "debug", print sent RTSP messages?
		"debugPrintRtspSdpSent": false                                 | If the log level is "debug", print sent RTSP SDP data?
	},

	"userAccounts": {                                                  | Dictionary of User Accounts
		"admin": "ABCDEFGH",                                           | Each User Account is a key-value pair with the username as the key and the password as the value
		"somebody": "thePassword" 
	},
	"userAccountGroups": {                                             | Dictionary of User Account Groups
		"grp_all_users": [ "admin", "somebody" ],                      | Each group is a list of User Account names
		"grp_admin_only": [ "admin" ]
	},

	"streamConfigDirectories": [                                       | List of directories containing 'Stream Configuration' files
		"sample-streams-config1",
		"sample-streams-config2"
	]
}
```


## 'Stream Configuration' File Format

``` json
{
	"streams": {                                                       | Dictionary of 'Streams'
		"sample-h265_and_aac.stream": {                                | Stream ID - this will be used in the RTSP URL
			"enabled": true,                                           | Enable this stream? Default is true
			"needsAuthentication": true,                               | Does this stream require user authentication? Default is true
			"allowedUserAccountGroups": [ "grp_admin_only" ],          | List of User Account Groups that are allowed to access this stream if needsAuthentication is true
			"needsEncryption": false,                                  | Does this stream need to be encrypted? Default is true. See note below.
			"subStreamIds": [ "input_h265", "input_aac" ],             | One or two 'Sub-Stream' IDs
			"tags": {                                                  | Tags for this stream. The client will receive these as part of the SDP data
				"streamName": "Sample H265 and AAC",                   | Name of the stream
				"streamDesc": "H265 aka HEVC video and AAC audio"      | Description of the stream
			}
		},
		"sample-raw-pcma.stream": {                                    | Stream ID - this will be used in the RTSP URL
			...
		}
	},

	"subStreams": {                                                    | Dictionary of 'Sub-Streams'
		"input_h265": {                                                | Sub-Stream ID
			"enabled": true,                                           | Enable this Sub-Stream? Default is true
			...
		},
		"input_aac": {                                                 | Sub-Stream ID
			...
		}
	}
}
```

**Note on `needsEncryption`:** The encryption requirement can either be satisfied by using TCP transport with SSL or  
by using TCP/UDP transport with SRTP.

Also note that the `streams` and `subStreams` dictionaries can be split into multiple files. They do not have
to be in the same file.  
It is also possible to split the `streams` dictionary into multiple files,
as well as to split the `subStreams` dictionary into multiple files.  


### Sub-Stream Configuration for 'Raw Files'

'Raw Files' are mainly intended for development/testing purposes.
They probably have little practical use in a production environment.

``` json
{
	"subStreams": {
		"sample_input_raw_video_h264": {
			"enabled": true,
			"rawFile": {
				"filePath": "samples/raw-video-30fps.h264",            | Path to the file (within the 'dataDir')
				"codec": "H264",                                       | Codec of the file. See note below
				"videoFps": 30.0                                       | Framerate in frames per second
			}
		},
		"sample_input_raw_audio_aac_surround": {
			"enabled": true,
			"rawFile": {
				"filePath": "samples/raw-audio-aac-surround_51_32000hz.aac",
				"codec": "AAC",
				"audioSamplerateHz": 32000,                            | Samplerate in Hz
				"audioChannelCount": 6,                                | Audio channel count (1 ^= mono, 2 ^= stereo, 5 or 6 ^= surround, ...)
				"aacSamplesPerFrame": 1024                             | Optional: only for AAC: Samples per frame. Default is 1024
			}
		},
		"sample_input_raw_audio_lpcm_s16_be": {
			"enabled": true,
			"rawFile": {
				"filePath": "samples/raw-audio-pcm-stereo_32000hz_s16b_be.raw",
				"codec": "LPCM16S",
				"audioSamplerateHz": 32000,
				"audioChannelCount": 2,
				"isPcmAudioBigEndian": true                            | optional: only for Linear PCM signed 16-bit: are PCM samples big-endian encoded? Default is false
			}
		}
	}
}
```

Options for `codec` for video files are:  

- 'H264': H.264 with AnnexB
- 'H265': H.265 aka HEVC with AnnexB
- 'MJPEG': Motion JPEG
- ('CSTM_VP8' for the custom VP8 file format supported by the server)

Options for `codec` for audio files are:

- 'AAC': Advanced Audio Codec with ADTS
- 'AC3': Audio Codec 3 aka Dolby Digital
- 'MPA': MPEG1-Audio Layer I/II/III
- 'PCMA': PCM a-law
- 'PCMU': PCM u-law aka mu-law
- 'LPCM08U': Linear PCM unsigned 8-bit
- 'LPCM16S': Linear PCM signed 16-bit
- ('CSTM_OPUS' for the custom Opus file format supported by the server)


### Sub-Stream Configuration for 'File Containers'

``` json
{
	"subStreams": {
		"sample_input_mkv_h264_aac": {
			"enabled": true,
			"fileContainer": {
				"filePath": "samples/fc-av-h264_aac.mkv"               | Path to the file (within the 'dataDir')
			}
		}
	}
}
```


### Sub-Stream Configuration for 'RTSP Input'

``` json
{
	"subStreams": {
		"sample_input_garden_camera": {
			"enabled": true,
			"rtsp": {
				"url": "rtsp://bob:secretPassword@192.168.1.100:88/videoMain"    | Complete RTSP URL of the source
			}
		}
	}
}
```


### Sub-Stream Configuration for 'Jukebox'

``` json
{
	"subStreams": {
		"sample_input_jukebox": {
			"enabled": true,
			"jukebox": {
				"folder": "samples",                                   | Path in which to search for inputs files (within the 'dataDir'), including sub-directories
				"transcode": {                                         | Transcoder settings
					"codec": "AAC",                                    | Target codec. Default is AAC. See note below
					"audioSamplerateHz": 48000,                        | Target samplerate in Hz. Default is 48000. Min=8000, Max=48000
					"audioChannelCount": 2,                            | Target channel count (1 or 2). Default is 2. Must be 1 or 2
					"audioBitrateKbps": 224                            | Target bitrate in kilobits per second. Default is 192. Min=8, Max=224
				}
			}
		}
	}
}
```

Options for `codec` are:

- 'AAC': Advanced Audio Codec
- 'AC3': Audio Codec 3 aka Dolby Digital
- 'MP2': MPEG1-Audio Layer II
- 'MP3': MPEG1-Audio Layer III
- 'OPUS': Opus
- 'PCMA': PCM a-law
- 'PCMU': PCM u-law aka mu-law
- 'LPCM16S': Linear PCM signed 16-bit


### Sub-Stream Configuration for 'Message Queues'

External Message Queues use a proprietary protocol both for authentication and media transmission.  
As of now I have not published a server implementation for this. The current implementation is written in C++  
and is not quite ready for publication.

``` json
{

	"subStreams": {
		"ip_camera_one_h265_mq": {
			"enabled": true,
			"mq": {
				"userAndPassword": "theUser:secretPassword",
				"hostAndPort": "192.168.1.1:7777",
				"resourceGroupAndChannel": "camera1:r_video"
			}
		},
		"ip_camera_one_pcm_mq": {
			"enabled": true,
			"mq": {
				"userAndPassword": "theUser:secretPassword",
				"hostAndPort": "192.168.1.1:7777",
				"resourceGroupAndChannel": "camera1:r_audio"
			}
		}
	}
}
```
