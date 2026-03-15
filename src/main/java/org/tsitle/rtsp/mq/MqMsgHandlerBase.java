package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

public abstract class MqMsgHandlerBase {

	protected ZMQ.@Nullable Socket zmqSocket;

	protected final @NonNull ByteBuffer cacheBufferData;

	private final HashCrc8Helper hashCrc8Helper = new HashCrc8Helper();

	protected MqMsgHandlerBase(ZMQ.@Nullable Socket zmqSocket) {
		this.zmqSocket = zmqSocket;

		this.cacheBufferData = ByteBuffer.allocate(1024);
		this.cacheBufferData.order(ByteOrder.BIG_ENDIAN);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public abstract Optional<MqPacketAv> readMsgFromMq(final @NonNull BufferExt payloadDataPtr) throws MqException;

	public abstract void writeMsgToMq(@NonNull MqPacketAv packetAv);

	// -----------------------------------------------------------------------------------------------------------------

	public void validatePacketPayloadCRC(byte expHashSum, final BufferExt buffer) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".validatePacketPayloadCRC()";

		byte isHashSum = hashCrc8Helper.computeChecksum(buffer, 0, buffer.getUsed());
		if (isHashSum != expHashSum) {
			throw new MqException(FNC_NAME + ": Invalid payload CRC8 checksum: is=" +
					String.format("0x%02X", isHashSum) + ", exp=" + String.format("0x%02X", expHashSum));
		}
	}

}
