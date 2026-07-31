package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.global.avutil;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

public final class FfmpegHelperPktConverter {

	private FfmpegHelperPktConverter() { }

	public static void convertFfToBasics(
				boolean copyPayload,
				boolean isVideo,
				@NonNull AVPacket inputPktAv,
				@NonNull RationalNumber timeBase,
				@NonNull FfmpegAvPktBasics outputPktBas
			) {
		// payload
		if (copyPayload) {
			outputPktBas.pktBe.increaseSize(inputPktAv.size());
			inputPktAv.data().get(outputPktBas.pktBe.getBaPtr(), 0, inputPktAv.size());
			outputPktBas.pktBe.setUsed(inputPktAv.size());
		} else {
			outputPktBas.pktBe.clear();
		}

		// timing
		outputPktBas.ptsUnits = (inputPktAv.pts() == avutil.AV_NOPTS_VALUE ? null : inputPktAv.pts());
		outputPktBas.dtsUnits = (inputPktAv.dts() == avutil.AV_NOPTS_VALUE ? null : inputPktAv.dts());
		outputPktBas.timeBase.copyFrom(timeBase);

		// other
		outputPktBas.flags = inputPktAv.flags();
		outputPktBas.isVideo = isVideo;
		outputPktBas.subStreamIndex = inputPktAv.stream_index();
		outputPktBas.duration = inputPktAv.duration();
		outputPktBas.pos = inputPktAv.pos();
	}

	public static void convertBasicsToFf(
				@NonNull FfmpegAvPktBasics inputPktBas,
				@NonNull BufferView inputPktPayloadBv,
				int outputSubStreamIx,
				@NonNull AVPacket outputPktAv
			) {
		// payload
		outputPktAv.data().put(inputPktPayloadBv.getInternalBaPtr(), inputPktPayloadBv.getOffset(), inputPktPayloadBv.getLength());

		// timing
		outputPktAv.pts(inputPktBas.ptsUnits == null ? avutil.AV_NOPTS_VALUE : inputPktBas.ptsUnits);
		outputPktAv.dts(inputPktBas.dtsUnits == null ? avutil.AV_NOPTS_VALUE : inputPktBas.dtsUnits);
		outputPktAv.time_base().num(inputPktBas.timeBase.getNumerator());
		outputPktAv.time_base().den(inputPktBas.timeBase.getDenominator());

		// other
		outputPktAv.flags(inputPktBas.flags);
		outputPktAv.stream_index(outputSubStreamIx);
		outputPktAv.duration(inputPktBas.duration);
		outputPktAv.pos(inputPktBas.pos);
	}

}
