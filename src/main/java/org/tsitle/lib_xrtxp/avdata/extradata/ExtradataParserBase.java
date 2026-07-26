package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

import java.util.*;

public abstract class ExtradataParserBase {

	protected ExtradataParserBase() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected byte[] parseHexStringToBytes(@NonNull String extradataHex) {
		if (extradataHex.length() < 2) {
			return null;
		}
		byte[] extradataBa;
		try {
			extradataBa = HexFormat.of().parseHex(extradataHex);
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (extradataBa == null || extradataBa.length == 0) {
			return null;
		}

		return extradataBa;
	}

	protected abstract @NonNull String parseExtradataBytes(byte[] extradata);

	// -----------------------------------------------------------------------------------------------------------------

	protected static @NonNull String encodeListAsB64String_h26xNalUnits(@NonNull List<byte[]> nalUnitList) {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (byte[] nalUnit : nalUnitList) {
			if (! isFirst) {
				sb.append(",");
			}
			isFirst = false;
			sb.append(Base64.getEncoder().encodeToString(nalUnit));
		}
		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected static int findStartCode_h26x(byte[] d, int from) {
		for (int i = Math.max(0, from); i + 3 < d.length; i++) {
			if (d[i] == 0 && d[i + 1] == 0) {
				if (d[i + 2] == 1) {
					return i;
				}
				if (i + 3 < d.length && d[i + 2] == 0 && d[i + 3] == 1) {
					return i;
				}
			}
		}
		return -1;
	}

	protected static int startCodeLen_h26x(byte[] d, int pos) {
		if (pos + 3 < d.length && d[pos] == 0 && d[pos + 1] == 0 && d[pos + 2] == 0 && d[pos + 3] == 1) return 4;
		return 3;
	}

}
