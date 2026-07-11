package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Rational number.
 */
public final class RationalNumber {

	private final boolean sign;
	private final int num;
	private final int den;

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

	public static @NonNull RationalNumber ofFps(double value) {
		if (Double.isInfinite(value) || Double.isNaN(value)) { return new RationalNumber(true, 0, 1); }

		boolean sign = (value >= 0.0);
		if (! sign) {
			value = -value;
		}

		if (value < 1.0) { return new RationalNumber(sign, 0, 1); }
		if (compareDblFps(value, 1.0)) { return new RationalNumber(sign, 1, 1); }
		if (compareDblFps(value, 5.0)) { return new RationalNumber(sign, 5, 1); }
		if (compareDblFps(value, 10.0)) { return new RationalNumber(sign, 10, 1); }
		if (compareDblFps(value, 12.0)) { return new RationalNumber(sign, 12, 1); }
		if (compareDblFps(value, 15.0)) { return new RationalNumber(sign, 15, 1); }
		if (compareDblFps(value, 20.0)) { return new RationalNumber(sign, 20, 1); }
		if (compareDblFps(value, 23.976)) { return new RationalNumber(sign, 24_000, 1_001); }
		if (compareDblFps(value, 24.0)) { return new RationalNumber(sign, 24, 1); }
		if (compareDblFps(value, 25.0)) { return new RationalNumber(sign, 25, 1); }
		if (compareDblFps(value, 29.77)) { return new RationalNumber(sign, 2_977, 100); }
		if (compareDblFps(value, 29.97)) { return new RationalNumber(sign, 30_000, 1_001); }
		if (compareDblFps(value, 30.0)) { return new RationalNumber(sign, 30, 1); }
		if (compareDblFps(value, 48.0)) { return new RationalNumber(sign, 48, 1); }
		if (compareDblFps(value, 50.0)) { return new RationalNumber(sign, 50, 1); }
		if (compareDblFps(value, 59.94)) { return new RationalNumber(sign, 19_001, 317); }
		if (compareDblFps(value, 60.0)) { return new RationalNumber(sign, 60, 1); }
		if (compareDblFps(value, 72.0)) { return new RationalNumber(sign, 72, 1); }
		if (compareDblFps(value, 75.0)) { return new RationalNumber(sign, 75, 1); }
		if (compareDblFps(value, 90.0)) { return new RationalNumber(sign, 90, 1); }
		if (compareDblFps(value, 100.0)) { return new RationalNumber(sign, 100, 1); }
		if (compareDblFps(value, 120.0)) { return new RationalNumber(sign, 120, 1); }
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

		if (compareDblTimeBase(value, 1.0 / 8_000.0)) { return new RationalNumber(sign, 1, 8_000); }
		if (compareDblTimeBase(value, 1.0 / 11_025.0)) { return new RationalNumber(sign, 1, 11_025); }
		if (compareDblTimeBase(value, 1.0 / 12_000.0)) { return new RationalNumber(sign, 1, 12_000); }
		if (compareDblTimeBase(value, 1.0 / 16_000.0)) { return new RationalNumber(sign, 1, 16_000); }
		if (compareDblTimeBase(value, 1.0 / 22_050.0)) { return new RationalNumber(sign, 1, 22_050); }
		if (compareDblTimeBase(value, 1.0 / 24_000.0)) { return new RationalNumber(sign, 1, 24_000); }
		if (compareDblTimeBase(value, 1.0 / 32_000.0)) { return new RationalNumber(sign, 1, 32_000); }
		if (compareDblTimeBase(value, 1.0 / 44_100.0)) { return new RationalNumber(sign, 1, 44_100); }
		if (compareDblTimeBase(value, 1.0 / 48_000.0)) { return new RationalNumber(sign, 1, 48_000); }
		if (compareDblTimeBase(value, 1.0 / 88_200.0)) { return new RationalNumber(sign, 1, 88_200); }
		if (compareDblTimeBase(value, 1.0 / 96_000.0)) { return new RationalNumber(sign, 1, 96_000); }
		if (compareDblTimeBase(value, 1.0 / 176_400.0)) { return new RationalNumber(sign, 1, 176_400); }
		if (compareDblTimeBase(value, 1.0 / 192_000.0)) { return new RationalNumber(sign, 1, 192_000); }

		if (value < 1.0) {
			int tmpDen = (int)(1.0 / value);
			if (tmpDen < 1) { return new RationalNumber(sign, 0, 1); }
			return new RationalNumber(sign, 1, tmpDen);
		}
		int tmpNum = (int)(value * 1_000.0);
		if (tmpNum < 1) { return new RationalNumber(sign, 0, 1); }
		return ofSimplified(tmpNum, 1_000);
	}

	public int getNumerator() {
		return num;
	}

	public int getDenominator() {
		return den;
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
		return (sign ? "" : "-") + num + "/" + den + " (" + toDouble() + ")";
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

	private static boolean compareDblFps(double a, double b) {
		return Math.abs(a - b) < 0.000_1;
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
