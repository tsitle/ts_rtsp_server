package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.util.HexFormat;
import java.util.Map;

public class MqExternalSub extends MqReceiverSubBase {

	private final @NonNull String mqAddrHostAndPort;
	private final @NonNull String mqAddrPath;
	private final @NonNull String mqAddrAuth;

	private final ZMQ.Curve.@NonNull KeyPair mqKeyPair;
	private @Nullable String mqServerPublicKeyZ85 = null;
	private @Nullable String mqServerEndpoint = null;
	private boolean mqEncrypted = true;

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

		//
		this.mqKeyPair = ZMQ.Curve.generateKeyPair();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}

		requestMqInfo();
		internalConnectToMq();

		msgHandler = MqMsgHandlerFactory.createHandler(zmqSocket);

		stateOpened.set(true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void requestMqInfo() throws MqException {
		HttpResponseOpenMq responseOpenMq;
		try {
			String authUser = mqAddrAuth.split(":")[0];
			String authPw = mqAddrAuth.split(":")[1];
			HttpPostJson client = new HttpPostJson(authUser, authPw);

			Map<String, Object> payload = Map.of(
					"clientPubKey", encodeHexString(mqKeyPair.publicKey)
				);

			responseOpenMq = client.postJson(
					"https://" + mqAddrHostAndPort + mqAddrPath,
					payload,
					HttpResponseOpenMq.class
				);
		} catch (java.net.ConnectException e) {
			throw new MqException("Could not connect to Message Queue server");
		} catch (IOException | InterruptedException e) {
			throw new MqException("Could not connect to Message Queue server: " + e.getMessage());
		} catch (Exception e) {
			throw new MqException("Exception caught: " + e.getMessage());
		}

		String mqHostOnly = mqAddrHostAndPort.split(":")[0];
		mqServerEndpoint = "tcp://" + mqHostOnly + ":" + responseOpenMq.mqPort();
		mqServerPublicKeyZ85 = decodeHexString(responseOpenMq.mqServerPubKey());
		mqEncrypted = responseOpenMq.mqEncrypted();
	}

	private void internalConnectToMq() {
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

		//
		if (mqEncrypted) {
			if (mqServerPublicKeyZ85 == null) {
				throw new IllegalStateException("MQ encryption is enabled, but mqServerPublicKey is not set");
			}
			zmqSocket.setCurveServerKey(mqServerPublicKeyZ85.getBytes(ZMQ.CHARSET));

			zmqSocket.setCurvePublicKey(mqKeyPair.publicKey.getBytes(ZMQ.CHARSET));
			zmqSocket.setCurveSecretKey(mqKeyPair.secretKey.getBytes(ZMQ.CHARSET));
		}

		// connect to publisher
		if (mqServerEndpoint == null) {
			throw new IllegalStateException("mqServerEndpoint is not set");
		}
		zmqSocket.connect(mqServerEndpoint);

		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIx = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);
	}

	private @NonNull String decodeHexString(@NonNull String hex) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".decodeHexString()";

		try {
			if (hex.startsWith("0x")) {
				hex = hex.substring(2);
			}
			if (hex.length() % 2 != 0) {
				throw new IllegalArgumentException("Hex string must have even length");
			}
			byte[] bytes = HexFormat.of().parseHex(hex);  // throws IllegalArgumentException
			return new String(bytes, ZMQ.CHARSET);
		} catch (IllegalArgumentException e) {
			throw new MqException(FNC_NAME + ": could not decode hex string: " + e.getMessage());
		}
	}

	private static @NonNull String encodeHexString(@NonNull String plain) {
		byte[] bytes = plain.getBytes(ZMQ.CHARSET);
		return "0x" + HexFormat.of().withUpperCase().formatHex(bytes);
	}

}
