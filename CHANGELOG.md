# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [1.2.1] - 2026-10-28

### Changed

- complete overhaul of the TCP multiplexer/de-multiplexer

## [1.2.0] - 2026-10-24

### Added

- option in the main config file to enable/disable printing debug messages for RTCP Receiver Report statistics

### Changed

- consolidated all timing-related code to use a common timer class

## [1.1.9] - 2026-10-20

### Fixed

- missing UTF-8 support in Docker image when building native images

## [1.1.8] - 2026-10-18

### Changed

- improved handling of inbound SRTxP KMDs for re-keying
- improved support for UTF-8 strings, especially in FFmpeg-related methods

### Fixed

- calculation of the 'fraction lost' value of RTCP RR (and SR) packets
- an issue where the server would be stuck in a reception loop for longer than necessary
- missing retrieval of the RTSP methods that the client supports when doing outbound SRTxP re-keying

## [1.1.7] - 2026-10-08

### Changed

- made some minor changes geared towards usage of the library 'lib_xrtxp' in client-side code

### Fixed

- missing reflection entries in the GraalVM reachability metadata

## [1.1.6] - 2026-10-01

### Added

- added support for building native images (for Linux x64/arm64) using GraalVM
- added CLI arguments for showing help and version information (`--help` and `--version`)
- added demo SSL certificate/key generator script in `data/rtsps_ssl_keys/keygen-EXAMPLE.sh`

### Fixed

- an issue where an empty 'config=' entry in the FMTP line of the SDP data for an AAC stream was causing VLC to crash

## [1.1.5] - 2026-09-23

### Changed

- in the ThreadInpDmxJb class, a new transcoder context is now created for each input file  
	to ensure that all samples are being drained before the next file is processed

### Fixed

- some issues in various FFmpeg-related classes
- an issue in the audio transcoder where samples were being buffered incorrectly
- an issue where IPv4 addresses were being tried to be resolved to an IPv4 address

## [1.1.4] - 2026-09-18

### Added

- UDP/TCP transports for RTP/RTCP can now be enabled/disabled in the main config file
- added support for pushing 'FileTags' (for Jukebox streams) to compatible clients

### Fixed

- an issue in RtspProtoHighResponseConsumer where a Session ID in the response headers was unnecessarily expected

## [1.1.3] - 2026-09-18

### Added

- MPEG-1 audio files can now be used as 'raw' input files when they don't contain ID3 tags
- Streams now have a configurable 'name' and 'description' in the config files

### Changed

- VideoJpegParser can now determine with absolute certainty whether a JPEG frame uses default Huffman tables
- the HTTP client for establishing MQ connections is now validating SSL Certificates more strictly

### Fixed

- several issues in the client-side code of RTP packet parsers
- an issue in the client-side code of RtspProtoHighRequestProducer regarding authentication parameters
- an issue in the client-side code of RtspProtoHighResponseConsumer regarding parsing of DESCRIBE bodies
- an issue in the client-side code of RtxpTcpReadWrite
- an issue in ThreadRtspPlay when the client didn't call SETUP for all sub-streams

## [1.1.2] - 2026-09-07

### Fixed

- memory leak in FfmpegTcTranscoderBase

## [1.1] - 2026-08-25

### Added

- added sub-stream source type 'Jukebox'

### Changed

- use the new class ProUri for handling all URIs
- internally multicast incoming RTSP streams to all connected clients
- allow AAC audio streams without codec 'extradata'

### Fixed

- AvStreamIncoming not being closed when ThreadDataProvEsBase is stopped

## [1.0] - 2026-08-18

First release.
