# Test Matrix

| RTSP Client                                              | OS                                          | Auth Supported | SRTP Supported | RTSPS Supported | Codecs Supported | Transports Supported |
|----------------------------------------------------------|---------------------------------------------|----------------|----------------|-----------------|------------------|----------------------|
| VLC 3.0.23 (LIVE555 v2020.11.05)                         | Linux x86 (Rocky Linux)                     | Yes            | Yes            | No              | all              | TCP, UDP             |
| VLC 3.0.23 (LIVE555 v2016.11.28)                         | macOS x86 (Sonoma), Win x86                 | Yes            | No             | No              | all              | TCP, UDP             |
| GStreamer 1.24.2, 1.24.11                                | Linux x86 (Rocky Linux, KUbuntu)            | Yes            | (broken)       | Yes             | all              | TCP, UDP             |
| FFplay (7.1.2 with Lavf61.7.100, 8.1 with Lavf62.12.100) | Linux x86 (Rocky Linux), macOS x86 (Sonoma) | Yes            | (broken)       | Yes             | all              | TCP, UDP             |
| Win RTSP Player (LIVE555 v2016.05.20)                    | Win x86                                     | Yes            | No             | No              | all but LPCM16   | TCP, UDP             |
| Another RTSP (LIVE555 v2016.05.20)                       | Win x86                                     | Yes            | No             | No              | all but LPCM16   | TCP, UDP             |
| RTSP Player (Lavf59.27.100)                              | macOS x86 (Sonoma), Win x86                 | Yes            | No             | Yes             | all              | TCP                  |
| OpenRTSP (LIVE555 v2026.04.01)                           | Linux x86 (Debian)                          | Yes            | Yes            | Yes             | all              | TCP, UDP             |

All Codecs: H264, H265, MJPEG, AAC, PCMU (G711U), LPCM16

RTSP Clients:

- [VLC](https://www.videolan.org/vlc/)
- [GStreamer](https://gstreamer.freedesktop.org/)
- [FFplay](https://ffmpeg.org/)
- not really great: [Win RTSP Player (Windows) - predecessor of 'Another RTSP'](https://github.com/e1z0/Win-RTSP-Player)
- not really great: [Another RTSP (Windows)](https://github.com/e1z0/AnotherRTSP)
- pretty good but minimalistic: [RTSP Player (Linux/macOS/Windows)](https://gardinal.net/rtsp-player/)
- only for testing via console but great: [OpenRTSP (Linux/macOS/Windows)](http://www.live555.com/openRTSP/)
