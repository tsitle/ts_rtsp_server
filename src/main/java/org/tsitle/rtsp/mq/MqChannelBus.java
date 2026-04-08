package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Message Queue channel bus for internal communication.
 */
public class MqChannelBus {

	private final static ConcurrentMap<@NonNull Integer, @NonNull String> mapIdToEndpoint = new ConcurrentHashMap<>();
	private final static ConcurrentMap<@NonNull String, @NonNull Integer> mapNameToId = new ConcurrentHashMap<>();

	/**
	 * Build a channel name for a stream source identifier.
	 * @param streamSourceId Stream source identifier
	 * @return Channel name
	 */
	public static String buildChannelNameForStreamSourceId(int streamSourceId) {
		return String.format("internal#%04d", streamSourceId);
	}

	/**
	 * Register a new channel with the given name.
	 * @param name Channel name
	 * @return Channel ID
	 */
	public synchronized static int registerChannel(@NonNull String name) {
		if (mapNameToId.containsKey(name)) {
			return mapNameToId.get(name);
		}

		final int id = mapIdToEndpoint.size();

		final String endpoint = "inproc://bus/" + id;

		mapIdToEndpoint.put(id, endpoint);
		mapNameToId.put(name, id);

		return id;
	}

	/**
	 * Check if a channel with the given name exists.
	 * @param name Channel name
	 * @return True if the channel exists
	 */
	@SuppressWarnings("unused")
	public synchronized static boolean channelExists(@NonNull String name) {
		return mapNameToId.containsKey(name);
	}

	/**
	 * Get the channel ID for the given channel name.
	 * @param name Channel name
	 * @return Channel ID
	 */
	public synchronized static int getChannelId(@NonNull String name) {
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
	public synchronized static ZMQ.@NonNull Socket createPublisher(int id, ZContext zmqContext) {
		check(id);

		ZMQ.Socket zmqSocket = zmqContext.createSocket(SocketType.PUB);
		zmqSocket.setSndHWM(10);
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
	public synchronized static ZMQ.@NonNull Socket createSubscriber(int id, ZContext zmqContext) {
		check(id);

		ZMQ.Socket zmqSocket = zmqContext.createSocket(SocketType.SUB);
		zmqSocket.setRcvHWM(10);
		zmqSocket.setReceiveTimeOut(50);  // milliseconds
		zmqSocket.setSendTimeOut(50);  // milliseconds
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

	private static void check(int id) {
		if (id >= mapIdToEndpoint.size()) {
			throw new IllegalArgumentException("invalid channel id");
		}
	}

}
