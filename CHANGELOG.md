# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

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
