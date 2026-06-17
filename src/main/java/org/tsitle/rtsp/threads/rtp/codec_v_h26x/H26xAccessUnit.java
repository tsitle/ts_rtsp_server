package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.tsitle.rtsp.avdata.CodecInfoInterface;

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
final class H26xAccessUnit<I extends CodecInfoInterface<I>> {

	public final String AU_NAME;
	public int arrNalUnitCount;
	public int arrNalUnitIx;
	public final List<H26xNalUnitData<I>> arrNalUnitData = new ArrayList<>() {{
			for (int i = 0; i < 5; i++) {
				add(new H26xNalUnitData<>());
			}
		}};
	public long totalRtpPayloadSize = 0L;

	public H26xAccessUnit(String name) {
		this.AU_NAME = name;
	}

	public void reset() {
		arrNalUnitCount = 0;
		arrNalUnitIx = 0;
		for (H26xNalUnitData<I> nalUnitData : arrNalUnitData) {
			nalUnitData.reset();
		}
		totalRtpPayloadSize = 0L;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"name=" + AU_NAME +
				", arrCount=" + arrNalUnitCount + " (sz=" + arrNalUnitData.size() + ")" +
				", arrIx=" + arrNalUnitIx +
				", totalRtpPayloadSize=" + totalRtpPayloadSize +
				"]";
	}

}
