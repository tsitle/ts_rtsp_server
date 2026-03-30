# Test Matrix

| RTSP Client                           | OS                      | Auth Supported | SRTP Supported | RTSPS Supported | Codecs Supported | Transports Supported |
|---------------------------------------|-------------------------|----------------|----------------|-----------------|------------------|----------------------|
| VLC 3.0.23 (LIVE555 v2020.11.05)      | Linux x86 (Rocky Linux) | Yes            | Yes            | No              | all              | TCP, UDP             |
| VLC 3.0.21 (LIVE555 v2016.11.28)      | Win x86                 | Yes            | No             | No              | all              | TCP, UDP             |
| VLC 3.0.23 (LIVE555 v2016.11.28)      | macOS x86 (Sonoma)      | Yes            | No             | No              | all              | TCP, UDP             |
| GStreamer 1.24.11                     | Linux x86 (Rocky Linux) | Yes            | No             | Yes             | all              | TCP, UDP             |
| GStreamer 1.24.2                      | Linux x86 (KUbuntu)     | Yes            | No             | Yes             | all              | TCP, UDP             |
| FFplay 7.1.2 (Lavf61.7.100)           | Linux x86 (Rocky Linux) | Yes            | No             | Yes             | all              | TCP, UDP             |
| Win RTSP Player (LIVE555 v2016.05.20) | Win x86                 | Yes            | No             | No              | all but LPCM16   | TCP, UDP             |
| Another RTSP (LIVE555 v2016.05.20)    | Win x86                 | Yes            | No             | No              | all but LPCM16   | TCP, UDP             |
| RTSP Player (Lavf59.27.100)           | Win x86                 | Yes            | No             | Yes             | all              | TCP                  |

All Codecs: H264, H265, MJPEG, AAC, PCMU (G711U), LPCM16

RTSP Clients:

- [VLC](https://www.videolan.org/vlc/)
- [GStreamer](https://gstreamer.freedesktop.org/)
- [FFplay](https://ffmpeg.org/)
- [Win RTSP Player (Windows) - predecessor of 'Another RTSP'](https://github.com/e1z0/Win-RTSP-Player)
- [Another RTSP (Windows)](https://github.com/e1z0/AnotherRTSP)
- [RTSP Player (Linux/macOS/Windows)](https://gardinal.net/rtsp-player/)
