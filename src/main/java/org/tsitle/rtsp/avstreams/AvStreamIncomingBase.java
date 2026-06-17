package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdStreamSource;

public abstract class AvStreamIncomingBase implements AutoCloseable {

	protected final @Nullable LogMsgInterface logMsgInterface;
	protected final @NonNull RtspProtoIdStreamSource idStreamSource = RtspProtoIdStreamSource.ofEmpty();

	protected boolean haveEos = false;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param idStreamSource Stream source identifier
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	protected AvStreamIncomingBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdStreamSource idStreamSource
			) throws AvCannotOpenInputException {
		this.logMsgInterface = logMsgInterface;
		this.idStreamSource.copyFrom(idStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Rewinds the stream to the beginning
	 * @throws AvCannotOpenInputException If the input stream cannot be reopened
	 */
	public abstract void rewind() throws AvCannotOpenInputException;

	/**
	 * Checks if we can still read data from the stream.
	 * @return True if the end of the stream has been reached, false otherwise
	 */
	public boolean haveEos() {
		return haveEos;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalLog(@NonNull RtxpLogLevel level, @NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(level, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
