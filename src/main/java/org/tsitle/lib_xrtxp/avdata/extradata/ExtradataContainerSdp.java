package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

public final class ExtradataContainerSdp {

	private @NonNull String sdp;

	private boolean isCodecAac;
	private boolean isCodecH264;
	private boolean isCodecH265;

	private ExtradataContainerSdp(@NonNull String sdp) {
		clear();
		this.sdp = sdp;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull ExtradataContainerSdp ofEmpty() {
		return new ExtradataContainerSdp("");
	}

	public static @NonNull ExtradataContainerSdp createAac(@NonNull String sdp) {
		ExtradataContainerSdp resObj = new ExtradataContainerSdp(sdp);
		resObj.isCodecAac = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerSdp createH264(@NonNull String sdp) {
		ExtradataContainerSdp resObj = new ExtradataContainerSdp(sdp);
		resObj.isCodecH264 = true;
		return resObj;
	}

	public static @NonNull ExtradataContainerSdp createH265(@NonNull String sdp) {
		ExtradataContainerSdp resObj = new ExtradataContainerSdp(sdp);
		resObj.isCodecH265 = true;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return sdp.isBlank();
	}

	public @NonNull String getEd() {
		return sdp;
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

	public void clear() {
		sdp = "";

		isCodecAac = false;
		isCodecH264 = false;
		isCodecH265 = false;
	}

	public void copyFrom(@NonNull ExtradataContainerSdp other) {
		sdp = other.sdp;

		isCodecAac = other.isCodecAac;
		isCodecH264 = other.isCodecH264;
		isCodecH265 = other.isCodecH265;
	}

}
