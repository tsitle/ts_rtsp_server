package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.mq.mqdata.MqPacketCodec;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Message Queue handler for 'segmented' messages.
 */
public final class MqMsgHandlerSegmented extends MqMsgHandlerBase {

	private final BufferExt cacheHeaderMarkerRd = new BufferExt();
	private final BufferExt cacheHeaderMarkerWr = new BufferExt(PKT_HEADER_MARKER_BA);

	/**
	 * Constructor.
	 * @param zmqSocket ZMQ socket
	 * @param zmqPollerObj ZMQ poller object
	 * @param zmqPollerIxWrite ZMQ poller index for write events
	 */
	public MqMsgHandlerSegmented(
				ZMQ.@Nullable Socket zmqSocket,
				ZMQ.@Nullable Poller zmqPollerObj,
				int zmqPollerIxWrite
			) {
		super(zmqSocket, zmqPollerObj, zmqPollerIxWrite);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<MqPacketAv> readMsgAvFromMq(final @NonNull BufferExt payloadDataPtr) {
		if (zmqSocket == null) {
			return Optional.empty();
		}

		final List<byte[]> frames = new ArrayList<>();

		// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
		byte[] frameBa;
		boolean isGarbage;
		do {
			frameBa = zmqSocket.recv(0);
			if (frameBa == null) {
				return Optional.empty();
			}
			isGarbage = isJeroMqInternalMsg(frameBa, frameBa.length);
			/*if (! isGarbage) {
				break;
			}
			System.out.println("garbage -- " + zmqSocket.getLastEndpoint());*/
		} while (isGarbage);
		frames.add(frameBa);
		while (zmqSocket.hasReceiveMore()) {
			frames.add(zmqSocket.recv(0));
		}
		if (frames.size() < 4 || frames.getFirst().length < PKT_HEADER_MARKER_LEN) {
			// truncated message
			/*BufferExt tmpBe = new BufferExt(frames.getFirst());
			System.out.println("missing msg frames -- " + zmqSocket.getLastEndpoint() + " -- " + tmpBe.toHexString(true));*/
			return Optional.empty();
		}

		try {
			return Optional.of(
					decodePacketAv(frames, payloadDataPtr)
				);
		} catch (MqException e) {
			return Optional.empty();
		}
	}

	public void writeMsgAvToMq(@NonNull MqPacketAv packet) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".writeMsgAvToMq()";

		if (zmqSocket == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ socket not initialized");
		}

		// write the header to the Message Queue
		/// Header Marker
		writeFieldToMqBinData(FNC_NAME, cacheHeaderMarkerWr, ZMQ.SNDMORE);
		///
		writeFieldToMqBool(FNC_NAME, packet.codec().isVideo(), ZMQ.SNDMORE);
		writeFieldToMqString127(FNC_NAME, packet.codec().getCodecName(), ZMQ.SNDMORE);
		if (packet.codec().isVideo()) {
			writeFieldToMqBool(FNC_NAME, packet.isCodecGuessed(), ZMQ.SNDMORE);
		}
		writeFieldToMqUint64(FNC_NAME, packet.mdTimestamp(), ZMQ.SNDMORE);
		writeFieldToMqUint32(FNC_NAME, packet.mdCounter(), ZMQ.SNDMORE);
		if (packet.codec().isVideo()) {
			writeFieldToMqBool(FNC_NAME, packet.mdVideoIsKeyframe(), ZMQ.SNDMORE);
			writeFieldToMqUint32(FNC_NAME, packet.mdVideoResoWidth(), ZMQ.SNDMORE);
			writeFieldToMqUint32(FNC_NAME, packet.mdVideoResoHeight(), ZMQ.SNDMORE);
			writeFieldToMqString127(
					FNC_NAME,
					String.format("%.2f", packet.mdVideoFps()).replace(',', '.'),
					ZMQ.SNDMORE
				);
			writeFieldToMqUint32(FNC_NAME, packet.mdVideoBitrate(), ZMQ.SNDMORE);
		} else {
			writeFieldToMqUint32(FNC_NAME, packet.mdAudioSamplerate(), ZMQ.SNDMORE);
			writeFieldToMqUint08(FNC_NAME, packet.mdAudioChannelCount(), ZMQ.SNDMORE);
		}
		writeFieldToMqUint08(FNC_NAME, packet.mdPayloadCRC8(), ZMQ.SNDMORE);
		writeFieldToMqUint32(FNC_NAME, packet.payloadDataPtr().getUsed(), ZMQ.SNDMORE);

		// write the payload data to the Message Queue
		writeFieldToMqBinData(FNC_NAME, packet.payloadDataPtr(), 0);
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

		// Header Marker
		decodeFieldBinData(FNC_NAME, frames, frameIx++, PKT_HEADER_MARKER_LEN, cacheHeaderMarkerRd);
		if (! hasValidPacketHeaderMarker(cacheHeaderMarkerRd.getBaPtr(), cacheHeaderMarkerRd.getUsed())) {
			throw new MqException(FNC_NAME + ": Invalid packet header marker");
		}

		//
		final boolean tmpIsVideo = decodeFieldBool(FNC_NAME, frames, frameIx++);

		final MqPacketCodec tmpCodecEn;
		final String tmpCodecStr = decodeFieldString127(FNC_NAME, frames, frameIx++);
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
		final long tmpMdTimestamp = decodeFieldUint64(FNC_NAME, frames, frameIx++);  // UI64
		final int tmpMdCounter = decodeFieldUint32(FNC_NAME, frames, frameIx++);  // UI32
		final boolean tmpMdVideoIsKeyframe = (tmpIsVideo && decodeFieldBool(FNC_NAME, frames, frameIx++));
		final int tmpMdVideoResoWidth = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);  // UI32
		final int tmpMdVideoResoHeight = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);  // UI32
		final double tmpMdVideoFpsDbl;
		if (tmpIsVideo) {
			String tmpMdVideoFpsStr = decodeFieldString127(FNC_NAME, frames, frameIx++);
			try {
				tmpMdVideoFpsDbl = Double.parseDouble(tmpMdVideoFpsStr);
			} catch (NumberFormatException e) {
				throw new MqException(FNC_NAME + ": Invalid video FPS format in packet: '" + tmpMdVideoFpsStr + "'");
			}
		} else {
			tmpMdVideoFpsDbl = 0.0;
		}
		final int tmpMdVideoBitrate = (tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);  // UI32
		final int tmpMdAudioSamplerate = (! tmpIsVideo ? decodeFieldUint32(FNC_NAME, frames, frameIx++) : 0);  // UI32
		final byte tmpMdAudioChannelCount = (! tmpIsVideo ? decodeFieldUint08(FNC_NAME, frames, frameIx++) : 0);  // UI08
		final byte tmpMdPayloadCRC8 = decodeFieldUint08(FNC_NAME, frames, frameIx++);  // UI08
		final int tmpBinDataLen = decodeFieldUint32(FNC_NAME, frames, frameIx++);  // UI32

		decodeFieldBinData(FNC_NAME, frames, frameIx, tmpBinDataLen, payloadDataPtr);

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
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String decodeFieldString127(
				@NonNull String fncName,
				final @NonNull List<byte[]> frames,
				final int frameIx
			) throws MqException {
		validateFrameSize(fncName, frames, frameIx, 0);
		byte[] tmpStrBytes = frames.get(frameIx);
		if (tmpStrBytes.length > Byte.MAX_VALUE) {
			throw new MqException(fncName + ": Invalid string length (is=" + tmpStrBytes.length + ", max=" + Byte.MAX_VALUE + ")");
		}
		return new String(tmpStrBytes, ZMQ.CHARSET);
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

	private void writeFieldToMqString127(
				@NonNull String fncName,
				@NonNull String value,
				@SuppressWarnings("SameParameterValue") int flags
			) throws MqException {
		if (value.length() > Byte.MAX_VALUE) {
			throw new IllegalArgumentException(fncName + ": String too long (is: " + value.length() + ", max=" + Byte.MAX_VALUE + ")");
		}
		if (! waitForSocketReadyToWrite(fncName)) {
			return;
		}
		//noinspection DataFlowIssue
		if (! zmqSocket.send(value, flags)) {
			throw new MqException(fncName + ": Failed to send data to MQ (String127)");
		}
	}

	private void writeFieldToMqUint08(
				@NonNull String fncName,
				byte value,
				@SuppressWarnings("SameParameterValue") int flags
			) throws MqException {
		if (! waitForSocketReadyToWrite(fncName)) {
			return;
		}
		//noinspection DataFlowIssue
		if (! zmqSocket.send(new byte[]{value}, flags)) {
			throw new MqException(fncName + ": Failed to send data to MQ (Uint08)");
		}
	}

	private void writeFieldToMqUint32(
				@NonNull String fncName,
				int value,
				@SuppressWarnings("SameParameterValue") int flags
			) throws MqException {
		ByteBuffer tmpBuf = ByteBuffer
				.allocate(4)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(value);
		if (! waitForSocketReadyToWrite(fncName)) {
			return;
		}
		//noinspection DataFlowIssue
		if (! zmqSocket.send(tmpBuf.array(), flags)) {
			throw new MqException(fncName + ": Failed to send data to MQ (Uint32)");
		}
	}

	private void writeFieldToMqUint64(
				@NonNull String fncName,
				long value,
				@SuppressWarnings("SameParameterValue") int flags
			) throws MqException {
		ByteBuffer tmpBuf = ByteBuffer
				.allocate(8)
				.order(ByteOrder.BIG_ENDIAN)
				.putLong(value);
		if (! waitForSocketReadyToWrite(fncName)) {
			return;
		}
		//noinspection DataFlowIssue
		if (! zmqSocket.send(tmpBuf.array(), flags)) {
			throw new MqException(fncName + ": Failed to send data to MQ (Uint64)");
		}
	}

	private void writeFieldToMqBool(
				@NonNull String fncName,
				boolean value,
				@SuppressWarnings("SameParameterValue") int flags
			) throws MqException {
		if (! waitForSocketReadyToWrite(fncName)) {
			return;
		}
		//noinspection DataFlowIssue
		if (! zmqSocket.send(new byte[]{value ? (byte)1 : (byte)0}, flags)) {
			throw new MqException(fncName + ": Failed to send data to MQ (Bool)");
		}
	}

	private void writeFieldToMqBinData(
				@NonNull String fncName,
				final @NonNull BufferExt value,
				@SuppressWarnings("SameParameterValue") int flags
			) throws MqException {
		if (! waitForSocketReadyToWrite(fncName)) {
			return;
		}
		//noinspection DataFlowIssue
		if (! zmqSocket.send(value.getBaPtr(), 0, value.getUsed(), flags)) {
			throw new MqException(fncName + ": Failed to send data to MQ (BinData)");
		}
	}

}
