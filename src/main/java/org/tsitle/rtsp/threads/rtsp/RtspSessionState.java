package org.tsitle.rtsp.threads.rtsp;

public enum RtspSessionState {

	INIT(0),
	READY(1),
	PLAYING(2);

	private final int value;

	RtspSessionState(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}

}
