package org.tsitle.rtsp.threads.rtp.h265;

import java.util.ArrayList;
import java.util.List;

/**
 * An Access Unit normally contains:<br />
 *   - optional “leading” non‑VCL (e.g., VPS/SPS/PPS, prefix SEI),<br />
 *   - one or more VCL NALs that form the picture (multiple slices/tiles),<br />
 *   - optional “trailing” non‑VCL (e.g., suffix SEI).<br />
 * The RTP packet timestamp needs to be the same for all packets of an AU.<br />
 * The 'Last RTP packet of a Frame Bit' needs to be set on the last packet of an AU.
 */
public final class HevcAccessUnit {

	public final String AU_NAME;
	public int auTimestamp;
	public boolean isAuTimestampSet = false;
	public int arrNalUnitCount;
	public int arrNalUnitIx;
	public final List<HevcNalUnitData> arrNalUnitData = new ArrayList<>() {{
			for (int i = 0; i < 5; i++) {
				add(new HevcNalUnitData());
			}
		}};

	public HevcAccessUnit(String name) {
		this.AU_NAME = name;
	}

	public void reset() {
		auTimestamp = 0;
		isAuTimestampSet = false;
		arrNalUnitCount = 0;
		arrNalUnitIx = 0;
		for (HevcNalUnitData nalUnitData : arrNalUnitData) {
			nalUnitData.reset();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" +
				"name=" + AU_NAME +
				", timestamp=" + auTimestamp + " (" + (isAuTimestampSet ? "S" : "-") + ")" +
				", arrCount=" + arrNalUnitCount + " (sz=" + arrNalUnitData.size() + ")" +
				", arrIx=" + arrNalUnitIx +
				"]";
	}

}
