package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for PCMA/PCMU/LinearPCM.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3551">RFC-3551</a>
 */
public final class RtpPacketPcm extends RtpPacketCodecBase {

	public static class InnerHeaderData implements Cloneable {
		// there are no inner header fields

		@Override
		public @NonNull String toString() {
			return "no inner header fields";
		}

		@Override
		public @NonNull InnerHeaderData clone() {
			try {
				return (InnerHeaderData)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE = 0;

	private final InnerHeaderData hdInnData = new InnerHeaderData();

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param packetType RTP packet type (since there are several PCM types)
	 * @param pcmInfo PCM info
	 * @param payloadView Payload data view
	 */
	public RtpPacketPcm(
				@NonNull ParamsContainerBase paramsBase,
				@NonNull RtpPacketType packetType,
				@NonNull AudioPcmInfo pcmInfo,
				@NonNull BufferView payloadView
			) {
		super(packetType, paramsBase);

		//
		if (! packetType.isPcmAudio()) {
			throw new IllegalArgumentException("Invalid RTP packet type");
		}
		if (pcmInfo.samplesPerChannelInAudioData < 1 || pcmInfo.samplesPerChannelInAudioData > 0xFFFFFF ||
				(pcmInfo.bitsPerSample != 8 && pcmInfo.bitsPerSample != 16) ||
				pcmInfo.channels < 1 || pcmInfo.channels > AudioPcmInfo.AUDIO_CHANNELS_MAX) {
			throw new IllegalArgumentException("Cannot process this kind of PCM");
		}

		//
		updatePacket(paramsBase, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetType RTP packet type (since there are several PCM types)
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketPcm(@NonNull RtpPacketType packetType, @NonNull BufferExt packetData) {
		super(packetType, packetData);

		if (! packetType.isPcmAudio()) {
			throw new IllegalArgumentException("Invalid RTP packet type");
		}

		// determine the length of the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;

		// parse inner main header fields
		/* there are none */
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull InnerHeaderData getParsedInnerHeaderData() {
		return hdInnData.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				@NonNull BufferView payloadView
			) {
		updatePacketHeader(paramsBase);

		// set inner main header fields
		/* there are none */

		// build the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;
		/* there is none */

		// copy the inner payload bitstream
		this.packetBuf.copyFrom(
				payloadView.getInternalBaPtr(),
				payloadView.getOffset(),
				this.packetBuf.getUsed(),
				payloadView.getLength()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				super.toString(true) +
				", " + hdInnData.toString() +
				"]";
	}

}
