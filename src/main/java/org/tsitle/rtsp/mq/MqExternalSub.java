package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.mq.httpdata.HttpResponseOpenMq;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;

/**
 * Subscribes to an external Message Queue using authentication and optional encryption.
 */
public class MqExternalSub extends MqReceiverSubBase {

	private static class MqSettings {
		boolean haveSettings = false;

		@NonNull String serverEndpoint = "";
		@NonNull String serverPublicKeyZ85 = "";
		boolean isEncrypted = true;
		boolean areMsgsSegmented = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final boolean DO_VALIDATE_PAYLOAD = true;

	private final @NonNull String mqAddrHostAndPort;
	private final @NonNull String mqAddrPath;
	private final @NonNull String mqAddrAuth;
	private final @NonNull String mqSslCertPath;

	private final ZMQ.Curve.@NonNull KeyPair mqKeyPair;
	private final MqSettings mqSettings = new MqSettings();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param mqAddressHostAndPort Message queue host address (IP/hostname and port)
	 * @param mqAddressPath Message queue path
	 * @param mqAddressAuth Message queue authentication (User:Password)
	 * @param mqSslCertPath Path to the SSL certificate file (can be empty)
	 */
	public MqExternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String mqAddressHostAndPort,
				@NonNull String mqAddressPath,
				@NonNull String mqAddressAuth,
				@NonNull String mqSslCertPath
			) {
		super(logMsgInterface, DO_VALIDATE_PAYLOAD, false);

		//
		this.mqSslCertPath = mqSslCertPath.strip();
		//
		if (mqAddressHostAndPort.isBlank() || mqAddressPath.isBlank() || mqAddressAuth.isBlank()) {
			throw new IllegalArgumentException("MQ host/path/auth must not be empty");
		}
		if (! mqAddressAuth.contains(":")) {
			throw new IllegalArgumentException("MQ auth must contain user and password separated by colon");
		}
		if (! mqAddressHostAndPort.contains(":")) {
			this.mqAddrHostAndPort = mqAddressHostAndPort + ":443";  // default HTTPS port
		} else {
			this.mqAddrHostAndPort = mqAddressHostAndPort;
		}
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

		msgHandler = MqMsgHandlerFactory.createHandlerExternalMq(zmqSocket, mqSettings.areMsgsSegmented);

		stateOpened.set(true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void requestMqInfo() throws MqException {
		final String mqHttpUrl = "https://" + mqAddrHostAndPort + mqAddrPath;
		HttpResponseOpenMq responseOpenMq;
		try {
			final String tmpAuthUser = mqAddrAuth.split(":")[0];
			final String tmpAuthPw = mqAddrAuth.split(":")[1];
			HttpClientJson client;
			if (mqSslCertPath.isBlank()) {
				client = HttpClientJson.createClientWithCompletelyInsecureSsl(tmpAuthUser, tmpAuthPw);
			} else {
				client = HttpClientJson.createClientWithRemoteCertForSsl(tmpAuthUser, tmpAuthPw, Path.of(mqSslCertPath));
			}

			Map<String, Object> payload = Map.of(
					"clientPubKey", encodeHexString(mqKeyPair.publicKey)
				);

			responseOpenMq = client.postJson(mqHttpUrl, payload, HttpResponseOpenMq.class);
		} catch (java.net.ConnectException e) {
			throw new MqException("Could not connect to Message Queue HTTP server '" + mqHttpUrl + "'");
		} catch (IOException | InterruptedException e) {
			throw new MqException("Connecting to Message Queue HTTP server '" + mqHttpUrl + "' failed: " + e.getMessage());
		} catch (Exception e) {
			throw new MqException("Exception caught while connecting to " +
					"Message Queue HTTP server '" + mqHttpUrl + "': " + e.getMessage());
		}

		final String tmpMqHostOnly = mqAddrHostAndPort.split(":")[0];
		mqSettings.serverEndpoint = "tcp://" + tmpMqHostOnly + ":" + responseOpenMq.mqPort();
		mqSettings.serverPublicKeyZ85 = decodeHexString(responseOpenMq.mqServerPubKey());
		mqSettings.isEncrypted = responseOpenMq.mqEncrypted();
		mqSettings.areMsgsSegmented = responseOpenMq.mqMsgSegmented();
		mqSettings.haveSettings = true;
	}

	private void internalConnectToMq() {
		if (! mqSettings.haveSettings) {
			throw new IllegalStateException("MQ settings have not been requested yet");
		}
		zmqSocket = zmqContext.createSocket(SocketType.SUB);
		zmqSocket.setReceiveTimeOut(250);  // milliseconds
		zmqSocket.setSendTimeOut(250);  // milliseconds
		zmqSocket.setReconnectIVL(1000);
		zmqSocket.setReconnectIVLMax(10000);
		zmqSocket.setRcvHWM(20);  // important! if too low, a JeroMQ bug causes packet loss
		// adjust the OS's receive buffer size
		zmqSocket.setReceiveBufferSize(2 * 1024 * 1024);
		zmqSocket.setLinger(0);
		// subscribe to all topics
		zmqSocket.subscribe("".getBytes());

		//
		if (mqSettings.isEncrypted) {
			if (mqSettings.serverPublicKeyZ85.isBlank()) {
				throw new IllegalStateException("MQ encryption is enabled, but ServerPublicKey is not set");
			}
			zmqSocket.setCurveServerKey(mqSettings.serverPublicKeyZ85.getBytes(ZMQ.CHARSET));

			zmqSocket.setCurvePublicKey(mqKeyPair.publicKey.getBytes(ZMQ.CHARSET));
			zmqSocket.setCurveSecretKey(mqKeyPair.secretKey.getBytes(ZMQ.CHARSET));
		}

		// connect to publisher
		if (mqSettings.serverEndpoint.isBlank()) {
			throw new IllegalStateException("ServerEndpoint is not set");
		}
		zmqSocket.connect(mqSettings.serverEndpoint);

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
			byte[] bytes = HexFormat.of().parseHex(hex);  // throws IllegalArgumentException or NumberFormatException
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
