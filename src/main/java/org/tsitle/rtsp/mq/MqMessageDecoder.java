package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.zeromq.ZMQ;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decoder for MQ messages containing video and audio packets.
 */
public class MqMessageDecoder {

	public static class DecodeTuple {
		public final @NonNull MqPacketAv packetAv;
		public final int payloadSize;

		DecodeTuple(final @NonNull MqPacketAv packetAv, final int payloadSize) {
			this.packetAv = packetAv;
			this.payloadSize = payloadSize;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull MqPacketAv decodePacketAvFromExternalMq(
				final @NonNull List<byte[]> frames,
				@NonNull BufferExt payloadData
			) throws MqException {
		int frameIx = 0;

		final MqPacketCodec codec;
		final String tmpCodecStr = decodeFieldString(frames, frameIx++);
		try {
			codec = MqPacketCodec.of(tmpCodecStr);
		} catch (IllegalArgumentException e) {
			throw new MqException("Invalid codec name: '" + tmpCodecStr + "'");
		}

		final boolean tmpIsCodecGuessed = (codec.isVideo() && decodeFieldBool(frames, frameIx++));
		final long tmpMdTimestamp = decodeFieldUint64(frames, frameIx++);
		final int tmpMdCounter = decodeFieldUint32(frames, frameIx++);
		final boolean tmpMdVideoIsKeyframe = (codec.isVideo() && decodeFieldBool(frames, frameIx++));
		final int tmpMdVideoResoWidth = (codec.isVideo() ? decodeFieldUint32(frames, frameIx++) : 0);
		final int tmpMdVideoResoHeight = (codec.isVideo() ? decodeFieldUint32(frames, frameIx++) : 0);
		final int tmpMdVideoFps = (codec.isVideo() ? decodeFieldUint32(frames, frameIx++) : 0);
		final int tmpMdVideoBitrate = (codec.isVideo() ? decodeFieldUint32(frames, frameIx++) : 0);
		final byte tmpMdPayloadCRC8 = decodeFieldUint08(frames, frameIx++);
		final int tmpBinDataLen = decodeFieldUint32(frames, frameIx++);

		decodeFieldBinData(frames, frameIx, tmpBinDataLen, payloadData);

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
				payloadData
			);
	}

	/**
	 * Decode a packet from the given buffer - but without the payload data.
	 * @param fullFrame Full frame buffer
	 * @param payloadDataPtr Pointer to payload data buffer (but the buffer won't be written to)
	 * @return Decoded packet and payload data size
	 * @throws MqException If the input buffer is too small
	 */
	public static @NonNull DecodeTuple decodePacketAvFromInternalMq(
				final @NonNull ByteBuffer fullFrame,
				final @NonNull BufferExt payloadDataPtr
			) throws MqException {
		try {
			// read codec
			int tmpStrLen = fullFrame.getInt();
			byte[] tmpStrBytes = new byte[tmpStrLen];
			fullFrame.get(tmpStrBytes);
			String tmpCodecStr = new String(tmpStrBytes, StandardCharsets.UTF_8);
			MqPacketCodec codec = MqPacketCodec.of(tmpCodecStr);
			//
			boolean tmpIsCodecGuessed = (codec.isVideo() && fullFrame.get() != 0);
			long tmpMdTimestamp = fullFrame.getLong();
			int tmpMdCounter = fullFrame.getInt();
			boolean tmpMdVideoIsKeyframe = (codec.isVideo() && fullFrame.get() != 0);
			int tmpMdVideoResoWidth = (codec.isVideo() ? fullFrame.getInt() : 0);
			int tmpMdVideoResoHeight = (codec.isVideo() ? fullFrame.getInt() : 0);
			int tmpMdVideoFps = (codec.isVideo() ? fullFrame.getInt() : 0);
			int tmpMdVideoBitrate = (codec.isVideo() ? fullFrame.getInt() : 0);
			byte tmpMdPayloadCRC8 = fullFrame.get();
			int tmpPayloadLen = fullFrame.getInt();

			return new DecodeTuple(
					new MqPacketAv(
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
						),
					tmpPayloadLen
				);
		} catch (BufferUnderflowException e) {
			throw new MqException("BufferUnderflowException");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public static void validatePacketPayloadCRC(
				final @NonNull HashCrc8Helper hashCrc8Helper,
				byte expHashSum,
				final BufferExt dataRub
			) throws MqException {
		byte isHashSum = hashCrc8Helper.computeChecksum(dataRub, 0, dataRub.getUsed());
		if (isHashSum != expHashSum) {
			throw new MqException("Invalid payload CRC8 checksum: is=" +
					String.format("0x%02X", isHashSum) + ", exp=" + String.format("0x%02X", expHashSum));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String decodeFieldString(
				final @NonNull List<byte[]> frames,
				final int frameIx
			) throws MqException {
		validateFrameSize(frames, frameIx, 0);
		return new String(frames.get(frameIx), ZMQ.CHARSET);
	}

	private static byte decodeFieldUint08(final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(frames, frameIx, 1);
		return frames.get(frameIx)[0];
	}

	private static int decodeFieldUint32(final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(frames, frameIx, 4);
		return ByteBuffer
				.wrap(frames.get(frameIx))
				.order(ByteOrder.BIG_ENDIAN)
				.getInt();
	}

	private static long decodeFieldUint64(final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(frames, frameIx, 8);
		return ByteBuffer
				.wrap(frames.get(frameIx))
				.order(ByteOrder.BIG_ENDIAN)
				.getLong();
	}

	private static boolean decodeFieldBool(final @NonNull List<byte[]> frames, final int frameIx) throws MqException {
		validateFrameSize(frames, frameIx, 1);
		return (frames.get(frameIx)[0] != 0);
	}

	private static void decodeFieldBinData(
				final @NonNull List<byte[]> frames,
				final int frameIx,
				final int binDataLen,
				@NonNull BufferExt payloadData
			) throws MqException {
		validateFrameSize(frames, frameIx, binDataLen);
		payloadData.copyOf(frames.get(frameIx));
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void validateFrameIx(int frameIx, int framesSize) throws MqException {
		if (frameIx >= framesSize) {
			throw new MqException("Invalid frame index: " + frameIx + " >= " + framesSize);
		}
	}

	private static void validateFrameSize(final @NonNull List<byte[]> frames, final int frameIx, int requSize) throws MqException {
		validateFrameIx(frameIx, frames.size());
		if (frames.get(frameIx).length < requSize) {
			throw new MqException("Invalid frame size: " + frames.get(frameIx).length + " < " + requSize);
		}
	}

}
