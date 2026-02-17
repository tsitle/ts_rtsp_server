package org.tsitle.rtsp.threads.rtp;

final class AdaptiveSendIntervalStats {

	double nextSendTimeNs = 0.0;
	long sleepCounter = 0;
	boolean needAlternatingDelta = false;

}
