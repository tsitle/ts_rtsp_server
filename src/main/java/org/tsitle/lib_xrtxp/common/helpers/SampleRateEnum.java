package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;

/**
 * Audio sample rates.
 */
public enum SampleRateEnum {

	UNKNOWN(-1),

	SR_008000(8000),
	SR_011025(11025),
	SR_012000(12000),
	SR_016000(16000),
	SR_022050(22050),
	SR_024000(24000),
	SR_032000(32000),
	SR_044100(44100),
	SR_048000(48000),
	SR_088200(88200),
	SR_096000(96000),
	SR_176400(176400),
	SR_192000(192000);

	private final int srHz;

	SampleRateEnum(int srHz) {
		this.srHz = srHz;
	}

	public int getSrHz() {
		return srHz;
	}

	public static @NonNull SampleRateEnum of(int value) {
		for (SampleRateEnum tmpEn : SampleRateEnum.values()) {
			if (tmpEn != UNKNOWN && tmpEn.getSrHz() == value) {
				return tmpEn;
			}
		}
		return UNKNOWN;
	}

}
