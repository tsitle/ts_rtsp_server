package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publisher for internal messages.
 */
public class MqInternalPub implements AutoCloseable {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoIdStreamSource idStreamSource = new RtspProtoIdStreamSource();

	private final @NonNull ZContext zmqContext;
	private ZMQ.@Nullable Socket zmqSocket;
	private boolean zmqContextOpened;

	private final AtomicBoolean stateClosed = new AtomicBoolean(false);
	private final AtomicBoolean stateOpened = new AtomicBoolean(false);

	private @Nullable MqMsgHandlerBase msgHandler;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param idStreamSource Stream source identifier
	 */
	public MqInternalPub(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdStreamSource idStreamSource
			) {
		this.logMsgInterface = logMsgInterface;
		this.idStreamSource.copyFrom(idStreamSource);

		//
		this.zmqContext = MqContextHelper.openMqContext();
		this.zmqContextOpened = true;
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
		String chanName = MqChannelBus.buildChannelNameForStreamSourceId(idStreamSource);
		int chanId = MqChannelBus.registerChannel(chanName);
		zmqSocket = MqChannelBus.createPublisher(chanId, zmqContext);
		final ZMQ.Poller zmqPollerObj = zmqContext.createPoller(1);
		final int zmqPollerIxWrite = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLOUT);

		msgHandler = MqMsgHandlerFactory.createHandlerInternalMq(zmqSocket, zmqPollerObj, zmqPollerIxWrite);

		stateOpened.set(true);
	}

	/**
	 * Send a message containing audio/video data to the Message Queue.
	 * @param packet A/V packet
	 * @throws MqException If an error has occurred
	 */
	public void sendMessageAv(@NonNull MqPacketAv packet) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendMessageAv()";

		//
		ensureOpen(FNC_NAME);
		if (msgHandler == null) {
			throw new IllegalStateException(FNC_NAME + ": Message handler not set");
		}
		if (zmqSocket == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ socket not initialized");
		}

		//
		msgHandler.writeMsgAvToMq(packet);

		//logDebug(FNC_NAME, "int MQ write " + (packet.codec().isVideo() ? "VID" : "AUD"));
	}

	/**
	 * Close the Message Queue.
	 */
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
