# Streams Configuration

## Introduction

See the sample server configuration files

- [config/sample-config-no_ssl.json](../config/sample-config-no_ssl.json) (only `rtsp://` available)
- [config/sample-config-with_ssl.json](../config/sample-config-with_ssl.json) (both `rtsp://` and `rtsps://` available)

and the 'Streams Configuration' files in `config/sample-streams-config1/` and `config/sample-streams-config2/`.

To be able to use the 'with SSL' configuration, you need to generate your own SSL Certificate and Private Key:

- SSL Certificate \[required\]: `data/rtsps_ssl_keys/YOUR_HOSTNAME-server.crt`
- SSL Private Key \[required\]: `data/rtsps_ssl_keys/YOUR_HOSTNAME-server-private.key`
- SSL CA certificate \[optional\]: `data/rtsps_ssl_keys/YOUR_HOSTNAME-ca.crt`

There is a demo script in `data/rtsps_ssl_keys/keygen-EXAMPLE.sh` that can be used to generate the required files.  
You'll only need to edit it first and change the values of `LCFG_SERVER_HOST`, `LCFG_CERTID_xxx` and `LCFG_SAN_xxx` to your own values.

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

## RTSP Stream URLs

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
