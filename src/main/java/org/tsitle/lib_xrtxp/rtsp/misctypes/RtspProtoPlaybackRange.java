package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.TimestampEpoch;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidPbRangeException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a playback range in the RTSP protocol (and SDP).
 */
public final class RtspProtoPlaybackRange implements Cloneable {

	private static final String PREFIX_CLOCK = "clock=";
	private static final String PREFIX_SMPTE = "smpte=";
	private static final String PREFIX_NPT = "npt=";

	private boolean isWriteProtected = false;

	private double absoluteSecsStart = -1.0;
	private double rangeSecsStart = -1.0;
	private double rangeSecsEnd = -1.0;

	private RtspProtoPlaybackRange() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspProtoPlaybackRange ofEmpty() {
		return new RtspProtoPlaybackRange();
	}

	public static @NonNull RtspProtoPlaybackRange ofAbsolute(@NonNull TimestampEpoch absoluteTimeStart) {
		return ofAbsolute(absoluteTimeStart, TimestampEpoch.ofEmpty());
	}

	public static @NonNull RtspProtoPlaybackRange ofAbsolute(
				@NonNull TimestampEpoch absoluteTimeStart,
				@NonNull TimestampEpoch absoluteTimeEnd
			) {
		if (absoluteTimeStart.isEmpty()) {
			throw new IllegalArgumentException(RtspProtoPlaybackRange.class.getSimpleName() + ".ofAbsolute(): " +
					"absoluteTimeStart must be set");
		}
		final double startAbsSecs = (double)absoluteTimeStart.getEpochNsUnsigned64bit().orElseThrow() / 1_000_000_000.0;
		double endAbsSecs = (absoluteTimeEnd.isEmpty() ?
				-1.0
				: (double)absoluteTimeEnd.getEpochNsUnsigned64bit().orElseThrow() / 1_000_000_000.0
			);
		if (endAbsSecs < startAbsSecs) {
			endAbsSecs = -1.0;
		}
		return ofAbsolute(startAbsSecs, 0.0, endAbsSecs >= 0.001 ? endAbsSecs - startAbsSecs : -1.0);
	}

	public static @NonNull RtspProtoPlaybackRange ofAbsolute(
				@NonNull TimestampEpoch absoluteTimeBase,
				double relStartSecs,
				double relEndSecs
			) {
		if (absoluteTimeBase.isEmpty()) {
			throw new IllegalArgumentException(RtspProtoPlaybackRange.class.getSimpleName() + ".ofAbsolute(): " +
					"absoluteTimeBase must be set");
		}
		final double startAbsSecs = (double)absoluteTimeBase.getEpochNsUnsigned64bit().orElseThrow() / 1_000_000_000.0;
		return ofAbsolute(startAbsSecs, relStartSecs, relEndSecs);
	}

	public static @NonNull RtspProtoPlaybackRange ofAbsolute(
				double absoluteTimeBaseSecs,
				double relStartSecs,
				double relEndSecs
			) {
		if (absoluteTimeBaseSecs < 0.001) {
			throw new IllegalArgumentException(RtspProtoPlaybackRange.class.getSimpleName() + ".ofAbsolute(): " +
					"absoluteTimeBaseSecs must be >= 0.001");
		}
		if (relStartSecs < 0.0) {
			throw new IllegalArgumentException(RtspProtoPlaybackRange.class.getSimpleName() + ".ofAbsolute(): " +
					"relStartSecs must be >= 0.0");
		}
		RtspProtoPlaybackRange res = new RtspProtoPlaybackRange();
		res.absoluteSecsStart = absoluteTimeBaseSecs;
		res.rangeSecsStart = relStartSecs;
		res.rangeSecsEnd = (relEndSecs < relStartSecs ? -1.0 : relEndSecs);
		return res;
	}

	public static @NonNull RtspProtoPlaybackRange ofRelative(double startSecs) {
		return ofRelative(startSecs, -1.0);
	}

	public static @NonNull RtspProtoPlaybackRange ofRelative(double startSecs, double endSecs) {
		if (startSecs < 0.0) {
			throw new IllegalArgumentException(RtspProtoPlaybackRange.class.getSimpleName() + ".ofRelative(): " +
					"startSecs must be >= 0.0");
		}
		RtspProtoPlaybackRange res = new RtspProtoPlaybackRange();
		res.rangeSecsStart = startSecs;
		res.rangeSecsEnd = (endSecs < startSecs ? -1.0 : endSecs);
		return res;
	}

	public static @NonNull RtspProtoPlaybackRange ofNowToInfinity() {
		return RtspProtoPlaybackRange.ofRelative(0.0, -1.0);
	}

	/**
	 * Parse a Playback Range string.
	 * @param str Input string (e.g., 'clock=19961108T143720.25Z-' or 'npt=123.45-125')
	 * @return Playback Range object
	 * @throws RtspProtoInvalidPbRangeException If the input string is invalid
	 */
	public static @NonNull RtspProtoPlaybackRange parseString(@NonNull String str) throws RtspProtoInvalidPbRangeException {
		final String FNC_NAME = RtspProtoPlaybackRange.class.getSimpleName() + ".parseString()";

		String lc = str.toLowerCase();
		if (lc.startsWith(PREFIX_CLOCK)) {
			str = str.substring(PREFIX_CLOCK.length());
			return parseAbsClockRangeString(str);
		}
		if (lc.startsWith(PREFIX_SMPTE)) {
			throw new RtspProtoInvalidPbRangeException(FNC_NAME + ": SMPTE is not supported");
		}
		if (lc.startsWith(PREFIX_NPT)) {
			str = str.substring(PREFIX_NPT.length());
			return parseNptRangeString(str);
		}
		throw new RtspProtoInvalidPbRangeException(FNC_NAME + ": Unknown prefix");
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Output the range in 'Absolute Time' format.
	 * @return Range in 'Absolute Time' format
	 */
	public Optional<String> toAbsClockString() {
		/*
		 * Absolute Time:
		 *    "Range: clock=19960213T143205Z-;time=19970123T143720Z"
		 *    "Range: clock=19961108T143720.25Z-"  (November 8, 1996 at 14h37 and 20 and a quarter seconds UTC)
		 */
		if (absoluteSecsStart < 0.001) {
			return Optional.empty();
		}
		return Optional.of(String.format("%s%s-%s",
				PREFIX_CLOCK,
				doubleToAbsClock(true, absoluteSecsStart, rangeSecsStart),
				doubleToAbsClock(false, absoluteSecsStart, rangeSecsEnd)
			));
	}

	// -------------------------------------------------------

	/**
	 * Output the range in 'SMPTE Relative Timestamps' format.<br />
	 * <b>Note:</b> this method is not yet implemented.
	 * @return Range in 'SMPTE Relative Timestamps' format
	 */
	@SuppressWarnings("UnusedReturnValue")
	public @NonNull String toSmpteString() {
		/*
		 * SMPTE Relative Timestamps:
		 * The SMPTE-relative format is a way to express media position using
		 * timecode style (hours/minutes/seconds/frames), rather than seconds (npt) or wall-clock UTC (clock).
		 *
		 * Basic format:
		 *   smpte[[-fps-variant]]=start[-end]
		 *
		 * Common variants:
		 *   'smpte=': default SMPTE format (historically 30 fps basis)
		 *   'smpte-25='
		 *   'smpte-30-drop='
		 * Timecode fields:
		 *   hh:mm:ss[:ff[.sf]]
		 *   hh = hours
		 *   mm = minutes
		 *   ss = seconds
		 *   ff = frame number within second
		 *   sf = subframe (fraction of frame, usually hundredths)
		 *
		 * Examples:
		 *    "Range: smpte=10:12:33:20-"
		 *    "Range: smpte=10:07:33-"
		 *    "Range: smpte=10:07:00-10:07:33:05.01"
		 *    "Range: smpte-25=10:07:00-10:07:33:05.01"
		 */
		throw new RuntimeException(getClass().getSimpleName() + ".toSmpteString(): Not implemented");
	}

	// -------------------------------------------------------

	/**
	 * Output the range in 'Normal Play Time' format with seconds and fraction of a second.
	 * @return Range in 'Normal Play Time' format
	 */
	public @NonNull String toNptString_secs() {
		/*
		 * Normal Play Time:
		 *    "Range: npt=123.45-"
		 *    "Range: npt=123.45-125"
		 *    "Range: npt=now-"
		 */
		return String.format("%s%s-%s",
				PREFIX_NPT,
				doubleToNpt_secs(true, rangeSecsStart),
				doubleToNpt_secs(false, rangeSecsEnd)
			);
	}

	/**
	 * Output the range in 'Normal Play Time' format with hours, minutes, seconds, and milliseconds.
	 * @return Range in 'Normal Play Time' format
	 */
	public @NonNull String toNptString_hoursMinutesSecsMs() {
		/*
		 * Normal Play Time:
		 *    "Range: npt=12:05:35.3-"
		 *    "Range: npt=12:05:35.3-14:07:21.8"
		 */
		return String.format("%s%s-%s",
				PREFIX_NPT,
				doubleToNpt_hoursMinutesSecsMs(true, rangeSecsStart),
				doubleToNpt_hoursMinutesSecsMs(false, rangeSecsEnd)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Double> getAbsoluteTimeStartSecsAsDouble() {
		if (absoluteSecsStart < 0.001) {
			return Optional.empty();
		}
		return Optional.of(absoluteSecsStart + rangeSecsStart);
	}

	public Optional<TimestampEpoch> getAbsoluteTimeStartAsTimestamp() {
		if (absoluteSecsStart < 0.001) {
			return Optional.empty();
		}
		TimestampEpoch epoch = TimestampEpoch.ofEpochMsUnsigned64bit((long)((absoluteSecsStart + rangeSecsStart) * 1_000.0));
		return Optional.of(epoch);
	}

	public Optional<Double> getAbsoluteTimeEndSecsAsDouble() {
		if (absoluteSecsStart < 0.001 || rangeSecsEnd < 0.001) {
			return Optional.empty();
		}
		return Optional.of(absoluteSecsStart + rangeSecsEnd);
	}

	public Optional<TimestampEpoch> getAbsoluteTimeEndAsTimestamp() {
		if (absoluteSecsStart < 0.001 || rangeSecsEnd < 0.001) {
			return Optional.empty();
		}
		TimestampEpoch epoch = TimestampEpoch.ofEpochMsUnsigned64bit((long)((absoluteSecsStart + rangeSecsEnd) * 1_000.0));
		return Optional.of(epoch);
	}

	// -------------------------------------------------------

	public double getRelativeTimeStartSecs() {
		return rangeSecsStart;
	}

	public Optional<Double> getRelativeTimeEndSecs() {
		if (rangeSecsEnd < 0.001) {
			return Optional.empty();
		}
		return Optional.of(rangeSecsEnd);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isAbsoluteTime() {
		return (absoluteSecsStart >= 0.0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		absoluteSecsStart = -1.0;
		rangeSecsStart = -1.0;
		rangeSecsEnd = -1.0;
	}

	public boolean isEmpty() {
		return (rangeSecsStart < 0.0);
	}

	public void copyFrom(@NonNull RtspProtoPlaybackRange other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		absoluteSecsStart = other.absoluteSecsStart;
		rangeSecsStart = other.rangeSecsStart;
		rangeSecsEnd = other.rangeSecsEnd;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public @NonNull RtspProtoPlaybackRange clone() {
		RtspProtoPlaybackRange cloned = new RtspProtoPlaybackRange();
		cloned.copyFrom(this);
		if (isWriteProtected) {
			cloned.writeProtect();
		}
		return cloned;
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoPlaybackRange that)) {
			return false;
		}
		return (Double.compare(absoluteSecsStart, that.absoluteSecsStart) == 0 &&
				Double.compare(rangeSecsStart, that.rangeSecsStart) == 0 &&
				Double.compare(rangeSecsEnd, that.rangeSecsEnd) == 0);
	}

	@Override
	public int hashCode() {
		return Objects.hash(absoluteSecsStart, rangeSecsStart, rangeSecsEnd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String doubleToAbsClock(boolean isStart, double absoluteSecsStart, double relSecs) {
		if (relSecs < 0.001 && ! isStart) {
			return "";
		}
		if (relSecs < 0.001) {
			relSecs = 0.0;
		}
		double absSecs = absoluteSecsStart + relSecs;
		TimestampEpoch epoch = TimestampEpoch.ofEpochMsUnsigned64bit((long)(absSecs * 1_000.0));
		return epochToAbsClockString(epoch);
	}

	private static @NonNull String epochToAbsClockString(@NonNull TimestampEpoch epoch) {
		if (epoch.isEmpty()) {
			return "";
		}
		//
		Instant tmpInst = epoch.toInstant().orElseThrow();
		ZonedDateTime tmpZdt = tmpInst.atZone(ZoneOffset.UTC);
		int fldYear = tmpZdt.getYear();
		int fldMonth = tmpZdt.getMonthValue();
		int fldDay = tmpZdt.getDayOfMonth();
		int fldHour = tmpZdt.getHour();
		int fldMinute = tmpZdt.getMinute();
		int fldSecond = tmpZdt.getSecond();
		int fldNano = tmpZdt.getNano();
		// 19961108T143720.25Z
		return String.format("%04d%02d%02dT%02d%02d%02d.%02dZ",
				fldYear, fldMonth, fldDay, fldHour, fldMinute, fldSecond, fldNano / 10_000_000);
	}

	// -------------------------------------------------------

	private static @NonNull String doubleToNpt_secs(boolean isStart, double secs) {
		if (secs < 0.001) {
			return (isStart ? "0" : "");
		}
		return String.format("%.3f", secs).replace(",", ".");
	}

	private static @NonNull String doubleToNpt_hoursMinutesSecsMs(boolean isStart, double secs) {
		if (secs < 0.001) {
			return (isStart ? "0:00:00.0" : "");
		}
		long durSecsAsLong = (long)secs;
		long remainderMs = (long)((secs - durSecsAsLong) * 1000);
		return String.format("%d:%02d:%02d.%03d",
				durSecsAsLong / 3600, (durSecsAsLong % 3600) / 60, durSecsAsLong % 60, remainderMs);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspProtoPlaybackRange parseAbsClockRangeString(@NonNull String str)
			throws RtspProtoInvalidPbRangeException {
		final String FNC_NAME = RtspProtoPlaybackRange.class.getSimpleName() + ".parseAbsClockRangeString()";

		/*
		 * "19960213T143205Z-;time=19970123T143720Z"
		 * "19961108T143720.25Z-"
		 */
		str = str.split(";")[0];  // discard any "time=xxx"
		String[] tmpSplit = str.split("-");
		if (tmpSplit.length < 1 || tmpSplit.length > 2) {
			throw new RtspProtoInvalidPbRangeException(FNC_NAME + ": Invalid range string: '" + str + "'");
		}

		RtspProtoPlaybackRange res = new RtspProtoPlaybackRange();
		res.absoluteSecsStart = parseAbsClockSingleString(FNC_NAME, tmpSplit[0].strip());
		res.rangeSecsStart = 0.0;
		if (tmpSplit.length == 2) {
			double tmpEndAbs = parseAbsClockSingleString(FNC_NAME, tmpSplit[1].strip());
			if (tmpEndAbs > 0.001) {
				res.rangeSecsEnd = tmpEndAbs - res.absoluteSecsStart;
			}
		}
		return res;
	}

	private static double parseAbsClockSingleString(@NonNull String fncName, @NonNull String str)
			throws RtspProtoInvalidPbRangeException {
		/*
		 * "19960213T143205Z"
		 * "19961108T143720.25Z"
		 */
		String[] tmpSplitT = str.split("T");
		if (tmpSplitT.length != 2) {
			throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
		}

		String tmpYearMonthDay = tmpSplitT[0].strip();
		if (tmpYearMonthDay.length() != 8) {
			throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
		}
		String tmpFldYear = tmpYearMonthDay.substring(0, 4);
		String tmpFldMonth = tmpYearMonthDay.substring(4, 6);
		String tmpFldDay = tmpYearMonthDay.substring(6, 8);

		String tmpTimeOfDay = tmpSplitT[1].strip().replace("Z", "");
		if (tmpTimeOfDay.length() < 6) {
			throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
		}
		String tmpFldHour = tmpTimeOfDay.substring(0, 2);
		String tmpFldMinute = tmpTimeOfDay.substring(2, 4);
		String tmpFldSecond = tmpTimeOfDay.substring(4, 6);
		String tmpFldFraction = (tmpTimeOfDay.length() > 6 ? tmpTimeOfDay.substring(7) : "0");

		String tmpIso = tmpFldYear + "-" + tmpFldMonth + "-" + tmpFldDay +
				"T" + tmpFldHour + ":" + tmpFldMinute + ":" + tmpFldSecond + "." + tmpFldFraction + "Z";
		Instant tmpInst = Instant.parse(tmpIso);
		TimestampEpoch tmpTimestamp = TimestampEpoch.ofInstant(tmpInst);
		return (double)tmpTimestamp.getEpochNsUnsigned64bit().orElseThrow() / 1_000_000_000.0;
	}

	// -------------------------------------------------------

	private static @NonNull RtspProtoPlaybackRange parseNptRangeString(@NonNull String str)
			throws RtspProtoInvalidPbRangeException {
		final String FNC_NAME = RtspProtoPlaybackRange.class.getSimpleName() + ".parseNptRangeString()";

		/*
		 * "123.45-"
		 * "123.45-125"
		 * "now-"
		 *
		 * "12:05:35.3-"
		 * "12:05:35.3-14:07:21.8"
		 */
		str = str.split(";")[0];  // discard any extra fields
		String[] tmpSplit = str.split("-");
		if (tmpSplit.length < 1 || tmpSplit.length > 2) {
			throw new RtspProtoInvalidPbRangeException(FNC_NAME + ": Invalid range string: '" + str + "'");
		}

		RtspProtoPlaybackRange res = new RtspProtoPlaybackRange();
		res.rangeSecsStart = parseNptSingleString(FNC_NAME, tmpSplit[0].strip());
		if (tmpSplit.length == 2) {
			res.rangeSecsEnd = parseNptSingleString(FNC_NAME, tmpSplit[1].strip());
			if (res.rangeSecsEnd < 0.001) {
				res.rangeSecsEnd = -1.0;
			}
		}
		return res;
	}

	private static double parseNptSingleString(@NonNull String fncName, @NonNull String str)
			throws RtspProtoInvalidPbRangeException {
		if (str.equalsIgnoreCase("now")) {
			return 0.0;
		}
		if (str.contains(":")) {
			/*
			 * "12:05:35.3"
			 * "3:05:35.003"
			 */
			if (str.length() < 7) {
				throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
			}
			String[] tmpSplit = str.split(":");
			if (tmpSplit.length != 3) {
				throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
			}
			try {
				int tmpFldHour = Integer.parseInt(tmpSplit[0]);
				int tmpFldMinute = Integer.parseInt(tmpSplit[1]);
				String tmpSecondAndFrac = tmpSplit[2];
				tmpSplit = tmpSecondAndFrac.split("\\.");
				if (tmpSplit.length < 1 || tmpSplit.length > 2) {
					throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
				}
				int tmpFldSecs = Integer.parseInt(tmpSplit[0]);
				int tmpFldFraction = (tmpSplit.length == 2 ? Integer.parseInt(tmpSplit[1]) : 0);

				return (tmpFldHour * 3600.0 + tmpFldMinute * 60.0 + tmpFldSecs + tmpFldFraction / 1000.0);
			} catch (NumberFormatException e) {
				throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
			}
		}
		/*
		 * "123.45"
		 * "125"
		 */
		try {
			return Double.parseDouble(str);
		} catch (NumberFormatException e) {
			throw new RtspProtoInvalidPbRangeException(fncName + ": Invalid range string: '" + str + "'");
		}
	}

}
