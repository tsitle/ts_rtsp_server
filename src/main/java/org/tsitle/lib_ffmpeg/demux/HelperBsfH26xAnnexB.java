package org.tsitle.lib_ffmpeg.demux;

import org.bytedeco.ffmpeg.avcodec.AVBSFContext;
import org.bytedeco.ffmpeg.avcodec.AVBitStreamFilter;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegErrorHelper;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;

/**
 * Bitstream filter for H.264/5 AnnexB.
 */
final class HelperBsfH26xAnnexB implements AutoCloseable {

	private final boolean isH264;

	private @Nullable AVBSFContext bsfCtx;

	HelperBsfH26xAnnexB(boolean isH264, @NonNull AVStream inVideoStream) throws FfmpegGenericException {
		this.isH264 = isH264;

		initForStream(inVideoStream);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Send one demuxed packet.
	 */
	void setInputPacket(@NonNull AVPacket inputPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".setInputPacket()";

		if (bsfCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": bsfCtx not initialized");
		}

		int r = avcodec.av_bsf_send_packet(bsfCtx, inputPkt);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_bsf_send_packet()", r);
	}

	/**
	 * Receive 0..N converted AnnexB packets - one per call.
	 */
	boolean receiveOnePacket(@NonNull AVPacket outputPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveOnePacket()";

		if (bsfCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": bsfCtx not initialized");
		}

		int r = avcodec.av_bsf_receive_packet(bsfCtx, outputPkt);
		if (r == avutil.AVERROR_EOF() || r == avutil.AVERROR_EAGAIN()) {
			return false;
		}
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_bsf_receive_packet()", r);
		return true;
	}

	@Override
	public void close() {
		if (bsfCtx != null) { avcodec.av_bsf_free(bsfCtx); bsfCtx = null; }
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void initForStream(@NonNull AVStream inVideoStream) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".initForStream()";

		//
		String tmpFilterName = (isH264 ? "h264_mp4toannexb" : "hevc_mp4toannexb");
		AVBitStreamFilter bsf = avcodec.av_bsf_get_by_name(tmpFilterName);
		if (bsf == null) {
			throw new FfmpegGenericException(FNC_NAME + ": Bitstream filter not found: " + tmpFilterName);
		}

		AVBSFContext ctx = new AVBSFContext(null);
		int r = avcodec.av_bsf_alloc(bsf, ctx);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_bsf_alloc()", r);

		r = avcodec.avcodec_parameters_copy(ctx.par_in(), inVideoStream.codecpar());
		if (r < 0) {
			avcodec.av_bsf_free(ctx);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_parameters_copy()", r);
		}

		ctx.time_base_in(inVideoStream.time_base());

		r = avcodec.av_bsf_init(ctx);
		if (r < 0) {
			avcodec.av_bsf_free(ctx);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_bsf_init()", r);
		}

		this.bsfCtx = ctx;
	}

}
