package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.zeromq.ZMQ;

/**
 * Subscriber for internal messages.
 */
public class MqInternalSub extends MqReceiverSubBase {

	private static final boolean DO_VALIDATE_PAYLOAD = false;
	private static final boolean DO_PRINT_DEBUG_STATS = false;

	private final @NonNull RtspProtoIdStreamSource idStreamSource = RtspProtoIdStreamSource.ofEmpty();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param idStreamSource Stream source identifier
	 */
	public MqInternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdStreamSource idStreamSource
			) {
		super(logMsgInterface, DO_VALIDATE_PAYLOAD, DO_PRINT_DEBUG_STATS);

		this.idStreamSource.copyFrom(idStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		String chanName = MqChannelBus.buildChannelNameForStreamSourceId(idStreamSource);
		int chanId = MqChannelBus.getChannelId(chanName);
		zmqSocket = MqChannelBus.createSubscriber(chanId, zmqContext);
		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIxRead = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);

		msgHandler = MqMsgHandlerFactory.createHandlerInternalMq(zmqSocket, null, -1);

		stateOpened.set(true);
		logDebug(FNC_NAME, "Connected to MQ channel: " + chanName);
	}

}
