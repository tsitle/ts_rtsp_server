package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.PcmInfo;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload for PCMU/LinearPCM.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3551">RFC-3551</a>
 */
public class RtpPacketPcm extends RtpPacketCodecBase {

	/** Size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE = 0;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param packetType RTP packet type (since there are several PCM types)
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the PCM frame data) (24 bits)
	 * @param pcmInfo PCM info
	 * @param payloadData Payload data
	 */
	public RtpPacketPcm(
				@NonNull ParamsContainerBase paramsBase,
				@NonNull RtpPacketType packetType,
				int fragmentOffset,
				@NonNull PcmInfo pcmInfo,
				@NonNull BufferExt payloadData
			) {
		super(packetType, paramsBase);

		//
		if (! packetType.isPcmAudio()) {
			throw new IllegalArgumentException("Invalid RTP packet type");
		}
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (pcmInfo.samplesPerChannelInAudioData < 1 || pcmInfo.samplesPerChannelInAudioData > 0xFFFFFF ||
				(pcmInfo.bitsPerSample != 8 && pcmInfo.bitsPerSample != 16) ||
				pcmInfo.channels < 1 || pcmInfo.channels > 2) {
			throw new IllegalArgumentException("Cannot process this kind of PCM");
		}

		// set inner main header fields
		/* there are none */

		// build the inner header bitstream
		this.payloadSpecHeaderSize = 0;
		/* there is none */

		// copy the inner payload bitstream
		this.packetBuf.append(payloadData);
	}

	/**
	 * Constructor.
	 * @param packetType RTP packet type (since there are several PCM types)
	 * @param packetData RTP packet bitstream including header and payload
	 */
	public RtpPacketPcm(@NonNull RtpPacketType packetType, @NonNull BufferExt packetData) {
		super(packetType, packetData);

		if (! packetType.isPcmAudio()) {
			throw new IllegalArgumentException("Invalid RTP packet type");
		}

		// determine the length of the inner header bitstream (main header + optional QT header)
		this.payloadSpecHeaderSize = 0;

		// parse inner main header fields
		/* there are none */
	}

}
