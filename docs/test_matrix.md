# Test Matrix

| RTSP Client                                              | OS                                          | Auth Supported | RTSPS Supported | SRTP Supported (*1) | RTSPS+SRTP+UDP Supported | Transports Supported | Codecs Supported |
|----------------------------------------------------------|---------------------------------------------|----------------|-----------------|---------------------|--------------------------|----------------------|------------------|
| VLC 3.0.23 (LIVE555 v2020.11.05)                         | Linux x86 (Rocky Linux)                     | Yes            | No              | Yes                 | No                       | TCP, UDP             | all              |
| VLC 3.0.23 (LIVE555 v2016.11.28)                         | macOS x86 (Sonoma), Win x86                 | Yes            | No              | No                  | No                       | TCP, UDP             | all              |
| VLC 3.0.23 (LIVE555 v2016.11.21)                         | iOS                                         | Yes            | No              | No                  | No                       | TCP                  | all              |
| GStreamer 1.24.2, 1.24.11                                | Linux x86 (Rocky Linux, KUbuntu)            | Yes            | Yes             | Yes (*2)            | Yes                      | TCP, UDP             | all              |
| FFplay (7.1.2 with Lavf61.7.100, 8.1 with Lavf62.12.100) | Linux x86 (Rocky Linux), macOS x86 (Sonoma) | Yes            | Yes             | Yes (*3)            | No                       | TCP, UDP             | all              |
| Win RTSP Player (LIVE555 v2016.05.20)                    | Win x86                                     | Yes            | No              | No                  | No                       | TCP, UDP             | all but LPCM16   |
| Another RTSP (LIVE555 v2016.05.20)                       | Win x86                                     | Yes            | No              | No                  | No                       | TCP, UDP             | all but LPCM16   |
| RTSP Player (Lavf59.27.100)                              | macOS x86 (Sonoma), Win x86                 | Yes            | Yes             | Yes (*3)            | No                       | TCP                  | all              |
| OpenRTSP (LIVE555 v2026.04.01)                           | Linux x86 (Debian)                          | Yes            | Yes             | Yes (*4)            | Yes                      | TCP, UDP             | all              |

All Codecs: H264, H265, MJPEG, AAC, PCMU (G711U), LPCM16


Note 1) SRTP:  
        - none of the tested RTSP clients support a Key Derivation Rate (KDR) parameter in the MIKEY/SDES message other than 0.  
        - none of the tested RTSP clients support re-keying the master key and salt mid-session.  
        As a result, SRTP connections will only be allowed to run for a limited time and will then be terminated by the server.  
        This prevents vulnerability against cryptanalysis and ensures secure communication.

Note 2) GStreamer's support for SRTP is somewhat broken. Bug #1 results in inbound MIKEY messages that contain a wrong  
        Authentication Tag length, and bug #2 results in GStreamer being unable to handle MKIs in MIKEY messages.  
        To enable SRTP with UDP transport when accessing a stream over RTSPS the URL query parameter `?srtp=1` must be used.

Note 3) FFplay's (Lavf) support for SRTP is somewhat broken. It doesn't support MIKEY key management - only the legacy SDES key management.  
        To enable SRTP the URL query parameter `?srtp=1` must be used.  
        Also, FFplay will always use TCP transport for RTP/RTCP when accessing a stream over RTSPS.  
        And lastly, there is a bug Lavf that results in SETUP requests for SRTP-enabled sub-streams that are actually  
        requests for unencrypted RTP sub-streams even though Lavf will use SRTP.

Note 4) OpenRTSP's support for SRTP is excellent.  
        To enable SRTP with UDP transport when accessing a stream over RTSPS the URL query parameter `?srtp=1` must be used.


RTSP Clients:

- [VLC](https://www.videolan.org/vlc/)
- [GStreamer](https://gstreamer.freedesktop.org/)
- [FFplay](https://ffmpeg.org/)
- not really great: [Win RTSP Player (Windows) - predecessor of 'Another RTSP'](https://github.com/e1z0/Win-RTSP-Player)
- not really great: [Another RTSP (Windows)](https://github.com/e1z0/AnotherRTSP)
- pretty good but minimalistic: [RTSP Player (Linux/macOS/Windows)](https://gardinal.net/rtsp-player/)
- only for testing via console but great: [OpenRTSP (Linux/macOS/Windows)](http://www.live555.com/openRTSP/)
