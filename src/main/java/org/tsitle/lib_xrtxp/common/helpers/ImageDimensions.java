package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;

public record ImageDimensions(int imgWidth, int imgHeight) {

	public static @NonNull ImageDimensions ofEmpty() {
		return new ImageDimensions(-1, -1);
	}

	public static @NonNull ImageDimensions of(int imgWidth, int imgHeight) {
		return new ImageDimensions(imgWidth, imgHeight);
	}

	public static @NonNull ImageDimensions of(@NonNull ImageDimensions imgDims) {
		return new ImageDimensions(imgDims.imgWidth, imgDims.imgHeight);
	}

	public boolean isEmpty() {
		return (imgWidth < 2 || imgHeight < 2);
	}

	@SuppressWarnings("unused")
	public @NonNull ImageDimensions scale(int settingMaxDimension) {
		if (isEmpty()) {
			return ImageDimensions.ofEmpty();
		}

		int tmpImgWidth = imgWidth;
		int tmpImgHeight = imgHeight;
		if ((tmpImgWidth & 1) != 0) {
			tmpImgWidth -= 1;
		}
		if ((tmpImgHeight & 1) != 0) {
			tmpImgHeight -= 1;
		}

		if (settingMaxDimension > 1 && (tmpImgWidth > settingMaxDimension || tmpImgHeight > settingMaxDimension)) {
			boolean isLandscape = (tmpImgWidth >= tmpImgHeight);
			double ratio = (double)tmpImgWidth / (double)tmpImgHeight;
			if (isLandscape) {
				tmpImgWidth = settingMaxDimension;
				if ((tmpImgWidth & 1) != 0) {
					tmpImgWidth -= 1;
				}
				tmpImgHeight = (int)((double)tmpImgWidth / ratio);
				if ((tmpImgHeight & 1) != 0) {
					tmpImgHeight -= 1;
				}
			} else {
				tmpImgHeight = settingMaxDimension;
				if ((tmpImgHeight & 1) != 0) {
					tmpImgHeight -= 1;
				}
				tmpImgWidth = (int)((double)tmpImgHeight * ratio);
				if ((tmpImgWidth & 1) != 0) {
					tmpImgWidth -= 1;
				}
			}
		}

		return new ImageDimensions(tmpImgWidth, tmpImgHeight);
	}

	@Override
	public @NonNull String toString() {
		if (isEmpty()) {
			return String.format("%s [empty]", getClass().getSimpleName());
		}
		return String.format("%s [%d x %d]", getClass().getSimpleName(), imgWidth, imgHeight);
	}

}
