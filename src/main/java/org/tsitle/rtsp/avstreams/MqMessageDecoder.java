package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Decoder for MQ messages containing video and audio packets.
 */
public class MqMessageDecoder {

	public enum PacketCodec {
		H264("H264"),
		H265("H265"),
		LPCM16_8K_MONO("LPCM16/8000/1");

		private final @NonNull String codecName;
		PacketCodec(@NonNull String codecName) {
			this.codecName = codecName;
		}
		public static @NonNull PacketCodec of(@NonNull String codecName) {
			for (PacketCodec codec : PacketCodec.values()) {
				if (codec.codecName.equalsIgnoreCase(codecName)) {
					return codec;
				}
			}
			throw new IllegalArgumentException("Unknown codec name: '" + codecName + "'");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public record PacketVideo(
				@NonNull PacketCodec codec,
				boolean isCodecGuessed,
				long mdTimestamp,
				int mdCounter,
				boolean mdVideoIsKeyframe,
				int mdVideoResoWidth,
				int mdVideoResoHeight,
				int mdVideoFps,
				int mdVideoBitrate,
				byte mdPayloadCRC8,
				@NonNull BufferExt dataRub
			) {
		@Override
		public @NonNull String toString() {
			return getClass().getSimpleName() + " [" +
					"codec=" + codec +
					", isCodecGuessed=" + (isCodecGuessed ? "T" : "F") +
					", mdTimestamp=" + Long.toUnsignedString(mdTimestamp) +
					", mdCounter=" + Integer.toUnsignedString(mdCounter) +
					", mdVideoIsKeyframe=" + (mdVideoIsKeyframe ? "T" : "F") +
					", mdVideoResoWidth=" + Integer.toUnsignedString(mdVideoResoWidth) +
					", mdVideoResoHeight=" + Integer.toUnsignedString(mdVideoResoHeight) +
					", mdVideoFps=" + Integer.toUnsignedString(mdVideoFps) +
					", mdVideoBitrate=" + Integer.toUnsignedString(mdVideoBitrate) +
					", mdPayloadCRC8=" + String.format("0x%02X", mdPayloadCRC8) +
					", dataRub.sz=" + dataRub.getUsed() +
					"]";
		}
	}

	public record PacketAudio(
				@NonNull PacketCodec codec,
				long mdTimestamp,
				int mdCounter,
				byte mdPayloadCRC8,
				@NonNull BufferExt dataRub
			) {
		@Override
		public @NonNull String toString() {
			return getClass().getSimpleName() + " [" +
					"codec=" + codec +
					", mdTimestamp=" + Long.toUnsignedString(mdTimestamp) +
					", mdCounter=" + Integer.toUnsignedString(mdCounter) +
					", mdPayloadCRC8=" + String.format("0x%02X", mdPayloadCRC8) +
					", dataRub.sz=" + dataRub.getUsed() +
					"]";
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull PacketCodec decodePacket_onlyCodec(final @NonNull List<byte[]> frames) throws MqException {
		final String tmpCodecStr = decodeFieldString(frames, 0);
		try {
			return PacketCodec.of(tmpCodecStr);
		} catch (IllegalArgumentException e) {
			throw new MqException("Invalid codec name: '" + tmpCodecStr + "'");
		}
	}

	public static @NonNull PacketVideo decodePacketVideo(final @NonNull List<byte[]> frames) throws MqException {
		final PacketCodec codec = decodePacket_onlyCodec(frames);
		int frameIx = 1;
		final boolean tmpIsCodecGuessed = decodeFieldBool(frames, frameIx++);
		final long tmpMdTimestamp = decodeFieldUint64(frames, frameIx++);
		final int tmpMdCounter = decodeFieldUint32(frames, frameIx++);
		final boolean tmpMdVideoIsKeyframe = decodeFieldBool(frames, frameIx++);
		final int tmpMdVideoResoWidth = decodeFieldUint32(frames, frameIx++);
		final int tmpMdVideoResoHeight = decodeFieldUint32(frames, frameIx++);
		final int tmpMdVideoFps = decodeFieldUint32(frames, frameIx++);
		final int tmpMdVideoBitrate = decodeFieldUint32(frames, frameIx++);
		final byte tmpMdPayloadCRC8 = decodeFieldUint08(frames, frameIx++);
		final int tmpBinDataLen = decodeFieldUint32(frames, frameIx++);
		return new PacketVideo(
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
				decodeFieldBinData(frames, frameIx, tmpBinDataLen)
			);
	}

	public static @NonNull PacketAudio decodePacketAudio(final @NonNull List<byte[]> frames) throws MqException {
		final PacketCodec codec = decodePacket_onlyCodec(frames);
		int frameIx = 1;
		final long tmpMdTimestamp = decodeFieldUint64(frames, frameIx++);
		final int tmpMdCounter = decodeFieldUint32(frames, frameIx++);
		final byte tmpMdPayloadCRC8 = decodeFieldUint08(frames, frameIx++);
		final int tmpBinDataLen = decodeFieldUint32(frames, frameIx++);
		return new PacketAudio(
				codec,
				tmpMdTimestamp,
				tmpMdCounter,
				tmpMdPayloadCRC8,
				decodeFieldBinData(frames, frameIx, tmpBinDataLen)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

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

	@SuppressWarnings("SameParameterValue")
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

	private static @NonNull BufferExt decodeFieldBinData(
				final @NonNull List<byte[]> frames,
				final int frameIx,
				final int binDataLen
			) throws MqException {
		validateFrameSize(frames, frameIx, binDataLen);
		BufferExt resBe = new BufferExt();
		resBe.copyOf(frames.get(frameIx));
		return resBe;
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
