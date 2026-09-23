# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [1.1.5] - 2026-09-23

### Changed

- in the ThreadInpDmxJb class, a new transcoder context is now created for each input file  
	to ensure that all samples are being drained before the next file is processed

### Fixed

- fixed some issues in various FFmpeg related classes
- fixed an issue in the audio transcoder where samples were being buffered incorrectly
- fixed an issue where IPv4 addresses were being tried to be resolved to an IPv4 address

## [1.1.4] - 2026-09-18

### Added

- UDP/TCP transports for RTP/RTCP can now be enabled/disabled in the main config file
- added support for pushing 'FileTags' (for Jukebox streams) to compatible clients

### Fixed

- fixed an issue in RtspProtoHighResponseConsumer where a Session ID in the response headers was unnecessarily expected

## [1.1.3] - 2026-09-18

### Added

- MPEG-1 audio files can now be used as 'raw' input files when they don't contain ID3 tags
- Streams now have a configurable 'name' and 'description' in the config files

### Changed

- VideoJpegParser can now determine with absolute certainty whether a JPEG frame uses default Huffman tables
- the HTTP client for establishing MQ connections is now validating SSL Certificates more strictly

### Fixed

- fixed several issues in the client-side code of RTP packet parsers
- fixed an issue in the client-side code of RtspProtoHighRequestProducer regarding authentication parameters
- fixed an issue in the client-side code of RtspProtoHighResponseConsumer regarding parsing of DESCRIBE bodies
- fixed an issue in the client-side code of RtxpTcpReadWrite
- fixed an issue in ThreadRtspPlay when the client didn't call SETUP for all sub-streams

## [1.1.2] - 2026-09-07

### Fixed

- fixed memory leak in FfmpegTcTranscoderBase

## [1.1] - 2026-08-25

### Added

- added sub-stream source type 'Jukebox'

### Changed

- use the new class ProUri for handling all URIs
- internally multicast incoming RTSP streams to all connected clients
- allow AAC audio streams without codec 'extradata'

### Fixed

- fixed AvStreamIncoming not being closed when ThreadDataProvEsBase is stopped

## [1.0] - 2026-08-18

First release.
