package org.tsitle.lib_rtsp_mq.common;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_rtsp_mq.common.cbtypes.MqChannelBusChannelId;
import org.tsitle.lib_rtsp_mq.common.cbtypes.MqChannelBusChannelName;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Message Queue channel bus for internal communication.
 */
public class MqChannelBus {

	private static final int SEND_RECV_HWM = 10;

	private static final ConcurrentMap<@NonNull MqChannelBusChannelId, @NonNull String> mapIdToEndpoint = new ConcurrentHashMap<>();
	private static final ConcurrentMap<@NonNull MqChannelBusChannelName, @NonNull MqChannelBusChannelId> mapNameToId = new ConcurrentHashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Build a channel name for a stream source identifier.
	 * @param idStreamSource Stream source identifier
	 * @return Channel name
	 */
	public static @NonNull MqChannelBusChannelName buildChannelNameForStreamSourceId(
				@NonNull RtspProtoIdStreamSource idStreamSource
			) {
		if (idStreamSource.isEmpty()) {
			throw new IllegalArgumentException("idStreamSource cannot be empty");
		}
		return MqChannelBusChannelName.of(String.format("internal#%s#", idStreamSource.getIdStr().orElseThrow()));
	}

	/**
	 * Register a new channel with the given name.
	 * @param name Channel name
	 * @return Channel ID
	 */
	public synchronized static @NonNull MqChannelBusChannelId registerChannel(@NonNull MqChannelBusChannelName name) {
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Channel name cannot be empty");
		}
		if (mapNameToId.containsKey(name)) {
			return mapNameToId.get(name);
		}

		final int tmpIdInt = mapIdToEndpoint.size();

		final String endpoint = "inproc://bus/" + Integer.toUnsignedString(tmpIdInt);

		MqChannelBusChannelId tmpIdObj;
		try {
			tmpIdObj = MqChannelBusChannelId.of(tmpIdInt);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
			tmpIdObj = MqChannelBusChannelId.ofEmpty();
		}

		mapIdToEndpoint.put(tmpIdObj, endpoint);
		mapNameToId.put(name, tmpIdObj);

		return tmpIdObj;
	}

	/**
	 * Check if a channel with the given name exists.
	 * @param name Channel name
	 * @return True if the channel exists
	 */
	@SuppressWarnings("unused")
	public synchronized static boolean channelExists(@NonNull MqChannelBusChannelName name) {
		return mapNameToId.containsKey(name);
	}

	/**
	 * Get the channel ID for the given channel name.
	 * @param name Channel name
	 * @return Channel ID
	 */
	public synchronized static @NonNull MqChannelBusChannelId getChannelId(@NonNull MqChannelBusChannelName name) {
		if (! mapNameToId.containsKey(name)) {
			throw new IllegalArgumentException("Channel not found: '" + name + "'");
		}
		return mapNameToId.get(name);
	}

	/**
	 * Create a publisher socket for the given channel ID.
	 * @param id Channel ID
	 * @return Publisher socket
	 */
	public synchronized static ZMQ.@NonNull Socket createPublisher(@NonNull MqChannelBusChannelId id, ZContext zmqContext) {
		check(id);

		ZMQ.Socket zmqSocket = zmqContext.createSocket(SocketType.PUB);
		zmqSocket.setSndHWM(SEND_RECV_HWM);
		// adjust the OS's send buffer size
		zmqSocket.setSendBufferSize(2 * 1024 * 1024);
		zmqSocket.setLinger(0);
		zmqSocket.setReceiveTimeOut(100);  // milliseconds
		zmqSocket.setSendTimeOut(100);  // milliseconds
		// detect dead subscribers
		zmqSocket.setTCPKeepAlive(1);
		zmqSocket.setTCPKeepAliveIdle(60);  // seconds
		zmqSocket.setTCPKeepAliveInterval(60);  // seconds
		zmqSocket.setTCPKeepAliveCount(3);
		// prevents messages being queued for connections that are not yet fully established. Helps to avoid message buildup if a subscriber disappears
		zmqSocket.setImmediate(true);

		final String endpoint = mapIdToEndpoint.get(id);
		zmqSocket.bind(endpoint);
		zmqSocket.send(new byte[0], ZMQ.DONTWAIT);  // send a warm-up message

		return zmqSocket;
	}

	/**
	 * Create a subscriber socket for the given channel ID.
	 * @param id Channel ID
	 * @return Subscriber socket
	 */
	public synchronized static ZMQ.@NonNull Socket createSubscriber(@NonNull MqChannelBusChannelId id, ZContext zmqContext) {
		check(id);

		ZMQ.Socket zmqSocket = zmqContext.createSocket(SocketType.SUB);
		zmqSocket.setRcvHWM(SEND_RECV_HWM);
		zmqSocket.setReceiveTimeOut(100);  // milliseconds
		zmqSocket.setSendTimeOut(100);  // milliseconds
		zmqSocket.setLinger(0);
		// adjust the OS's receive buffer size
		zmqSocket.setReceiveBufferSize(2 * 1024 * 1024);
		// subscribe to all topics
		zmqSocket.subscribe("".getBytes());

		final String endpoint = mapIdToEndpoint.get(id);
		zmqSocket.connect(endpoint);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}

		return zmqSocket;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void check(@NonNull MqChannelBusChannelId id) {
		if (! mapIdToEndpoint.containsKey(id)) {
			throw new IllegalArgumentException("invalid channel id");
		}
	}

}
