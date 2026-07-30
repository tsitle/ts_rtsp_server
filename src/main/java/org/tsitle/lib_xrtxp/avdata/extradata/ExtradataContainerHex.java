package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

public final class ExtradataContainerHex {

	private @NonNull String hex;

	private boolean isCodecAac;
	private boolean isCodecH264;
	private boolean isCodecH265;

	private boolean isFmtH26xAnnexB;

	private ExtradataContainerHex(@NonNull String hex) {
		clear();
		this.hex = hex;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull ExtradataContainerHex ofEmpty() {
		return new ExtradataContainerHex("");
	}

	public static @NonNull ExtradataContainerHex createAac(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecAac = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex createH264_avcC(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH264 = true;
		resObj.isFmtH26xAnnexB = false;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex createH264_annexB(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH264 = true;
		resObj.isFmtH26xAnnexB = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex createH265_hvcC(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH265 = true;
		resObj.isFmtH26xAnnexB = false;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex createH265_annexB(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH265 = true;
		resObj.isFmtH26xAnnexB = true;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return hex.isBlank();
	}

	public @NonNull String getEd() {
		return hex;
	}

	public boolean isCodecAac() {
		return isCodecAac;
	}

	public boolean isCodecH264() {
		return isCodecH264;
	}

	public boolean isCodecH265() {
		return isCodecH265;
	}

	/**
	 * Is the 'extradata' in H26x AnnexB format? If not, it is in H264 avcC or H265 hvcC format or something else.
	 * @return True if the 'extradata' is in H26x AnnexB format
	 */
	public boolean isFmtH26xAnnexB() {
		return isFmtH26xAnnexB;
	}

	public void clear() {
		hex = "";

		isCodecAac = false;
		isCodecH264 = false;
		isCodecH265 = false;

		isFmtH26xAnnexB = false;
	}

	public void copyFrom(@NonNull ExtradataContainerHex other) {
		hex = other.hex;

		isCodecAac = other.isCodecAac;
		isCodecH264 = other.isCodecH264;
		isCodecH265 = other.isCodecH265;

		isFmtH26xAnnexB = other.isFmtH26xAnnexB;
	}

}
