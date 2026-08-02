package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegPixelFmt;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;

public final class FfmpegHelperPixelFmtConv {

	private FfmpegHelperPixelFmtConv() { }

	@SuppressWarnings("unused")
	public static int convertPixelFmtToInt(@NonNull String fncName, @NonNull FfmpegPixelFmt pixelFmt)
			throws FfmpegGenericException {
		return switch (pixelFmt) {
				case UNKNOWN -> throw new FfmpegGenericException(fncName + ": Invalid pixel format");
				case YUV420P -> avutil.AV_PIX_FMT_YUV420P;
				case YUV420P10LE -> avutil.AV_PIX_FMT_YUV420P10LE;
				case YUV422P -> avutil.AV_PIX_FMT_YUV422P;
			};
	}

	public static @NonNull FfmpegPixelFmt convertPixelFmtFromInt(@NonNull String fncName, int pixelFmt)
			throws FfmpegGenericException {
		return switch (pixelFmt) {
				case avutil.AV_PIX_FMT_YUV420P, avutil.AV_PIX_FMT_YUVJ420P -> FfmpegPixelFmt.YUV420P;
				case avutil.AV_PIX_FMT_YUV422P, avutil.AV_PIX_FMT_YUVJ422P -> FfmpegPixelFmt.YUV422P;
				case avutil.AV_PIX_FMT_YUV420P10LE -> FfmpegPixelFmt.YUV420P10LE;
				default -> {
					String pixFmtName;
					try (BytePointer bp = avutil.av_get_pix_fmt_name(pixelFmt)) {
						pixFmtName = (bp != null ? bp.getString() : "-unknown-");
					}
					throw new FfmpegGenericException(fncName + ": unsupported PixelFmt: " + pixFmtName + " (#" + pixelFmt + ")");
				}
			};
	}

}
