package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.zeromq.ZMQ;

import java.nio.BufferUnderflowException;
import java.util.Optional;

public final class MqMsgHandlerTwoParts extends MqMsgHandlerBase {

	private int payloadDataSize = 0;

	public MqMsgHandlerTwoParts(ZMQ.@Nullable Socket zmqSocket) {
		super(zmqSocket);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<MqPacketAv> readMsgFromMq(final @NonNull BufferExt payloadDataPtr) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".readMsgFromMq()";

		if (zmqSocket == null) {
			return Optional.empty();
		}

		// receive the first part of the message
		try {
			cacheBufferData.clear();
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			int tmpRecvRes = zmqSocket.recvByteBuffer(cacheBufferData, 0);
			if (tmpRecvRes == -1) {
				throw new MqException(FNC_NAME + ": Socket closed or interrupted");
			}
			if (tmpRecvRes == 0) {
				return Optional.empty();
			}
			cacheBufferData.flip();
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}

		// decode the first part of the message
		payloadDataSize = 0;
		final MqPacketAv packetAv = decodePacketAv(payloadDataPtr);

		// receive the payload data
		try {
			payloadDataPtr.clear();
			if (! zmqSocket.hasReceiveMore()) {
				throw new MqException(FNC_NAME + ": Missing payload data from socket");
			}
			payloadDataPtr.increaseSize(payloadDataSize);  // prepare buffer size
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			int tmpRecvRes = zmqSocket.recv(payloadDataPtr.getBufPtr(), 0, payloadDataSize, 0);
			if (tmpRecvRes != payloadDataSize) {
				throw new MqException(FNC_NAME + ": Received too few data from socket");
			}
			payloadDataPtr.setUsed(tmpRecvRes);
		} catch (MqException e) {
			throw e;
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}

		return Optional.of(packetAv);
	}

	public void writeMsgToMq(@NonNull MqPacketAv packetAv) {
		cacheBufferData.clear();

		if (zmqSocket == null) {
			throw new IllegalStateException("MQ socket not initialized");
		}

		byte[] tmpStrBytes = packetAv.codec().getCodecName().getBytes(ZMQ.CHARSET);
		cacheBufferData.putInt(tmpStrBytes.length);
		cacheBufferData.put(tmpStrBytes);
		if (packetAv.codec().isVideo()) {
			cacheBufferData.put(packetAv.isCodecGuessed() ? (byte)1 : (byte)0);
		}
		cacheBufferData.putLong(packetAv.mdTimestamp());
		cacheBufferData.putInt(packetAv.mdCounter());
		if (packetAv.codec().isVideo()) {
			cacheBufferData.put(packetAv.mdVideoIsKeyframe() ? (byte)1 : (byte)0);
			cacheBufferData.putInt(packetAv.mdVideoResoWidth());
			cacheBufferData.putInt(packetAv.mdVideoResoHeight());
			cacheBufferData.putInt(packetAv.mdVideoFps());
			cacheBufferData.putInt(packetAv.mdVideoBitrate());
		}
		cacheBufferData.put(packetAv.mdPayloadCRC8());
		cacheBufferData.putInt(packetAv.payloadDataPtr().getUsed());

		cacheBufferData.flip();

		// write the header to the Message Queue
		zmqSocket.send(cacheBufferData.array(), 0, cacheBufferData.limit(), ZMQ.SNDMORE);
		// write the payload data to the Message Queue
		zmqSocket.send(packetAv.payloadDataPtr().getBufPtr(), 0, packetAv.payloadDataPtr().getUsed(), 0);
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

		try {
			// read codec
			int tmpStrLen = cacheBufferData.getInt();
			byte[] tmpStrBytes = new byte[tmpStrLen];
			cacheBufferData.get(tmpStrBytes);
			String tmpCodecStr = new String(tmpStrBytes, ZMQ.CHARSET);
			MqPacketCodec codec = MqPacketCodec.of(tmpCodecStr);
			//
			boolean tmpIsCodecGuessed = (codec.isVideo() && cacheBufferData.get() != 0);
			long tmpMdTimestamp = cacheBufferData.getLong();
			int tmpMdCounter = cacheBufferData.getInt();
			boolean tmpMdVideoIsKeyframe = (codec.isVideo() && cacheBufferData.get() != 0);
			int tmpMdVideoResoWidth = (codec.isVideo() ? cacheBufferData.getInt() : 0);
			int tmpMdVideoResoHeight = (codec.isVideo() ? cacheBufferData.getInt() : 0);
			int tmpMdVideoFps = (codec.isVideo() ? cacheBufferData.getInt() : 0);
			int tmpMdVideoBitrate = (codec.isVideo() ? cacheBufferData.getInt() : 0);
			byte tmpMdPayloadCRC8 = cacheBufferData.get();
			payloadDataSize = cacheBufferData.getInt();

			return new MqPacketAv(
					codec,
					tmpIsCodecGuessed,
					tmpMdTimestamp,
					tmpMdCounter,
					tmpMdVideoIsKeyframe,
					tmpMdVideoResoWidth,
					tmpMdVideoResoHeight,
					tmpMdVideoFps,
					tmpMdVideoBitrate,
					tmpMdPayloadCRC8,
					payloadDataPtr
				);
		} catch (BufferUnderflowException e) {
			throw new MqException(FNC_NAME + ": received too few bytes");
		}
	}

}
