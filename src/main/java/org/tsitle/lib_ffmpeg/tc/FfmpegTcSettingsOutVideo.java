package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeH26x;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;

import java.util.HashSet;
import java.util.Set;

/**
 * Settings for Video Transcoder Output.
 */
public final class FfmpegTcSettingsOutVideo extends FfmpegTcSettingsOutBase {

	public enum FrameRateConversionMode {
		PASSTHROUGH,
		LIMITED,
		FIXED
	}

	public enum ImageScalingMode {
		PASSTHROUGH,
		LIMITED,
		FIXED
	}

	public enum OutputModeMjpeg {
		/** (default:) uses optimized Huffman tables. this results in smaller frames but is not compatible with RTP. */
		OPTIMIZED,
		/** uses standard Huffman tables as is required for RTP. this results in larger frames */
		RTP
	}

	public enum QualitySpeedRatio {
		BEST_QUALITY,
		BALANCED,
		FASTEST
	}

	public enum GopSize {
		EVERY_30_FRAMES,
		EVERY_60_FRAMES
	}

	public @NonNull FrameRateConversionMode cfgFrCm;
	public @NonNull FrameRateEnum cfgFrameRateMax;
	public @NonNull FrameRateEnum cfgFrameRateFixed;
	public int cfgBitRateKbps;
	public @NonNull ImageScalingMode cfgImgScalingMode;
	public int cfgImgDimsMax;
	public int cfgImgDimsFixed;
	/** Output H.26x as AnnexB, length-prefixed or as-is? */
	public @NonNull FfmpegPktConvModeH26x cfgOutputModeH26x;
	/** Output MJPEG with default or RTP-compatible Huffman Tables? */
	public @NonNull OutputModeMjpeg cfgOutputModeMjpeg;
	public @NonNull QualitySpeedRatio cfgQualitySpeedRatioAv1;
	public @NonNull QualitySpeedRatio cfgQualitySpeedRatioVpX;
	/** GOP size (Group of Pictures) is the number of frames between two full keyframes (I-frames) */
	public @NonNull GopSize cfgGopSize;

	public final @NonNull Set<@NonNull String> cfgDisabledEncodersList = new HashSet<>();

	public FfmpegTcSettingsOutVideo() {
		clear();
	}

	public void clear() {
		baseClear();

		cfgFrCm = FrameRateConversionMode.PASSTHROUGH;
		cfgFrameRateMax = FrameRateEnum.UNKNOWN;
		cfgFrameRateFixed = FrameRateEnum.UNKNOWN;
		cfgBitRateKbps = -1;
		cfgImgScalingMode = ImageScalingMode.PASSTHROUGH;
		cfgImgDimsMax = -1;
		cfgImgDimsFixed = -1;
		cfgOutputModeH26x = FfmpegPktConvModeH26x.PASSTHROUGH;
		cfgOutputModeMjpeg = OutputModeMjpeg.OPTIMIZED;
		cfgQualitySpeedRatioAv1 = QualitySpeedRatio.BALANCED;
		cfgQualitySpeedRatioVpX = QualitySpeedRatio.BALANCED;
		cfgGopSize = GopSize.EVERY_60_FRAMES;

		cfgDisabledEncodersList.clear();
	}

	public void copyFrom(@NonNull FfmpegTcSettingsOutVideo other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		cfgFrCm = other.cfgFrCm;
		cfgFrameRateMax = other.cfgFrameRateMax;
		cfgFrameRateFixed = other.cfgFrameRateFixed;
		cfgBitRateKbps = other.cfgBitRateKbps;
		cfgImgScalingMode = other.cfgImgScalingMode;
		cfgImgDimsMax = other.cfgImgDimsMax;
		cfgImgDimsFixed = other.cfgImgDimsFixed;
		cfgOutputModeH26x = other.cfgOutputModeH26x;
		cfgOutputModeMjpeg = other.cfgOutputModeMjpeg;
		cfgQualitySpeedRatioAv1 = other.cfgQualitySpeedRatioAv1;
		cfgQualitySpeedRatioVpX = other.cfgQualitySpeedRatioVpX;
		cfgGopSize = other.cfgGopSize;

		cfgDisabledEncodersList.clear();
		cfgDisabledEncodersList.addAll(other.cfgDisabledEncodersList);
	}

}
