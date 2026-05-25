# Test Matrix

| RTSP Client                                              | OS                                          | Auth Supported | SRTP Supported | RTSPS Supported | Codecs Supported | Transports Supported |
|----------------------------------------------------------|---------------------------------------------|----------------|----------------|-----------------|------------------|----------------------|
| VLC 3.0.23 (LIVE555 v2020.11.05)                         | Linux x86 (Rocky Linux)                     | Yes            | Yes            | No              | all              | TCP, UDP             |
| VLC 3.0.23 (LIVE555 v2016.11.28)                         | macOS x86 (Sonoma), Win x86                 | Yes            | No             | No              | all              | TCP, UDP             |
| GStreamer 1.24.2, 1.24.11                                | Linux x86 (Rocky Linux, KUbuntu)            | Yes            | (broken)       | Yes             | all              | TCP, UDP             |
| FFplay (7.1.2 with Lavf61.7.100, 8.1 with Lavf62.12.100) | Linux x86 (Rocky Linux), macOS x86 (Sonoma) | Yes            | Yes (*1)       | Yes             | all              | TCP, UDP             |
| Win RTSP Player (LIVE555 v2016.05.20)                    | Win x86                                     | Yes            | No             | No              | all but LPCM16   | TCP, UDP             |
| Another RTSP (LIVE555 v2016.05.20)                       | Win x86                                     | Yes            | No             | No              | all but LPCM16   | TCP, UDP             |
| RTSP Player (Lavf59.27.100)                              | macOS x86 (Sonoma), Win x86                 | Yes            | Yes (*1)       | Yes             | all              | TCP                  |
| OpenRTSP (LIVE555 v2026.04.01)                           | Linux x86 (Debian)                          | Yes            | Yes (*2)       | Yes             | all              | TCP, UDP             |

All Codecs: H264, H265, MJPEG, AAC, PCMU (G711U), LPCM16

Note 1) FFplay's (Lavf) support for SRTP is somewhat broken. It doesn't support MIKEY key management - only the legacy SDES key management.  
        To enable SRTP the URL query parameter `?srtp=1` must be used.  
        Also, FFplay will always use TCP transport for RTP/RTCP when accessing a stream over RTSPS.

Note 2) OpenRTSP's support for SRTP is excellent. When using the URL query parameter `?srtp=1` it even supports
        SRTP over UDP when accessing a stream over RTSPS.

Note 3) None of the tested RTSP clients support a Key Derivation Rate (KDR) parameter in the MIKEY message other than 0.

RTSP Clients:

- [VLC](https://www.videolan.org/vlc/)
- [GStreamer](https://gstreamer.freedesktop.org/)
- [FFplay](https://ffmpeg.org/)
- not really great: [Win RTSP Player (Windows) - predecessor of 'Another RTSP'](https://github.com/e1z0/Win-RTSP-Player)
- not really great: [Another RTSP (Windows)](https://github.com/e1z0/AnotherRTSP)
- pretty good but minimalistic: [RTSP Player (Linux/macOS/Windows)](https://gardinal.net/rtsp-player/)
- only for testing via console but great: [OpenRTSP (Linux/macOS/Windows)](http://www.live555.com/openRTSP/)
