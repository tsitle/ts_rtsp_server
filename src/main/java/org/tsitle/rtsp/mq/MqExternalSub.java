package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class MqExternalSub extends MqReceiverSubBase {

	private final @NonNull String mqAddrHostAndPort;
	private final @NonNull String mqAddrPath;
	private final @NonNull String mqAddrAuth;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param mqAddressHostAndPort Message queue host address (IP/hostname and port)
	 * @param mqAddressPath Message queue path
	 * @param mqAddressAuth Message queue authentication (User:Password)
	 */
	public MqExternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String mqAddressHostAndPort,
				@NonNull String mqAddressPath,
				@NonNull String mqAddressAuth
			) {
		super(logMsgInterface, true, false);

		this.mqAddrHostAndPort = mqAddressHostAndPort;
		this.mqAddrPath = mqAddressPath;
		this.mqAddrAuth = mqAddressAuth;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		/*
		 * - send HTTP GET request to the URL in this.inputUriStr, using Basic Auth with the credentials in this.inputUriAuth
		 * - parse the response and store the Message Queues address in this.mqAddress
		 * - connect to the MQ server at this.mqAddress
		 */
		String endpoint = "tcp://localhost:" + (mqAddrPath.contains("r_video") ? "7778" : "7779");  // @TODO
		internalConnectToMq(endpoint);

		msgHandler = MqMsgHandlerFactory.createHandler(zmqSocket);

		stateOpened.set(true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalConnectToMq(String endpoint) {
		zmqSocket = zmqContext.createSocket(SocketType.SUB);
		zmqSocket.setReceiveTimeOut(10);
		zmqSocket.setReconnectIVL(1000);
		zmqSocket.setReconnectIVLMax(10000);
		zmqSocket.setRcvHWM(5);
		// adjust the OS's receive buffer size
		zmqSocket.setReceiveBufferSize(2 * 1024 * 1024);
		zmqSocket.setLinger(0);
		// subscribe to all topics
		zmqSocket.subscribe("".getBytes());

		// connect to publisher
		zmqSocket.connect(endpoint);

		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIx = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);
	}

}
