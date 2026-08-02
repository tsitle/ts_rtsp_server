package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public final class ExtradataContainerHex {  // @CODEC

	private @NonNull String hex;

	private boolean isCodecAac;
	private boolean isCodecAlac;
	private boolean isCodecAv1;
	private boolean isCodecFlac;
	private boolean isCodecH264;
	private boolean isCodecH265;
	private boolean isCodecMpeg2;
	private boolean isCodecMpeg4;
	private boolean isCodecOpus;
	private boolean isCodecTheora;
	private boolean isCodecVorbis;

	private boolean isFmtH26xAnnexB;

	private ExtradataContainerHex(@NonNull String hex) {
		clear();
		this.hex = hex.toUpperCase();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull ExtradataContainerHex ofEmpty() {
		return new ExtradataContainerHex("");
	}

	public static @NonNull ExtradataContainerHex ofAac(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecAac = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofAlac(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecAlac = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofAv1(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecAv1 = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofFlac(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecFlac = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofH264_avcC(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH264 = true;
		resObj.isFmtH26xAnnexB = false;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofH264_annexB(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH264 = true;
		resObj.isFmtH26xAnnexB = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofH265_hvcC(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH265 = true;
		resObj.isFmtH26xAnnexB = false;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofH265_annexB(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecH265 = true;
		resObj.isFmtH26xAnnexB = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofMpeg2(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecMpeg2 = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofMpeg4(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecMpeg4 = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofOpus(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecOpus = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofTheora(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecTheora = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerHex ofVorbis(@NonNull String hex) {
		ExtradataContainerHex resObj = new ExtradataContainerHex(hex);
		resObj.isCodecVorbis = true;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return hex.isBlank();
	}

	public @NonNull String getEd() {
		return hex;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isCodecAac() {
		return isCodecAac;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isCodecAlac() {
		return isCodecAlac;
	}

	@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
	public boolean isCodecAv1() {
		return isCodecAv1;
	}

	@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
	public boolean isCodecFlac() {
		return isCodecFlac;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isCodecH264() {
		return isCodecH264;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isCodecH265() {
		return isCodecH265;
	}

	@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
	public boolean isCodecMpeg2() {
		return isCodecMpeg2;
	}

	@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
	public boolean isCodecMpeg4() {
		return isCodecMpeg4;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isCodecOpus() {
		return isCodecOpus;
	}

	@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
	public boolean isCodecTheora() {
		return isCodecTheora;
	}

	@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "unused"})
	public boolean isCodecVorbis() {
		return isCodecVorbis;
	}

	public @NonNull String getCodecStr() {
		if (isEmpty()) { return "-none-"; }
		if (isCodecAac) { return "AAC"; }
		if (isCodecAlac) { return "ALAC"; }
		if (isCodecAv1) { return "AV1"; }
		if (isCodecFlac) { return "FLAC"; }
		if (isCodecH264) { return "H264"; }
		if (isCodecH265) { return "H265"; }
		if (isCodecMpeg2) { return "MPEG2"; }
		if (isCodecMpeg4) { return "MPEG4"; }
		if (isCodecOpus) { return "Opus"; }
		if (isCodecTheora) { return "Theora"; }
		if (isCodecVorbis) { return "Vorbis"; }
		throw new IllegalStateException(getClass().getSimpleName() + ".getCodecStr(): " + "Unknown codec");
	}

	/**
	 * Is the 'extradata' in H26x AnnexB format? If not, it is in H264 avcC or H265 hvcC format or something else.
	 * @return True if the 'extradata' is in H26x AnnexB format
	 */
	public boolean isFmtH26xAnnexB() {
		return isFmtH26xAnnexB;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		hex = "";

		isCodecAac = false;
		isCodecAlac = false;
		isCodecAv1 = false;
		isCodecFlac = false;
		isCodecH264 = false;
		isCodecH265 = false;
		isCodecMpeg2 = false;
		isCodecMpeg4 = false;
		isCodecOpus = false;
		isCodecTheora = false;
		isCodecVorbis = false;

		isFmtH26xAnnexB = false;
	}

	public void copyFrom(@NonNull ExtradataContainerHex other) {
		hex = other.hex;

		isCodecAac = other.isCodecAac;
		isCodecAlac = other.isCodecAlac;
		isCodecAv1 = other.isCodecAv1;
		isCodecFlac = other.isCodecFlac;
		isCodecH264 = other.isCodecH264;
		isCodecH265 = other.isCodecH265;
		isCodecMpeg2 = other.isCodecMpeg2;
		isCodecMpeg4 = other.isCodecMpeg4;
		isCodecOpus = other.isCodecOpus;
		isCodecTheora = other.isCodecTheora;
		isCodecVorbis = other.isCodecVorbis;

		isFmtH26xAnnexB = other.isFmtH26xAnnexB;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof ExtradataContainerHex that)) {
			return false;
		}
		if (isEmpty() && that.isEmpty()) {
			return true;
		}
		if (isEmpty() != that.isEmpty()) {
			return false;
		}
		return (isCodecAac == that.isCodecAac &&
				isCodecAlac == that.isCodecAlac &&
				isCodecAv1 == that.isCodecAv1 &&
				isCodecFlac == that.isCodecFlac &&
				isCodecH264 == that.isCodecH264 &&
				isCodecH265 == that.isCodecH265 &&
				isCodecMpeg2 == that.isCodecMpeg2 &&
				isCodecMpeg4 == that.isCodecMpeg4 &&
				isCodecOpus == that.isCodecOpus &&
				isCodecTheora == that.isCodecTheora &&
				isCodecVorbis == that.isCodecVorbis &&
				isFmtH26xAnnexB == that.isFmtH26xAnnexB &&
				Objects.equals(hex, that.hex));
	}

	@Override
	public int hashCode() {
		if (isEmpty()) {
			return 0;
		}
		return Objects.hash(
				hex,
				isCodecAac,
				isCodecAlac,
				isCodecAv1,
				isCodecFlac,
				isCodecH264,
				isCodecH265,
				isCodecMpeg2,
				isCodecMpeg4,
				isCodecOpus,
				isCodecTheora,
				isCodecVorbis,
				isFmtH26xAnnexB
			);
	}

	@Override
	public @NonNull String toString() {
		String resS = getClass().getSimpleName() + " [";
		if (isEmpty()) {
			resS += "empty";
		} else {
			resS += "codec=" + getCodecStr();
			if (isCodecH264 || isCodecH265) {
				resS += ", fmt=" + (isFmtH26xAnnexB ? "AnnexB" : "LP");
			}
			resS += String.format(", hex='%s'", hex);
		}
		return resS + "]";
	}

}
