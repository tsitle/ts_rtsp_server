package org.tsitle.rtsp.threads;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ThreadBase extends Thread {

	protected final AtomicBoolean doStop = new AtomicBoolean(false);
	protected final AtomicBoolean isRunning = new AtomicBoolean(false);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void stopThread() {
		doStop.set(true);
		stopThreadHook();
		while (isRunning.get()) {
			try {
				//noinspection BusyWait
				Thread.sleep(10);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	@SuppressWarnings("unused")
	public synchronized boolean isRunning() {
		return isRunning.get();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void stopThreadHook();

}
