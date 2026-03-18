package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * Base class for message handlers.
 */
public abstract class MqMsgHandlerBase {

	protected ZMQ.@Nullable Socket zmqSocket;

	protected final @NonNull ByteBuffer cacheBufferData;

	private final HashCrc8Helper hashCrc8Helper = new HashCrc8Helper();

	/**
	 * Constructor.
	 * @param zmqSocket ZMQ socket
	 */
	protected MqMsgHandlerBase(ZMQ.@Nullable Socket zmqSocket) {
		this.zmqSocket = zmqSocket;

		this.cacheBufferData = ByteBuffer.allocate(1024);
		this.cacheBufferData.order(ByteOrder.BIG_ENDIAN);
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
	 */
	public abstract void writeMsgAvToMq(@NonNull MqPacketAv packet);

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

}
