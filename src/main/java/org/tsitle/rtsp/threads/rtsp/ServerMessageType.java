package org.tsitle.rtsp.threads.rtsp;

public enum ServerMessageType {

	UNKNOWN(-1),
	SETUP(0),
	PLAY(1),
	PAUSE(2),
	TEARDOWN(3),
	DESCRIBE(4),
	OPTIONS(5);

	private final int value;

	ServerMessageType(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}

}
