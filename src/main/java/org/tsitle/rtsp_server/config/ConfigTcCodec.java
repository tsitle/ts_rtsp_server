package org.tsitle.rtsp_server.config;

public enum ConfigTcCodec {

	AACLC,
	AC3,
	MP2,
	MP3,
	OPUS,
	PCMA,
	PCMU,
	LPCM16S,

	H264,
	H265,
	MJPEG,
	VP8;

	public boolean isVideo() {
		return switch (this) {
				case H264, H265, MJPEG, VP8 -> true;
				default -> false;
			};
	}

	public boolean isAudio() {
		return switch (this) {
				case AACLC, AC3, MP2, MP3, OPUS, PCMA, PCMU, LPCM16S -> true;
				default -> false;
			};
	}

}
