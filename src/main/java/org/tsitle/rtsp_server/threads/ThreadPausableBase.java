package org.tsitle.rtsp_server.threads;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ThreadPausableBase extends ThreadBase {

	protected final AtomicBoolean isPaused = new AtomicBoolean(false);

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 */
	public ThreadPausableBase(@NonNull LogMsgInterface logMsgInterface) {
		super(logMsgInterface);
	}

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
