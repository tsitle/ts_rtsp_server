package org.tsitle.rtsp.mq;

import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.zeromq.ZMQ;

/**
 * Subscriber for internal messages.
 */
public class MqInternalSub extends MqReceiverSubBase {

	private static final boolean DO_VALIDATE_PAYLOAD = false;
	private static final boolean DO_PRINT_DEBUG_STATS = false;

	private final int streamSourceId;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param streamSourceId Stream source identifier
	 */
	public MqInternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				int streamSourceId
			) {
		super(logMsgInterface, DO_VALIDATE_PAYLOAD, DO_PRINT_DEBUG_STATS);

		this.streamSourceId = streamSourceId;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		String chanName = MqChannelBus.buildChannelNameForStreamSourceId(streamSourceId);
		int chanId = MqChannelBus.getChannelId(chanName);
		zmqSocket = MqChannelBus.createSubscriber(chanId, zmqContext);
		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIx = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);

		msgHandler = MqMsgHandlerFactory.createHandlerInternalMq(zmqSocket);

		stateOpened.set(true);
		logDebug(FNC_NAME, "Connected to MQ channel: " + chanName);
	}

}
