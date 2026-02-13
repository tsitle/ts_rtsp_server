package org.tsitle.rtsp.threads;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ThreadPausableBase extends ThreadBase {

	protected final AtomicBoolean isPaused = new AtomicBoolean(false);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void pauseThread() {
		isPaused.set(true);
	}

	public synchronized void unpauseThread() {
		isPaused.set(false);
	}

	public synchronized boolean isPaused() {
		return isPaused.get();
	}

}
