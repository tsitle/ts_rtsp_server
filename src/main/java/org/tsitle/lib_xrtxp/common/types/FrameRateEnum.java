package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;

/**
 * Video frame rates.
 */
public enum FrameRateEnum {

	UNKNOWN(-1.0),

	FPS_1_0(1.0),
	FPS_5_0(5.0),
	FPS_10_0(10.0),
	FPS_12_0(12.0),
	FPS_15_0(15.0),
	FPS_20_0(20.0),
	FPS_23_976(23.976),
	FPS_24_0(24.0),
	FPS_25_0(25.0),
	FPS_29_97(29.97),
	FPS_30_0(30.0),
	FPS_48_0(48.0),
	FPS_50_0(50.0),
	FPS_59_94(59.94),
	FPS_60_0(60.0),
	FPS_72_0(72.0),
	FPS_75_0(75.0),
	FPS_90_0(90.0),
	FPS_100_0(100.0),
	FPS_120_0(120.0);

	private final double frDbl;

	FrameRateEnum(double frDbl) {
		this.frDbl = frDbl;
	}

	public double getFrDbl() {
		return frDbl;
	}

	public static @NonNull FrameRateEnum of(double value) {
		for (FrameRateEnum tmpEn : FrameRateEnum.values()) {
			if (tmpEn != UNKNOWN && compareDblFps(tmpEn.getFrDbl(), value)) {
				return tmpEn;
			}
		}
		return UNKNOWN;
	}

	private static boolean compareDblFps(double a, double b) {
		return Math.abs(a - b) < 0.000_1;
	}

}
