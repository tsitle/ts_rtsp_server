package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVBSFContext;
import org.bytedeco.ffmpeg.avcodec.AVBitStreamFilter;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Bitstream filter for converting length-prefixed H.264/H.265 packets to AnnexB.
 */
public final class FfmpegHelperBsfH26xToAnnexB implements FfmpegHelperBsfH26xInterface {

	private final boolean isH264;

	private @Nullable AVBSFContext bsfCtx;

	public FfmpegHelperBsfH26xToAnnexB(boolean isH264, @NonNull AVStream inVideoStream) throws FfmpegGenericException {
		this.isH264 = isH264;

		initForStream(inVideoStream.codecpar(), inVideoStream.time_base());
	}

	@SuppressWarnings("unused")
	public FfmpegHelperBsfH26xToAnnexB(boolean isH264, @NonNull AVCodecParameters codecParams, @NonNull AVRational timeBase)
			throws FfmpegGenericException {
		this.isH264 = isH264;

		initForStream(codecParams, timeBase);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Set the length-prefixed input packet.
	 */
	public void setInputPacket(@NonNull AVPacket inputPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".setInputPacket()";

		if (bsfCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": bsfCtx not initialized");
		}

		int r = avcodec.av_bsf_send_packet(bsfCtx, inputPkt);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_bsf_send_packet()", r);
	}

	/**
	 * Receive 0..N converted AnnexB-prefixed packets - one per call.
	 */
	public boolean receiveOneConvertedPacket(@NonNull AVPacket outputPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveOneConvertedPacket()";

		if (bsfCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": bsfCtx not initialized");
		}

		int r = avcodec.av_bsf_receive_packet(bsfCtx, outputPkt);
		if (r == avutil.AVERROR_EOF() || r == avutil.AVERROR_EAGAIN()) {
			return false;
		}
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_bsf_receive_packet()", r);
		return true;
	}

	@Override
	public void close() {
		if (bsfCtx != null) { avcodec.av_bsf_free(bsfCtx); bsfCtx = null; }
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void initForStream(@NonNull AVCodecParameters codecParams, @NonNull AVRational timeBase)
			throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".initForStream()";

		//
		String tmpFilterName = (isH264 ? "h264_mp4toannexb" : "hevc_mp4toannexb");
		AVBitStreamFilter bsf = avcodec.av_bsf_get_by_name(tmpFilterName);
		if (bsf == null) {
			throw new FfmpegGenericException(FNC_NAME + ": Bitstream filter not found: " + tmpFilterName);
		}

		AVBSFContext ctx = new AVBSFContext(null);
		int r = avcodec.av_bsf_alloc(bsf, ctx);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_bsf_alloc()", r);

		r = avcodec.avcodec_parameters_copy(ctx.par_in(), codecParams);
		if (r < 0) {
			avcodec.av_bsf_free(ctx);
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_parameters_copy()", r);
		}

		ctx.time_base_in(timeBase);

		r = avcodec.av_bsf_init(ctx);
		if (r < 0) {
			avcodec.av_bsf_free(ctx);
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_bsf_init()", r);
		}

		this.bsfCtx = ctx;
	}

}
