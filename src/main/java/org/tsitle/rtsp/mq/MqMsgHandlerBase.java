package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.zeromq.ZMQ;

import java.util.Optional;

/**
 * Base class for message handlers.
 */
public abstract class MqMsgHandlerBase {

	protected static final byte[] PKT_HEADER_MARKER_BA = new byte[] {0x01, 0x02, 0x03, 0x04};
	protected static final int PKT_HEADER_MARKER_LEN = PKT_HEADER_MARKER_BA.length;

	private static final int TIMEOUT_WAIT_FOR_SOCKET_WRITABLE_MS = 1000;

	protected ZMQ.@Nullable Socket zmqSocket;
	protected final ZMQ.@Nullable Poller zmqPollerObj;
	protected final int zmqPollerIxWrite;

	private final HashCrc8Helper hashCrc8Helper = new HashCrc8Helper();

	/**
	 * Constructor.
	 * @param zmqSocket ZMQ socket
	 * @param zmqPollerObj ZMQ poller object
	 * @param zmqPollerIxWrite ZMQ poller index for write events
	 */
	protected MqMsgHandlerBase(
				ZMQ.@Nullable Socket zmqSocket,
				ZMQ.@Nullable Poller zmqPollerObj,
				int zmqPollerIxWrite
			) {
		this.zmqSocket = zmqSocket;
		this.zmqPollerObj = zmqPollerObj;
		this.zmqPollerIxWrite = zmqPollerIxWrite;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receive a message containing audio/video data from the Message Queue.
	 * @param payloadDataPtr Pointer to the payload data buffer
	 * @return Received message or empty if no message was received
	 * @throws MqException If an error has occurred
	 */
	public abstract Optional<MqPacketAv> readMsgAvFromMq(final @NonNull BufferExt payloadDataPtr) throws MqException;

	/**
	 * Send a message containing audio/video data to the Message Queue.
	 * @param packet A/V packet
	 * @throws MqException If an error has occurred
	 */
	public abstract void writeMsgAvToMq(@NonNull MqPacketAv packet) throws MqException;

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Validate the CRC8 checksum of the payload data.
	 * @param expHashSum Expected CRC8 checksum
	 * @param buffer Data buffer
	 * @throws MqException If the CRC8 checksum is invalid
	 */
	public void validateCRC8(byte expHashSum, final BufferExt buffer) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateCRC8()";

		byte isHashSum = hashCrc8Helper.computeChecksum(buffer, 0, buffer.getUsed());
		if (isHashSum != expHashSum) {
			throw new MqException(FNC_NAME + ": Invalid payload CRC8 checksum: is=" +
					String.format("0x%02X", isHashSum) + ", exp=" + String.format("0x%02X", expHashSum));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected boolean isJeroMqInternalMsg(byte[] buffer, int length) {
		return (length > 7 && buffer[0] == 7 && buffer[1] == 'M' &&
				buffer[2] == 'E' && buffer[3] == 'S');
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	protected boolean hasValidPacketHeaderMarker(byte[] buffer, int length) {
		return (length >= PKT_HEADER_MARKER_LEN &&
				buffer[0] == PKT_HEADER_MARKER_BA[0] && buffer[1] == PKT_HEADER_MARKER_BA[1] &&
				buffer[2] == PKT_HEADER_MARKER_BA[2] && buffer[3] == PKT_HEADER_MARKER_BA[3]);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	protected boolean waitForSocketReadyToWrite(@NonNull String fncName) throws MqException {
		int timeoutCnt = 0;
		while (! Thread.currentThread().isInterrupted() && zmqPollerObj != null) {
			if (zmqPollerObj.poll(5) > 0) {
				// check that the zmqPollerObj hasn't been deleted since poll() was called
				//noinspection ConstantValue
				if (zmqPollerObj != null && zmqPollerObj.pollout(zmqPollerIxWrite)) {
					return true;
				}
			}
			if (++timeoutCnt > TIMEOUT_WAIT_FOR_SOCKET_WRITABLE_MS / 5) {
				throw new MqException(fncName + ": Timeout waiting for socket (wr)");
			}
		}
		return false;
	}

}
