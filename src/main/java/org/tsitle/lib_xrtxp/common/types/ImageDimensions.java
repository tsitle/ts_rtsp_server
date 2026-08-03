package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;

public record ImageDimensions(int imgWidth, int imgHeight) implements Cloneable {

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
	public @NonNull ImageDimensions scaleWithLimiter(int settingMaxDimension) {
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

		ImageDimensions resObj = ImageDimensions.of(this);
		if (settingMaxDimension > 1 && (tmpImgWidth > settingMaxDimension || tmpImgHeight > settingMaxDimension)) {
			resObj = internalScale(this, settingMaxDimension);
		}

		return resObj;
	}

	@SuppressWarnings("unused")
	public @NonNull ImageDimensions scaleToFixed(int settingDimension) {
		if (isEmpty() || settingDimension < 2) {
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

		ImageDimensions resObj = ImageDimensions.of(this);
		if (tmpImgWidth != settingDimension || tmpImgHeight != settingDimension) {
			resObj = internalScale(this, settingDimension);
		}

		return resObj;
	}

	@Override
	public @NonNull String toString() {
		if (isEmpty()) {
			return String.format("%s [empty]", getClass().getSimpleName());
		}
		return String.format("%s [%d x %d]", getClass().getSimpleName(), imgWidth, imgHeight);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public @NonNull ImageDimensions clone() {
		return new ImageDimensions(imgWidth, imgHeight);
	}

	private static @NonNull ImageDimensions internalScale(@NonNull ImageDimensions inputDims, int outputDim) {
		int tmpImgWidth = inputDims.imgWidth();
		int tmpImgHeight = inputDims.imgHeight();

		boolean isLandscape = (tmpImgWidth >= tmpImgHeight);
		double ratio = (double)tmpImgWidth / (double)tmpImgHeight;
		if (isLandscape) {
			tmpImgWidth = outputDim;
			if ((tmpImgWidth & 1) != 0) {
				tmpImgWidth -= 1;
			}
			tmpImgHeight = (int)((double)tmpImgWidth / ratio);
			if ((tmpImgHeight & 1) != 0) {
				tmpImgHeight -= 1;
			}
		} else {
			tmpImgHeight = outputDim;
			if ((tmpImgHeight & 1) != 0) {
				tmpImgHeight -= 1;
			}
			tmpImgWidth = (int)((double)tmpImgHeight * ratio);
			if ((tmpImgWidth & 1) != 0) {
				tmpImgWidth -= 1;
			}
		}

		tmpImgWidth = Math.max(tmpImgWidth, 2);
		tmpImgHeight = Math.max(tmpImgHeight, 2);
		return new ImageDimensions(tmpImgWidth, tmpImgHeight);
	}

}
