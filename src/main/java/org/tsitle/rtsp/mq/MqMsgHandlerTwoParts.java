package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.mq.mqdata.MqPacketCodec;
import org.zeromq.ZMQ;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * Message Queue handler for 'two parts' messages.
 */
public final class MqMsgHandlerTwoParts extends MqMsgHandlerBase {

	private static final int FIRST_PART_BUFFER_SIZE = 1024;

	private int payloadDataSize = 0;
	private final @NonNull BufferExt cacheBufferDataR = new BufferExt();
	private final @NonNull BufferExt cacheBufferDataS = new BufferExt();

	/**
	 * Constructor.
	 * @param zmqSocket ZMQ socket
	 */
	public MqMsgHandlerTwoParts(ZMQ.@Nullable Socket zmqSocket) {
		super(zmqSocket);

		cacheBufferDataR.increaseSize(FIRST_PART_BUFFER_SIZE);
		cacheBufferDataS.increaseSize(FIRST_PART_BUFFER_SIZE);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<MqPacketAv> readMsgAvFromMq(final @NonNull BufferExt payloadDataPtr) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".readMsgAvFromMq()";

		if (zmqSocket == null) {
			return Optional.empty();
		}

		// receive the first part of the message
		try {
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			int tmpRecvRes = zmqSocket.recv(cacheBufferDataR.getBaPtr(), 0, FIRST_PART_BUFFER_SIZE, 0);
			if (tmpRecvRes == -1) {
				throw new MqException(FNC_NAME + ": Socket closed or interrupted");
			}
			if (tmpRecvRes == FIRST_PART_BUFFER_SIZE) {
				// truncated message - probably because of a previous message that was spurious ZeroMQ internal data
				return Optional.empty();
			}
			if (tmpRecvRes == 0) {
				return Optional.empty();
			}
			cacheBufferDataR.setUsed(tmpRecvRes);
			// check for spurious ZeroMQ internal data
			/*
			 * 0x07 4D 45 53 53 41 47 45 000000000000
			 *      M  E  S  S  A  G  E  000000000000
			 */
			if (isJeroMqInternalMsg(cacheBufferDataR.getBaPtr(), cacheBufferDataR.getUsed())) {
				// receive any remaining messages to drain the Message Queue
				//System.out.println("garbage, pkt #" + rcvdPacketCount + " -- " + zmqSocket.getLastEndpoint());
				int maxPkts = 6;
				while (zmqSocket.hasReceiveMore() && maxPkts-- > 0) {
					zmqSocket.recv(ZMQ.DONTWAIT);
					//System.out.println("-- garbage remainder -- " + zmqSocket.getLastEndpoint());
				}
				return Optional.empty();
			}
		} catch (MqException e) {
			throw e;
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}

		// decode the first part of the message
		payloadDataSize = 0;
		final MqPacketAv packet;
		try {
			packet = decodePacketAv(payloadDataPtr);
		} catch (MqException ignored) {
			return Optional.empty();
		}

		// receive the payload data
		try {
			payloadDataPtr.clear();
			if (! zmqSocket.hasReceiveMore()) {
				throw new MqException(FNC_NAME + ": Missing payload data from socket");
			}
			payloadDataPtr.increaseSize(payloadDataSize);  // prepare buffer size
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			int tmpRecvRes = zmqSocket.recv(payloadDataPtr.getBaPtr(), 0, payloadDataSize, 0);
			if (tmpRecvRes != payloadDataSize) {
				throw new MqException(FNC_NAME + ": Received too few data from socket");
			}
			payloadDataPtr.setUsed(tmpRecvRes);
		} catch (MqException e) {
			throw e;
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}

		// receive any remaining messages to drain the Message Queue
		while (zmqSocket.hasReceiveMore()) {
			zmqSocket.recv(0);
		}

		return Optional.of(packet);
	}

	public void writeMsgAvToMq(@NonNull MqPacketAv packet) {
		if (zmqSocket == null) {
			throw new IllegalStateException("MQ socket not initialized");
		}

		ByteBuffer tempBb = ByteBuffer
				.wrap(cacheBufferDataS.getBaPtr(), 0, FIRST_PART_BUFFER_SIZE)
				.order(ByteOrder.BIG_ENDIAN);

		tempBb.put(packet.codec().isVideo() ? (byte)1 : (byte)0);
		writeString127ToMqBuf(packet.codec().getCodecName(), tempBb);
		if (packet.codec().isVideo()) {
			tempBb.put(packet.isCodecGuessed() ? (byte)1 : (byte)0);
		}
		tempBb.putLong(packet.mdTimestamp());
		tempBb.putInt(packet.mdCounter());
		if (packet.codec().isVideo()) {
			tempBb.put(packet.mdVideoIsKeyframe() ? (byte)1 : (byte)0);
			tempBb.putInt(packet.mdVideoResoWidth());
			tempBb.putInt(packet.mdVideoResoHeight());
			writeString127ToMqBuf(
					String.format("%.2f", packet.mdVideoFps()).replace(',', '.'),
					tempBb
				);
			tempBb.putInt(packet.mdVideoBitrate());
		} else {
			tempBb.putInt(packet.mdAudioSamplerate());
			tempBb.put(packet.mdAudioChannelCount());
		}
		tempBb.put(packet.mdPayloadCRC8());
		tempBb.putInt(packet.payloadDataPtr().getUsed());

		tempBb.flip();

		// write the header to the Message Queue
		zmqSocket.send(cacheBufferDataS.getBaPtr(), 0, tempBb.limit(), ZMQ.SNDMORE);
		// write the payload data to the Message Queue
		zmqSocket.send(packet.payloadDataPtr().getBaPtr(), 0, packet.payloadDataPtr().getUsed(), 0);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decode a packet - but without the payload data.
	 * @param payloadDataPtr Pointer to payload data buffer (but the buffer won't be written to)
	 * @return Decoded packet and payload data size
	 * @throws MqException If the input buffer is too small
	 */
	private @NonNull MqPacketAv decodePacketAv(final @NonNull BufferExt payloadDataPtr) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".decodePacketAv()";

		ByteBuffer tempBb = ByteBuffer
				.wrap(cacheBufferDataR.getBaPtr(), 0, cacheBufferDataR.getUsed())
				.order(ByteOrder.BIG_ENDIAN);

		try {
			boolean tmpIsVideo = (tempBb.get() != 0);
			// read codec
			String tmpCodecStr = readString127FromMqBuf(FNC_NAME, tempBb);
			MqPacketCodec tmpCodecEn;
			try {
				tmpCodecEn = MqPacketCodec.of(tmpCodecStr);
			} catch (IllegalArgumentException e) {
				throw new MqException(FNC_NAME + ": Invalid codec name: '" + tmpCodecStr + "'");
			}
			if (tmpIsVideo != tmpCodecEn.isVideo()) {
				throw new MqException(FNC_NAME + ": Invalid codec in " + (tmpIsVideo ? "VID" : "AUD") +
						" packet: '" + tmpCodecStr + "'");
			}
			//
			boolean tmpIsCodecGuessed = (tmpIsVideo && tempBb.get() != 0);
			long tmpMdTimestamp = tempBb.getLong();  // UI64
			int tmpMdCounter = tempBb.getInt();  // UI32
			boolean tmpMdVideoIsKeyframe = (tmpIsVideo && tempBb.get() != 0);
			int tmpMdVideoResoWidth = (tmpIsVideo ? tempBb.getInt() : 0);  // UI32
			int tmpMdVideoResoHeight = (tmpIsVideo ? tempBb.getInt() : 0);  // UI32
			double tmpMdVideoFpsDbl;
			if (tmpIsVideo) {
				String tmpMdVideoFpsStr = readString127FromMqBuf(FNC_NAME, tempBb);
				try {
					tmpMdVideoFpsDbl = Double.parseDouble(tmpMdVideoFpsStr);
				} catch (NumberFormatException e) {
					throw new MqException(FNC_NAME + ": Invalid video FPS format in packet: '" + tmpMdVideoFpsStr + "'");
				}
			} else {
				tmpMdVideoFpsDbl = 0.0;
			}
			int tmpMdVideoBitrate = (tmpIsVideo ? tempBb.getInt() : 0);  // UI32
			int tmpMdAudioSamplerate = (! tmpIsVideo ? tempBb.getInt() : 0);  // UI32
			byte tmpMdAudioChannelCount = (! tmpIsVideo ? tempBb.get() : 0);  // UI08
			byte tmpMdPayloadCRC8 = tempBb.get();  // UI08
			payloadDataSize = tempBb.getInt();  // UI32

			return new MqPacketAv(
					tmpCodecEn,
					tmpIsCodecGuessed,
					tmpMdTimestamp,
					tmpMdCounter,
					tmpMdVideoIsKeyframe,
					tmpMdVideoResoWidth,
					tmpMdVideoResoHeight,
					tmpMdVideoFpsDbl,
					tmpMdVideoBitrate,
					tmpMdAudioSamplerate,
					tmpMdAudioChannelCount,
					tmpMdPayloadCRC8,
					payloadDataPtr
				);
		} catch (BufferUnderflowException e) {
			throw new MqException(FNC_NAME + ": received too few bytes (have " + cacheBufferDataR.getUsed() + ")");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String readString127FromMqBuf(@NonNull String fncName, @NonNull ByteBuffer inpBb) throws MqException {
		byte tmpStrLen = inpBb.get();  // SI08
		if (tmpStrLen < 0) {
			throw new MqException(fncName + ": Invalid string length (is < 0)");
		}
		if (tmpStrLen == 0) {
			return "";
		}
		if (tmpStrLen > inpBb.remaining()) {
			throw new MqException(fncName + ": Invalid string length (is=" + tmpStrLen + ", max=" + inpBb.remaining() + ")");
		}
		byte[] tmpStrBytes = new byte[tmpStrLen];
		inpBb.get(tmpStrBytes);
		return new String(tmpStrBytes, ZMQ.CHARSET);
	}

	private void writeString127ToMqBuf(@NonNull String str, @NonNull ByteBuffer outpBb) {
		if (str.length() > Byte.MAX_VALUE) {
			throw new IllegalArgumentException("String too long");
		}
		byte[] tmpStrBytes = str.getBytes(ZMQ.CHARSET);
		outpBb.put((byte)tmpStrBytes.length);
		outpBb.put(tmpStrBytes);
	}

}
