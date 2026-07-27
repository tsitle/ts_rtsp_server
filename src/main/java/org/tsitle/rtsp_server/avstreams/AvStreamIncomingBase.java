package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.util.Optional;

public abstract class AvStreamIncomingBase implements AutoCloseable {

	protected final @Nullable LogMsgInterface logMsgInterface;
	protected final @NonNull RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();

	protected boolean haveEos = false;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param idEsSource Elementary-Stream Source identifier
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	protected AvStreamIncomingBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdEsSource idEsSource
			) throws AvCannotOpenInputException {
		this.logMsgInterface = logMsgInterface;
		this.idEsSource.copyFrom(idEsSource);
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

	/**
	 * Returns the video frames per second if available
	 * @return Video frames per second
	 */
	public abstract Optional<Double> getVideoFps();

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
