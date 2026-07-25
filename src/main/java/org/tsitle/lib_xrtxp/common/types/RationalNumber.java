package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Rational number.
 */
public final class RationalNumber {

	private boolean sign;
	private int num;
	private int den;

	private RationalNumber(boolean sign, int num, int den) {
		this.sign = sign;
		this.num = num;
		this.den = den;
	}

	public static @NonNull RationalNumber ofEmpty() {
		return new RationalNumber(true, 0, 1);
	}

	public static @NonNull RationalNumber of(int num, int den) {
		return ofSimplified(num, den);
	}

	public static @NonNull RationalNumber of(@NonNull RationalNumber other) {
		return ofSimplified(other.num, other.den);
	}

	public static @NonNull RationalNumber ofFps(double value) {
		if (Double.isInfinite(value) || Double.isNaN(value)) { return new RationalNumber(true, 0, 1); }

		boolean sign = (value >= 0.0);
		if (! sign) {
			value = -value;
		}

		if (value < 1.0) { return new RationalNumber(sign, 0, 1); }

		FrameRateEnum tmpFrEn = FrameRateEnum.of(value);
		switch (tmpFrEn) {
			case FPS_1_0 -> { return new RationalNumber(sign, 1, 1); }
			case FPS_5_0 -> { return new RationalNumber(sign, 5, 1); }
			case FPS_10_0 -> { return new RationalNumber(sign, 10, 1); }
			case FPS_12_0 -> { return new RationalNumber(sign, 12, 1); }
			case FPS_15_0 -> { return new RationalNumber(sign, 15, 1); }
			case FPS_20_0 -> { return new RationalNumber(sign, 20, 1); }
			case FPS_23_976 -> { return new RationalNumber(sign, 24_000, 1_001); }
			case FPS_24_0 -> { return new RationalNumber(sign, 24, 1); }
			case FPS_25_0 -> { return new RationalNumber(sign, 25, 1); }
			case FPS_29_97 -> { return new RationalNumber(sign, 30_000, 1_001); }
			case FPS_30_0 -> { return new RationalNumber(sign, 30, 1); }
			case FPS_48_0 -> { return new RationalNumber(sign, 48, 1); }
			case FPS_50_0 -> { return new RationalNumber(sign, 50, 1); }
			case FPS_59_94 -> { return new RationalNumber(sign, 60_000, 1_001); }
			case FPS_60_0 -> { return new RationalNumber(sign, 60, 1); }
			case FPS_72_0 -> { return new RationalNumber(sign, 72, 1); }
			case FPS_75_0 -> { return new RationalNumber(sign, 75, 1); }
			case FPS_90_0 -> { return new RationalNumber(sign, 90, 1); }
			case FPS_100_0 -> { return new RationalNumber(sign, 100, 1); }
			case FPS_120_0 -> { return new RationalNumber(sign, 120, 1); }
		}

		int tmpNum = (int)(value * 1_000.0);
		if (tmpNum < 1_000) { return new RationalNumber(sign, 0, 1); }
		return ofSimplified(tmpNum, 1_000);
	}

	public static @NonNull RationalNumber ofTimeBase(double value) {
		if (Double.isInfinite(value) || Double.isNaN(value)) { return new RationalNumber(true, 0, 1); }

		boolean sign = (value >= 0.0);
		if (! sign) {
			value = -value;
		}

		if (value < 0.000_000_001) { return new RationalNumber(sign, 0, 1); }
		if (compareDblTimeBase(value, 1.0)) { return new RationalNumber(sign, 1, 1); }
		if (compareDblTimeBase(value, 0.001)) { return new RationalNumber(sign, 1, 1_000); }
		if (compareDblTimeBase(value, 0.000_001)) { return new RationalNumber(sign, 1, 1_000_000); }
		if (compareDblTimeBase(value, 0.000_000_001)) { return new RationalNumber(sign, 1, 1_000_000_000); }
		if (compareDblTimeBase(value, 1.0 / 1_000_000_000.0)) { return new RationalNumber(sign, 1, 1_000_000_000); }

		for (SampleRateEnum tmpSrEn : SampleRateEnum.values()) {
			if (tmpSrEn != SampleRateEnum.UNKNOWN &&
					compareDblTimeBase(value, 1.0 / (double)tmpSrEn.getSrHz())) {
				return new RationalNumber(sign, 1, tmpSrEn.getSrHz());
			}
		}

		if (value < 1.0) {
			int tmpDen = (int)(1.0 / value);
			if (tmpDen < 1) { return new RationalNumber(sign, 0, 1); }
			return new RationalNumber(sign, 1, tmpDen);
		}
		int tmpNum = (int)(value * 1_000.0);
		if (tmpNum < 1) { return new RationalNumber(sign, 0, 1); }
		return ofSimplified(tmpNum, 1_000);
	}

	public void copyFrom(@NonNull RationalNumber other) {
		sign = other.sign;
		num = other.num;
		den = other.den;
	}

	public int getNumerator() {
		return num;
	}

	public int getDenominator() {
		return den;
	}

	public int cmp(@NonNull RationalNumber other) {
		if (this == other) { return 0; }
		if (! isValid() && other.isValid()) { return -1; }
		if (isValid() && ! other.isValid()) { return 1; }
		if (! isValid()) { return 0; }
		return Double.compare(toDouble(), other.toDouble());
	}

	public boolean isPositive() {
		return sign;
	}

	public boolean isValid() {
		return (num != 0 && den != 0);
	}

	public double toDouble() {
		if (den == 0) {
			return 0.0;
		}
		return ((double)num / (double)den) * (sign ? 1.0 : -1.0);
	}

	@Override
	public @NonNull String toString() {
		return toString(3);
	}

	public @NonNull String toString(int decimalPlaces) {
		String fmt = "%." + decimalPlaces + "f";
		return (sign ? "" : "-") + num + "/" + den + " (" +
				String.format(fmt, toDouble()).replace(",", ".") +
				")";
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RationalNumber that)) {
			return false;
		}
		return (sign == that.sign && num == that.num && den == that.den);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sign, num, den);
	}

	private static boolean compareDblTimeBase(double a, double b) {
		return Math.abs(a - b) < 0.000_000_000_1;
	}

	private static @NonNull RationalNumber ofSimplified(int num, int den) {
		boolean sign = ((num >= 0 && den >= 0) || (num < 0 && den < 0));
		if (num < 0) {
			num = -num;
		}
		if (den < 0) {
			den = -den;
		} else if (den == 0) {
			return new RationalNumber(true, 0, 1);
		}

		while (den % 10 == 0) {
			int tmpNum = num / 10;
			if (tmpNum * 10 != num) {
				break;
			}
			num /= 10;
			den /= 10;
		}

		return new RationalNumber(sign, num, den);
	}

}
