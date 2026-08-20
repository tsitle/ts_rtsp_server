package org.tsitle.lib_ffmpeg.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;

public final class FfmpegHelperValidateExtradata {

	private FfmpegHelperValidateExtradata() { }

	public static void validateForCodec(
				@NonNull String fncName,
				@NonNull FfmpegCodec ffmpegCodec,
				int audioChannelCount,
				@NonNull ExtradataContainerHex extradata
			) throws FfmpegGenericException {
		if (extradata.isEmpty() &&  // @CODEC
				(ffmpegCodec == FfmpegCodec.A_AAC ||
						ffmpegCodec == FfmpegCodec.A_ALAC ||
						ffmpegCodec == FfmpegCodec.A_FLAC ||
						ffmpegCodec == FfmpegCodec.A_VORBIS ||
						ffmpegCodec == FfmpegCodec.V_AV1 ||
						ffmpegCodec == FfmpegCodec.V_H264 ||
						ffmpegCodec == FfmpegCodec.V_H265 ||
						ffmpegCodec == FfmpegCodec.V_MPEG2 ||
						ffmpegCodec == FfmpegCodec.V_MPEG4 ||
						ffmpegCodec == FfmpegCodec.V_THEORA)) {
			throw new FfmpegGenericException(fncName + ": extradataHex is empty for codec=" + ffmpegCodec);
		}
		if (extradata.isEmpty() &&
				ffmpegCodec == FfmpegCodec.A_OPUS && audioChannelCount > 2) {
			throw new FfmpegGenericException(fncName + ": extradataHex is empty for Opus with >2 channels");
		}
		if (extradata.isEmpty()) {
			return;
		}

		if ((ffmpegCodec == FfmpegCodec.A_AAC && ! extradata.isCodecAac()) ||  // @CODEC
				(ffmpegCodec == FfmpegCodec.A_ALAC && ! extradata.isCodecAlac()) ||
				(ffmpegCodec == FfmpegCodec.A_FLAC && ! extradata.isCodecFlac()) ||
				(ffmpegCodec == FfmpegCodec.A_OPUS && ! extradata.isCodecOpus()) ||
				(ffmpegCodec == FfmpegCodec.A_VORBIS && ! extradata.isCodecVorbis()) ||
				(ffmpegCodec == FfmpegCodec.V_AV1 && ! extradata.isCodecAv1()) ||
				(ffmpegCodec == FfmpegCodec.V_H264 && ! extradata.isCodecH264()) ||
				(ffmpegCodec == FfmpegCodec.V_H265 && ! extradata.isCodecH265()) ||
				(ffmpegCodec == FfmpegCodec.V_MPEG2 && ! extradata.isCodecMpeg2()) ||
				(ffmpegCodec == FfmpegCodec.V_MPEG4 && ! extradata.isCodecMpeg4()) ||
				(ffmpegCodec == FfmpegCodec.V_THEORA && ! extradata.isCodecTheora())) {
			throw new FfmpegGenericException(fncName + ": extradataHex codec does not match expected codec " +
					"(is=" + extradata.getCodecStr() + ", exp=" + ffmpegCodec + ")");
		}
	}

}
