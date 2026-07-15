package org.tsitle.lib_xrtxp.common.helpers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RationalNumberTest {

	@Test
	void testConstructorEmpty() {
		RationalNumber rationalNumber = RationalNumber.ofEmpty();
		assertEquals(0, rationalNumber.getNumerator());
		assertEquals(1, rationalNumber.getDenominator());
	}

	@Test
	void testConstructorDef() {
		RationalNumber rationalNumber = RationalNumber.of(1, 2);
		assertEquals(1, rationalNumber.getNumerator());
		assertEquals(2, rationalNumber.getDenominator());
	}

	private record TestEntry(double valDbl, RationalNumber rn) { }

	@Test
	void testConstructorFps() {
		List<TestEntry> testEntries = new ArrayList<>() {{
			add(new TestEntry(0.999, RationalNumber.of(0, 1)));
			add(new TestEntry(1.0, RationalNumber.of(1, 1)));
			add(new TestEntry(-1.0, RationalNumber.of(-1, 1)));
			add(new TestEntry(-1.0, RationalNumber.of(1, -1)));
			add(new TestEntry(5.0, RationalNumber.of(5, 1)));
			add(new TestEntry(10.0, RationalNumber.of(10, 1)));
			add(new TestEntry(12.0, RationalNumber.of(12, 1)));
			add(new TestEntry(15.0, RationalNumber.of(15, 1)));
			add(new TestEntry(20.0, RationalNumber.of(20, 1)));
			add(new TestEntry(Double.parseDouble("23.976"), RationalNumber.of(24_000, 1_001)));
			add(new TestEntry(24.0, RationalNumber.of(24, 1)));
			add(new TestEntry(25.0, RationalNumber.of(25, 1)));
			add(new TestEntry(29.97, RationalNumber.of(30_000, 1_001)));
			add(new TestEntry(30.0, RationalNumber.of(30, 1)));
			add(new TestEntry(48.0, RationalNumber.of(48, 1)));
			add(new TestEntry(50.0, RationalNumber.of(50, 1)));
			add(new TestEntry(59.94, RationalNumber.of(60_000, 1_001)));
			add(new TestEntry(60.0, RationalNumber.of(60, 1)));
			add(new TestEntry(72.0, RationalNumber.of(72, 1)));
			add(new TestEntry(75.0, RationalNumber.of(75, 1)));
			add(new TestEntry(90.0, RationalNumber.of(90, 1)));
			add(new TestEntry(100.0, RationalNumber.of(100, 1)));
			add(new TestEntry(120.0, RationalNumber.of(120, 1)));

			add(new TestEntry(29.77, RationalNumber.of(2_977, 100)));
			add(new TestEntry(2.5, RationalNumber.of(2_500, 1_000)));
			add(new TestEntry(240.0, RationalNumber.of(240_000, 1_000)));
		}};

		for (TestEntry testEntry : testEntries) {
			RationalNumber actualRn = RationalNumber.ofFps(testEntry.valDbl);
			assertEquals(testEntry.valDbl >= 0.0, actualRn.isPositive(), "dbl: " + testEntry.valDbl);
			assertEquals(testEntry.rn.getNumerator(), actualRn.getNumerator(), "dbl: " + testEntry.valDbl);
			assertEquals(testEntry.rn.getDenominator(), actualRn.getDenominator(), "dbl: " + testEntry.valDbl);
			System.out.println(testEntry.rn);
		}
	}

	@Test
	void testConstructorTb() {
		List<TestEntry> testEntries = new ArrayList<>() {{
			add(new TestEntry(0.000_000_000_9, RationalNumber.of(0, 1)));

			add(new TestEntry(10.0, RationalNumber.of(10_000, 1_000)));
			add(new TestEntry(1.0, RationalNumber.of(1, 1)));
			add(new TestEntry(0.001, RationalNumber.of(1, 1_000)));
			add(new TestEntry(-0.001, RationalNumber.of(-1, 1_000)));
			add(new TestEntry(-0.001, RationalNumber.of(1, -1_000)));
			add(new TestEntry(0.000_001, RationalNumber.of(1, 1_000_000)));
			add(new TestEntry(0.000_000_001, RationalNumber.of(1, 1_000_000_000)));
			add(new TestEntry(1.0 / 1_000_000_000.0, RationalNumber.of(1, 1_000_000_000)));

			add(new TestEntry(1.0 / 8_000.0, RationalNumber.of(1, 8_000)));
			add(new TestEntry(1.0 / 11_025.0, RationalNumber.of(1, 11_025)));
			add(new TestEntry(1.0 / 12_000.0, RationalNumber.of(1, 12_000)));
			add(new TestEntry(1.0 / 16_000.0, RationalNumber.of(1, 16_000)));
			add(new TestEntry(1.0 / 22_050.0, RationalNumber.of(1, 22_050)));
			add(new TestEntry(1.0 / 24_000.0, RationalNumber.of(1, 24_000)));
			add(new TestEntry(1.0 / 32_000.0, RationalNumber.of(1, 32_000)));
			add(new TestEntry(1.0 / 44_100.0, RationalNumber.of(1, 44_100)));
			add(new TestEntry(1.0 / 48_000.0, RationalNumber.of(1, 48_000)));
			add(new TestEntry(1.0 / 88_200.0, RationalNumber.of(1, 88_200)));
			add(new TestEntry(1.0 / 96_000.0, RationalNumber.of(1, 96_000)));
			add(new TestEntry(1.0 / 176_400.0, RationalNumber.of(1, 176_400)));
			add(new TestEntry(1.0 / 192_000.0, RationalNumber.of(1, 192_000)));

			add(new TestEntry(1.0 / 11_908.0, RationalNumber.of(1, 11_908)));
		}};

		for (TestEntry testEntry : testEntries) {
			RationalNumber actualRn = RationalNumber.ofTimeBase(testEntry.valDbl);
			assertEquals(testEntry.valDbl >= 0.0, actualRn.isPositive(), "dbl: " + testEntry.valDbl);
			assertEquals(testEntry.rn.getNumerator(), actualRn.getNumerator(), "dbl: " + testEntry.valDbl);
			assertEquals(testEntry.rn.getDenominator(), actualRn.getDenominator(), "dbl: " + testEntry.valDbl);
			System.out.println(testEntry.rn.toString(9));
		}
	}

	@Test
	void testCmp() {
		RationalNumber rn1 = RationalNumber.ofEmpty();
		RationalNumber rn2 = RationalNumber.of(2, 4);
		assertEquals(-1, rn1.cmp(rn2));

		rn1 = RationalNumber.of(2, 4);
		rn2 = RationalNumber.ofEmpty();
		assertEquals(1, rn1.cmp(rn2));

		rn1 = RationalNumber.ofEmpty();
		rn2 = RationalNumber.ofEmpty();
		assertEquals(0, rn1.cmp(rn2));

		rn1 = RationalNumber.of(1, 2);
		rn2 = RationalNumber.of(2, 4);
		assertEquals(0, rn1.cmp(rn2));

		rn1 = RationalNumber.of(1, 5);
		rn2 = RationalNumber.of(2, 5);
		assertEquals(-1, rn1.cmp(rn2));

		rn1 = RationalNumber.of(2, 5);
		rn2 = RationalNumber.of(1, 5);
		assertEquals(1, rn1.cmp(rn2));
	}

}
