package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MqMsgHandlerSegmented extends MqMsgHandlerBase {

	public MqMsgHandlerSegmented(ZMQ.@Nullable Socket zmqSocket) {
		super(zmqSocket);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<MqPacketAv> readMsgFromMq(final @NonNull BufferExt payloadDataPtr) throws MqException {
		if (zmqSocket == null) {
			return Optional.empty();
		}

		final List<byte[]> frames = new ArrayList<>();

		byte[] frame = zmqSocket.recv(0);
		if (frame == null) {
			return Optional.empty();
		}
		frames.add(frame);
		while (zmqSocket.hasReceiveMore()) {
			frames.add(zmqSocket.recv(0));
		}

		return Optional.of(
				decodePacketAv(frames, payloadDataPtr)
			);
	}

	public void writeMsgToMq(@NonNull MqPacketAv packetAv) {
		if (zmqSocket == null) {
			throw new IllegalStateException("MQ socket not initialized");
		}

		// write the header to the Message Queue
		writeFieldToMqBool(packetAv.codec().isVideo(), ZMQ.SNDMORE);
		writeFieldToMqStr(packetAv.codec().getCodecName(), ZMQ.SNDMORE);
		if (packetAv.codec().isVideo()) {
			writeFieldToMqBool(packetAv.isCodecGuessed(), ZMQ.SNDMORE);
		}
		writeFieldToMqUint64(packetAv.mdTimestamp(), ZMQ.SNDMORE);
		writeFieldToMqUint32(packetAv.mdCounter(), ZMQ.SNDMORE);
		if (packetAv.codec().isVideo()) {
			writeFieldToMqBool(packetAv.mdVideoIsKeyframe(), ZMQ.SNDMORE);
			writeFieldToMqUint32(packetAv.mdVideoResoWidth(), ZMQ.SNDMORE);
			writeFieldToMqUint32(packetAv.mdVideoResoHeight(), ZMQ.SNDMORE);
			writeFieldToMqUint32(packetAv.mdVideoFps(), ZMQ.SNDMORE);
			writeFieldToMqUint32(packetAv.mdVideoBitrate(), ZMQ.SNDMORE);
		}
		writeFieldToMqUint08(packetAv.mdPayloadCRC8(), ZMQ.SNDMORE);
		writeFieldToMqUint32(packetAv.payloadDataPtr().getUsed(), ZMQ.SNDMORE);

		// write the payload data to the Message Queue
		writeFieldToMqBinData(packetAv.payloadDataPtr(), 0);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decode a packet from the given buffers - including the payload data.
	 * @param frames Frame buffers
	 * @param payloadDataPtr Output payload data buffer
	 * @return Decoded packet and payload data
	 * @throws MqException If a problem occurred while parsing the input buffers
	 */
	private @NonNull MqPacketAv decodePacketAv(
				final @NonNull List<byte[]> frames,
				final @NonNull BufferExt payloadDataPtr
			) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".decodePacketAv()";

		int frameIx = 0;

		final boolean tmpIsVideo = decodeFieldBool(FNC_NAME, frames, frameIx++);

		final MqPacketCodec tmpCodecEn;
		final String tmpCodecStr = decodeFieldString(FNC_NAME, frames, frameIx++);
		try {
			tmpCodecEn = MqPacketCodec.of(tmpCodecStr);
		} catch (IllegalArgumentException e) {
			throw new MqException(FNC_NAME + ": Invalid codec name: '" + tmpCodecStr + "'");
		}
		if (tmpIsVideo != tmpCodecEn.isVideo()) {
			throw new MqException(FNC_NAME + ": Invalid codec in " + (tmpIsVideo ? "VID" : "AUD") +
					" packet: '" + tmpCodecStr + "'");
		}

		final boolean tmpIsCodecGuessed = (tmpIsVideo && decodeFieldBool(FNC_NAME, frames, frameIx++));
		final long tmpMdTimestamp = decodeFieldUint64(FNC_NAME, frames, frameIx++);
		final int tmpMdCounter = decodeFieldUint32(FNC_NAME, frames, frameIx++);
		final boolean tmpMdVideoIsKeyframe = (tmpIsVideo && decodeFieldBool(FNC_NAME, frames, frameIx++));
		final int tmpMdVideoResoWidth = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);
		final int tmpMdVideoResoHeight = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);
		final int tmpMdVideoFps = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);
		final int tmpMdVideoBitrate = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);
		final byte tmpMdPayloadCRC8 = decodeFieldUint08(FNC_NAME, frames, frameIx++);
		final int tmpBinDataLen = decodeFieldUint32(FNC_NAME, frames, frameIx++);

		decodeFieldBinData(FNC_NAME, frames, frameIx, tmpBinDataLen, payloadDataPtr);

		return new MqPacketAv(
				tmpCodecEn,
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
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String decodeFieldString(
				@NonNull String fncName,
				final @NonNull List<byte[]> frames,
				final int frameIx
			) throws MqException {
		validateFrameSize(fncName, frames, frameIx, 0);
		return new String(frames.get(frameIx), ZMQ.CHARSET);
	}

	private byte decodeFieldUint08(@NonNull String fncName, final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(fncName, frames, frameIx, 1);
		return frames.get(frameIx)[0];
	}

	private int decodeFieldUint32(@NonNull String fncName, final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(fncName, frames, frameIx, 4);
		return ByteBuffer
				.wrap(frames.get(frameIx))
				.order(ByteOrder.BIG_ENDIAN)
				.getInt();
	}

	private long decodeFieldUint64(@NonNull String fncName, final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(fncName, frames, frameIx, 8);
		return ByteBuffer
				.wrap(frames.get(frameIx))
				.order(ByteOrder.BIG_ENDIAN)
				.getLong();
	}

	private boolean decodeFieldBool(@NonNull String fncName, final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(fncName, frames, frameIx, 1);
		return (frames.get(frameIx)[0] != 0);
	}

	private void decodeFieldBinData(
				@NonNull String fncName,
				final @NonNull List<byte[]> frames,
				final int frameIx,
				final int binDataLen,
				final @NonNull BufferExt payloadDataPtr
			) throws MqException {
		validateFrameSize(fncName, frames, frameIx, binDataLen);
		payloadDataPtr.copyOf(frames.get(frameIx));
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void validateFrameIx(@NonNull String fncName, int frameIx, int framesSize) throws MqException {
		if (frameIx >= framesSize) {
			throw new MqException(fncName + ": Invalid frame index: " + frameIx + " >= " + framesSize);
		}
	}

	private void validateFrameSize(
				@NonNull String fncName,
				final @NonNull List<byte[]> frames,
				final int frameIx,
				int requSize
			) throws MqException {
		validateFrameIx(fncName, frameIx, frames.size());
		if (frames.get(frameIx).length < requSize) {
			throw new MqException(fncName + ": Invalid frame size: " + frames.get(frameIx).length + " < " + requSize);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void writeFieldToMqStr(String value, @SuppressWarnings("SameParameterValue") int flags) {
		//noinspection DataFlowIssue
		zmqSocket.send(value, flags);
	}

	private void writeFieldToMqUint08(byte value, @SuppressWarnings("SameParameterValue") int flags) {
		//noinspection DataFlowIssue
		zmqSocket.send(new byte[]{value}, flags);
	}

	private void writeFieldToMqUint32(int value, @SuppressWarnings("SameParameterValue") int flags) {
		ByteBuffer tmpBuf = ByteBuffer
				.allocate(4)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(value);
		//noinspection DataFlowIssue
		zmqSocket.send(tmpBuf.array(), flags);
	}

	private void writeFieldToMqUint64(long value, @SuppressWarnings("SameParameterValue") int flags) {
		ByteBuffer tmpBuf = ByteBuffer
				.allocate(8)
				.order(ByteOrder.BIG_ENDIAN)
				.putLong(value);
		//noinspection DataFlowIssue
		zmqSocket.send(tmpBuf.array(), flags);
	}

	private void writeFieldToMqBool(boolean value, @SuppressWarnings("SameParameterValue") int flags) {
		//noinspection DataFlowIssue
		zmqSocket.send(new byte[]{value ? (byte)1 : (byte)0}, flags);
	}

	private void writeFieldToMqBinData(final @NonNull BufferExt value, @SuppressWarnings("SameParameterValue") int flags) {
		//noinspection DataFlowIssue
		zmqSocket.send(value.getBufPtr(), 0, value.getUsed(), flags);
	}

}
