package org.tsitle.rtsp.threads.rtsp;

public enum SessionState {

	INIT(0),
	READY(1),
	PLAYING(2);

	private final int value;

	SessionState(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}

}
