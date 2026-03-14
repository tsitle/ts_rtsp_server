package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqInternalPub implements AutoCloseable {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final int streamSourceId;

	private final @NonNull ZContext zmqContext;
	private ZMQ.@Nullable Socket zmqSocket;

	private final AtomicBoolean stateClosed = new AtomicBoolean(false);
	private final AtomicBoolean stateOpened = new AtomicBoolean(false);

	private boolean zmqContextOpened;

	private final @NonNull ByteBuffer cacheBufferData;


	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param streamSourceId Stream source identifier
	 */
	public MqInternalPub(
				@Nullable LogMsgInterface logMsgInterface,
				int streamSourceId
			) {
		this.logMsgInterface = logMsgInterface;
		this.streamSourceId = streamSourceId;

		//
		this.zmqContext = MqContextHelper.openMqContext();
		this.zmqContextOpened = true;

		//
		this.cacheBufferData = ByteBuffer.allocate(1024);
		this.cacheBufferData.order(ByteOrder.BIG_ENDIAN);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		String chanName = MqChannelBus.buildChannelNameForStreamSourceId(streamSourceId);
		int chanId = MqChannelBus.registerChannel(chanName);
		zmqSocket = MqChannelBus.createPublisher(chanId, zmqContext);

		stateOpened.set(true);
	}

	public void sendMessage(@NonNull MqPacketAv packet) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendMessage()";

		//
		ensureOpen(FNC_NAME);
		if (zmqSocket == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ socket not initialized");
		}

		//
		MqMessageEncoder.encodePacketAvForInternalMq(packet, cacheBufferData);

		zmqSocket.send(cacheBufferData.array(), 0, cacheBufferData.limit(), ZMQ.SNDMORE);
		zmqSocket.send(packet.payloadDataPtr().getBufPtr(), 0, packet.payloadDataPtr().getUsed(), 0);
		//logDebug(FNC_NAME, "int MQ write " + (packet.codec().isVideo() ? "VID" : "AUD"));
	}

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			if (stateClosed.compareAndSet(false, true) && zmqSocket != null) {
				zmqSocket.close();
				zmqSocket = null;
			}
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		}
		closeZmqContextInstance();
		stateOpened.set(false);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void closeZmqContextInstance() {
		if (zmqContextOpened) {
			MqContextHelper.closeMqContext();
			zmqContextOpened = false;
		}
	}

	private void ensureOpen(String fncName) throws MqException {
		if (! stateOpened.get() || stateClosed.get()) {
			throw new MqException(fncName + ": Stream not opened or already closed");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

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
