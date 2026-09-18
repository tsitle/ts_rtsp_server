package org.tsitle.lib_mq.client;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_mq.common.MqMsgHandlerFactory;
import org.tsitle.lib_mq.common.httpdata.HttpResponseOpenMq;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_mq.common.MqReceiverSubBase;
import org.tsitle.lib_mq.exceptions.MqException;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;

/**
 * Subscribes to an external Message Queue using authentication and optional encryption.
 */
public final class MqExternalSub extends MqReceiverSubBase {

	private static class MqSettingsExtended {
		boolean haveSettings = false;

		final @NonNull MqElementaryStreamSourceSettings settsBasic;
		@NonNull String serverEndpoint = "";
		@NonNull String serverPublicKeyZ85 = "";
		boolean isEncrypted = true;
		boolean areMsgsSegmented = false;

		MqSettingsExtended(@NonNull MqElementaryStreamSourceSettings mqSettingsBasic) {
			this.settsBasic = mqSettingsBasic.clone();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final boolean DISABLE_SSL_CERT_VALIDATION = false;
	private static final boolean DO_VALIDATE_PAYLOAD = true;
	private static final int SEND_RECV_HWM = 100;

	private final @NonNull String mqSslCertPath;

	private final ZMQ.Curve.@NonNull KeyPair mqKeyPair;
	private final MqSettingsExtended mqSettingsExtended;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param mqSettings Message Queue settings
	 * @param mqSslCertPath Path to the SSL certificate file (can be empty)
	 */
	public MqExternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull MqElementaryStreamSourceSettings mqSettings,
				@NonNull String mqSslCertPath
			) {
		super(logMsgInterface, DO_VALIDATE_PAYLOAD, false);

		//
		this.mqSslCertPath = mqSslCertPath.strip();

		//
		this.mqSettingsExtended = new MqSettingsExtended(mqSettings);
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

		msgHandler = MqMsgHandlerFactory.createHandlerExternalMq(zmqSocket, mqSettingsExtended.areMsgsSegmented);

		stateOpened.set(true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void requestMqInfo() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".requestMqInfo()";

		final String mqHttpUrl = mqSettingsExtended.settsBasic.getInputUri().getUriString().orElse("");
		HttpResponseOpenMq responseOpenMq;
		try {
			final String tmpAuthUser = mqSettingsExtended.settsBasic.getUsername();
			final String tmpAuthPw = mqSettingsExtended.settsBasic.getPassword();
			HttpClientJson client;
			if (DISABLE_SSL_CERT_VALIDATION) {
				client = HttpClientJson.createClientWithCompletelyInsecureSsl(tmpAuthUser, tmpAuthPw);
			} else if (mqSslCertPath.isBlank()) {
				client = HttpClientJson.createClientWithDefaultSsl(tmpAuthUser, tmpAuthPw);
			} else {
				client = HttpClientJson.createClientWithRemoteCertForSsl(tmpAuthUser, tmpAuthPw, Path.of(mqSslCertPath));
			}

			Map<String, Object> payload = Map.of(
					"clientPubKey", encodeHexString(mqKeyPair.publicKey)
				);

			responseOpenMq = client.postJson(mqHttpUrl, payload, HttpResponseOpenMq.class);
		} catch (java.net.ConnectException e) {
			throw new MqException(FNC_NAME + ": Connecting to MQ HTTP server failed");
		} catch (IOException | InterruptedException e) {
			throw new MqException(FNC_NAME + ": Connecting to MQ HTTP server failed (IOE/IE): " + e.getMessage());
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught while connecting to " +
					"MQ HTTP server '" + mqHttpUrl + "': " + e.getMessage());
		}

		final String tmpMqHostOnly = mqSettingsExtended.settsBasic.getHostname();
		mqSettingsExtended.serverEndpoint = "tcp://" + tmpMqHostOnly + ":" + Integer.toUnsignedString(responseOpenMq.mqPort());
		mqSettingsExtended.serverPublicKeyZ85 = decodeHexString(responseOpenMq.mqServerPubKey());
		mqSettingsExtended.isEncrypted = responseOpenMq.mqEncrypted();
		mqSettingsExtended.areMsgsSegmented = responseOpenMq.mqMsgSegmented();
		mqSettingsExtended.haveSettings = true;
	}

	private void internalConnectToMq() {
		final String FNC_NAME = getClass().getSimpleName() + ".internalConnectToMq()";

		if (! mqSettingsExtended.haveSettings) {
			throw new IllegalStateException(FNC_NAME + ": MQ settings have not been requested yet");
		}
		zmqSocket = zmqContext.createSocket(SocketType.SUB);
		zmqSocket.setReceiveTimeOut(100);  // milliseconds
		zmqSocket.setSendTimeOut(100);  // milliseconds
		zmqSocket.setReconnectIVL(1000);
		zmqSocket.setReconnectIVLMax(10000);
		zmqSocket.setRcvHWM(SEND_RECV_HWM);  // important! if too low, a JeroMQ bug causes packet loss
		// adjust the OS's receive buffer size
		zmqSocket.setReceiveBufferSize(2 * 1024 * 1024);
		zmqSocket.setLinger(0);
		// subscribe to all topics
		zmqSocket.subscribe("".getBytes());

		//
		if (mqSettingsExtended.isEncrypted) {
			if (mqSettingsExtended.serverPublicKeyZ85.isBlank()) {
				throw new IllegalStateException(FNC_NAME + ": MQ encryption is enabled, but ServerPublicKey is not set");
			}
			zmqSocket.setCurveServerKey(mqSettingsExtended.serverPublicKeyZ85.getBytes(ZMQ.CHARSET));

			zmqSocket.setCurvePublicKey(mqKeyPair.publicKey.getBytes(ZMQ.CHARSET));
			zmqSocket.setCurveSecretKey(mqKeyPair.secretKey.getBytes(ZMQ.CHARSET));
		}

		// connect to publisher
		if (mqSettingsExtended.serverEndpoint.isBlank()) {
			throw new IllegalStateException(FNC_NAME + ": ServerEndpoint is not set");
		}
		zmqSocket.connect(mqSettingsExtended.serverEndpoint);

		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIxRead = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);
	}

	private @NonNull String decodeHexString(@NonNull String hex) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".decodeHexString()";

		try {
			if (hex.startsWith("0x")) {
				hex = hex.substring(2);
			}
			if (hex.length() % 2 != 0) {
				throw new IllegalArgumentException(FNC_NAME + ": Hex string must have even length");
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
