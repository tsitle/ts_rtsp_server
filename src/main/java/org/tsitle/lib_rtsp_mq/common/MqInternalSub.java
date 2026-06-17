package org.tsitle.lib_rtsp_mq.common;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_rtsp_mq.common.cbtypes.MqChannelBusChannelId;
import org.tsitle.lib_rtsp_mq.common.cbtypes.MqChannelBusChannelName;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_rtsp_mq.exceptions.MqException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
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
		MqChannelBusChannelName chanName = MqChannelBus.buildChannelNameForStreamSourceId(idStreamSource);
		MqChannelBusChannelId chanId = MqChannelBus.getChannelId(chanName);
		zmqSocket = MqChannelBus.createSubscriber(chanId, zmqContext);
		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIxRead = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);

		msgHandler = MqMsgHandlerFactory.createHandlerInternalMq(zmqSocket, null, -1);

		stateOpened.set(true);
		logDebug(FNC_NAME, "Connected to MQ channel: " + chanName);
	}

}
