package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class BlockedState {

	enum Flag { SOCKET_READ, BUFFER_READ, SOCKET_WRITE, BUFFER_WRITE }

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition stateChanged = lock.newCondition();

	private boolean socketReadBlocked;
	private boolean bufferReadBlocked;
	private boolean socketWriteBlocked;
	private boolean bufferWriteBlocked;
	private boolean doStop;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void waitForUnblockedAndThenBlock(@NonNull Flag flag) throws InterruptedException {
		lock.lock();
		try {
			while (! doStop && isBlocked(flag)) {
				stateChanged.await();
			}
			if (doStop) {
				return;
			}
			setBlocked(flag, true);
		} finally {
			lock.unlock();
		}
	}

	void unblock(@NonNull Flag flag) {
		setBlocked(flag, false);
	}

	@SuppressWarnings("unused")
	void stop() {
		lock.lock();
		try {
			doStop = true;
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void setBlocked(@NonNull Flag flag, boolean value) {
		lock.lock();
		try {
			setBlockedInternal(flag, value);
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	private boolean isBlocked(@NonNull Flag flag) {
		return switch (flag) {
			case SOCKET_READ -> socketReadBlocked;
			case BUFFER_READ -> bufferReadBlocked;
			case SOCKET_WRITE -> socketWriteBlocked;
			case BUFFER_WRITE -> bufferWriteBlocked;
		};
	}

	private void setBlockedInternal(@NonNull Flag flag, boolean value) {
		switch (flag) {
			case SOCKET_READ -> socketReadBlocked = value;
			case BUFFER_READ -> bufferReadBlocked = value;
			case SOCKET_WRITE -> socketWriteBlocked = value;
			case BUFFER_WRITE -> bufferWriteBlocked = value;
		}
	}

}
