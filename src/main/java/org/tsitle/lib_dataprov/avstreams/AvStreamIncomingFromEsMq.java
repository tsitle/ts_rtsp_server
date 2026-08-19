package org.tsitle.lib_dataprov.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_mq.exceptions.MqException;
import org.tsitle.lib_mq.common.MqInternalSub;
import org.tsitle.lib_mq.common.mqdata.MqPacketAv;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.net.URI;
import java.util.Optional;

public final class AvStreamIncomingFromEsMq extends AvStreamIncomingBase {

	private final @NonNull MqInternalSub mqInternalSub;

	private @Nullable Long prevTimestampEpochMs = null;
	private @Nullable TimestampMonotonic prevTimestampMono = null;

	private @Nullable Double videoFps = null;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param idEsSource Elementary-Stream source identifier
	 * @param inputUri Input URI
	 * @throws AvCannotOpenInputException If the input stream cannot be opened
	 */
	public AvStreamIncomingFromEsMq(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull URI inputUri
			) throws AvCannotOpenInputException {
		super(logMsgInterface, idEsSource);

		if (inputUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI scheme cannot be null (inputUri='" + inputUri + "')");
		}
		if (! (inputUri.getScheme().equals("http") || inputUri.getScheme().equals("https"))) {
			throw new IllegalArgumentException("Input URI scheme must be 'http' or 'https'");
		}

		//
		this.mqInternalSub = new MqInternalSub(logMsgInterface, idEsSource);
		openInput();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void readFrame(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".readFrame()";

		stTimestamp.clear();
		buf.clear();
		if (haveEos) {
			throw new InputStreamEosException();
		}
		try {
			while (true) {
				Optional<MqPacketAv> optPacket = mqInternalSub.receiveMessageAv(buf);
				if (optPacket.isPresent()) {
					if (! optPacket.get().mdTimestampEpochMs().isEmpty()) {
						long curTsMs = optPacket.get().mdTimestampEpochMs().getEpochNsUnsigned64bit().orElseThrow() / 1_000_000L;
						if (prevTimestampEpochMs == null || prevTimestampMono == null) {
							prevTimestampMono = TimestampMonotonic.ofNow();
						} else {
							long deltaMs = curTsMs - prevTimestampEpochMs;
							prevTimestampMono = TimestampMonotonic.ofMsUnsigned64bit(
									(prevTimestampMono.getNsUnsigned64bit().orElseThrow() / 1_000_000L) + deltaMs
								);
						}
						stTimestamp.copyFrom(prevTimestampMono);
						prevTimestampEpochMs = curTsMs;
					} else {
						stTimestamp.clear();
					}
					//
					videoFps = optPacket.get().mdVideoFps().getFrDbl();
					//
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

	@Override
	public Optional<Double> getVideoFps() {
		return Optional.ofNullable(videoFps);
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
