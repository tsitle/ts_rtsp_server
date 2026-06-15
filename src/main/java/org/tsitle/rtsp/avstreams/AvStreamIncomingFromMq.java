package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.mq.MqInternalSub;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;

import java.net.URI;
import java.util.Optional;

public final class AvStreamIncomingFromMq extends AvStreamIncomingBase {

	private final @NonNull MqInternalSub mqInternalSub;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param idStreamSource Stream source identifier
	 * @param inputUri Input URI
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncomingFromMq(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull URI inputUri
			) throws AvCannotOpenInputException {
		super(logMsgInterface, idStreamSource);

		if (inputUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI scheme cannot be null (inputUri='" + inputUri + "')");
		}
		if (! inputUri.getScheme().equals("https")) {
			throw new IllegalArgumentException("Input URI scheme must be 'https'");
		}

		//
		this.mqInternalSub = new MqInternalSub(logMsgInterface, idStreamSource);
		openInput();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void readFrame(@NonNull BufferExt buf) throws InputStreamIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".readFrame()";

		buf.clear();
		if (haveEos) {
			throw new InputStreamEosException();
		}
		try {
			while (true) {
				Optional<MqPacketAv> optPacket = mqInternalSub.receiveMessageAv(buf);
				if (optPacket.isPresent()) {
					break;
				}
				try {
					//noinspection BusyWait
					Thread.sleep(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
			}
		} catch (MqException e) {
			haveEos = true;
			logDebug(FNC_NAME, "MqException caught: " + e.getMessage());
			throw new InputStreamIoException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Rewinds the stream to the beginning
	 * @throws AvCannotOpenInputException If the input stream cannot be reopened
	 */
	@Override
	public void rewind() throws AvCannotOpenInputException {
		throw new AvCannotOpenInputException("Cannot rewind a MQ stream");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			mqInternalSub.close();
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		}
		//
		haveEos = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void openInput() throws AvCannotOpenInputException {
		try {
			mqInternalSub.connectToMq();
		} catch (MqException e) {
			throw new AvCannotOpenInputException("MqException caught: " + e.getMessage());
		}
	}

}
