package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.global.avcodec;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Queue;

/**
 * Bitstream filter for converting AnnexB-prefixed H.264/H.265 packets to length-prefixed.
 */
public final class FfmpegHelperBsfH26xToLengthPrefixed implements FfmpegHelperBsfH26xInterface {

	private static final class QueuedPacket {
		private final byte[] payload;
		private final int streamIndex;
		private final long pts;
		private final long dts;
		private final long duration;
		private final long pos;
		private final int flags;
		private final int tbNum;
		private final int tbDen;

		private QueuedPacket(byte[] payload, @NonNull AVPacket srcPkt) {
			this.payload = payload;
			this.streamIndex = srcPkt.stream_index();
			this.pts = srcPkt.pts();
			this.dts = srcPkt.dts();
			this.duration = srcPkt.duration();
			this.pos = srcPkt.pos();
			this.flags = srcPkt.flags();
			this.tbNum = srcPkt.time_base().num();
			this.tbDen = srcPkt.time_base().den();
		}
	}

	private final @NonNull Queue<@NonNull QueuedPacket> outQueue = new ArrayDeque<>();

	public FfmpegHelperBsfH26xToLengthPrefixed() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public static boolean isAnnexB(@NonNull String hex) {
		if (hex.length() < 8) {
			return false;
		}
		return isAnnexB(HexFormat.of().parseHex(hex.substring(0, 8)));
	}

	public static boolean isAnnexB(@NonNull AVPacket pktAv) {
		if (pktAv.size() < 4) {
			return false;
		}
		byte[] inBa = new byte[4];
		pktAv.data().get(inBa, 0, inBa.length);
		return isAnnexB(inBa);
	}

	@SuppressWarnings("unused")
	public static boolean isAnnexB(@NonNull BufferExt pktBe) {
		if (pktBe.getUsed() < 4) {
			return false;
		}
		byte[] inBa = new byte[4];
		System.arraycopy(pktBe.getBaPtr(), 0, inBa, 0, inBa.length);
		return isAnnexB(inBa);
	}

	public static boolean isAnnexB(byte[] pktBa) {
		if (pktBa.length < 4) {
			return false;
		}
		return (findStartCode(pktBa, 0) == 0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Set the AnnexB-prefixed input packet.
	 */
	public void setInputPacket(@NonNull AVPacket inputPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".setInputPacket()";

		if (inputPkt.data() == null || inputPkt.size() < 1) {
			return;
		}

		byte[] inBa = new byte[inputPkt.size()];
		inputPkt.data().get(inBa, 0, inputPkt.size());

		byte[] convertedBa = convertAnnexBToLengthPrefixed(FNC_NAME, inBa);
		outQueue.add(new QueuedPacket(convertedBa, inputPkt));
	}

	/**
	 * Receive 0..N converted length-prefixed packets - one per call.
	 */
	public boolean receiveOneConvertedPacket(@NonNull AVPacket outputPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveOneConvertedPacket()";

		QueuedPacket qp = outQueue.poll();
		if (qp == null) {
			return false;
		}

		avcodec.av_packet_unref(outputPkt);

		int r = avcodec.av_new_packet(outputPkt, qp.payload.length);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_new_packet()", r);

		outputPkt.data().put(qp.payload, 0, qp.payload.length);
		outputPkt.stream_index(qp.streamIndex);
		outputPkt.pts(qp.pts);
		outputPkt.dts(qp.dts);
		outputPkt.duration(qp.duration);
		outputPkt.pos(qp.pos);
		outputPkt.flags(qp.flags);
		outputPkt.time_base().num(qp.tbNum);
		outputPkt.time_base().den(qp.tbDen);

		return true;
	}

	@Override
	public void close() {
		outQueue.clear();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] convertAnnexBToLengthPrefixed(@NonNull String fncName, byte[] annexB)
			throws FfmpegGenericException {
		int len = annexB.length;
		int firstSc = findStartCode(annexB, 0);
		if (firstSc < 0) {
			throw new FfmpegGenericException(fncName + ": input packet is not AnnexB (start code not found)");
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream(len + 32);
		int scPos = firstSc;

		while (scPos >= 0) {
			int scLen = startCodeLengthAt(annexB, scPos);
			int nalStart = scPos + scLen;

			int nextSc = findStartCode(annexB, nalStart);
			int nalEnd = (nextSc >= 0 ? nextSc : len);

			if (nalStart < nalEnd) {
				int nalLen = nalEnd - nalStart;
				writeU32Be(out, nalLen);
				out.write(annexB, nalStart, nalLen);
			}

			scPos = nextSc;
		}

		return out.toByteArray();
	}

	private static int findStartCode(byte[] ba, int from) {
		int end = ba.length - 3;
		for (int i = Math.max(0, from); i < end; i++) {
			if (ba[i] != 0 || ba[i + 1] != 0) {
				continue;
			}
			if (ba[i + 2] == 1) {
				return i;  // 00 00 01
			}
			if (i + 3 < ba.length && ba[i + 2] == 0 && ba[i + 3] == 1) {
				return i;  // 00 00 00 01
			}
		}
		return -1;
	}

	private static int startCodeLengthAt(byte[] ba, int pos) {
		if (pos + 3 < ba.length && ba[pos] == 0 && ba[pos + 1] == 0 && ba[pos + 2] == 0 && ba[pos + 3] == 1) {
			return 4;
		}
		return 3;
	}

	private static void writeU32Be(@NonNull ByteArrayOutputStream out, int value) {
		out.write((value >>> 24) & 0xFF);
		out.write((value >>> 16) & 0xFF);
		out.write((value >>> 8) & 0xFF);
		out.write(value & 0xFF);
	}

}
