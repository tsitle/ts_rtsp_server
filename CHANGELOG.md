# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

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
