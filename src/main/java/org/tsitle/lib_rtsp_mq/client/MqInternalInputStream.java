package org.tsitle.lib_rtsp_mq.client;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_rtsp_mq.common.MqInternalSub;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqPacketAv;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_rtsp_mq.exceptions.MqException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Input stream wrapper for an internal Message Queue.<br />
 * Wraps the input from the Message Queue into an InputStream.
 */
@SuppressWarnings("unused")
public final class MqInternalInputStream extends InputStream {

	private final @Nullable LogMsgInterface logMsgInterface;

	private final @NonNull MqInternalSub mqInternalSub;

	private final @NonNull BufferExt bufferData = new BufferExt();
	private int bufferPos = 0;
	private boolean isFirstMessage = true;

	private final AtomicBoolean stateClosed = new AtomicBoolean(false);
	private final AtomicBoolean stateOpened = new AtomicBoolean(false);

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param idEsSource Elementary-Stream source identifier
	 */
	public MqInternalInputStream(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdEsSource idEsSource
			) {
		this.logMsgInterface = logMsgInterface;

		//
		this.mqInternalSub = new MqInternalSub(logMsgInterface, idEsSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Connect to the Message Queue.
	 * @throws MqException If an error has occurred
	 */
	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		mqInternalSub.connectToMq();

		stateOpened.set(true);
	}

	/**
	 * Read a single byte from the stream.
	 * @return The byte read, or -1 if the end of the stream has been reached
	 * @throws IOException If an I/O error occurs
	 */
	@Override
	public int read() throws IOException {
		if (bufferPos >= bufferData.getUsed()) {
			receiveMessageAv();
			if (bufferData.isEmpty()) {
				return -1;
			}
		}

		return (bufferData.get(bufferPos++) & 0xFF);
	}

	/**
	 * Read bytes from the stream into the provided buffer.
	 * @param b The buffer to read into
	 * @param offs The offset within the buffer to start writing
	 * @param len The number of bytes to read
	 * @return The number of bytes read, or -1 if the end of the stream has been reached
	 * @throws IOException If an I/O error occurs
	 */
	@Override
	public int read(byte[] b, final int offs, final int len) throws IOException {
		Objects.checkFromIndexSize(offs, len, b.length);

		int totalCopied = 0;
		while (totalCopied < len) {
			if (bufferPos >= bufferData.getUsed()) {
				receiveMessageAv();
				if (bufferData.isEmpty()) {
					return -1;
				}
			}

			int remainingInInp = bufferData.getUsed() - bufferPos;
			int toCopy = Math.min(len - totalCopied, remainingInInp);

			bufferData.copyInto(bufferPos, b, offs + totalCopied, toCopy);

			bufferPos += toCopy;
			totalCopied += toCopy;
		}
		return totalCopied;
	}

	/**
	 * Close the Message Queue.
	 */
	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			if (stateClosed.compareAndSet(false, true)) {
				mqInternalSub.close();
			}
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private synchronized void receiveMessageAv() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveMessageAv()";

		bufferPos = 0;
		bufferData.clear();

		//
		if (! stateOpened.get()) {
			throw new IllegalStateException(FNC_NAME + ": Stream has not been opened");
		}
		if (stateClosed.get()) {
			return;
		}

		//
		try {
			Optional<MqPacketAv> optPacket = Optional.empty();
			for (int i = 0; i < 2; i++) {
				optPacket = mqInternalSub.receiveMessageAv(bufferData);
				if (! isFirstMessage || optPacket.isPresent()) {
					break;
				}
				isFirstMessage = false;
			}
			if (optPacket.isEmpty()) {
				if (! stateClosed.get()) {
					logError(FNC_NAME, "received nothing from MQ");
				}
			} /*else {
				logDebug(FNC_NAME, "Received int MQ Packet " + (optPacket.get().codec().isVideo() ? "VID" : "AUD"));
			}*/
		} catch (MqException e) {
			throw new IOException("MqException caught: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.DEBUG, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

	private void logError(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.ERROR, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
