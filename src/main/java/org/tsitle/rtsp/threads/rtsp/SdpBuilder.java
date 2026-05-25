package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.packets.rtp.RtpPacketAac;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;

import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;

import static org.tsitle.rtsp.threads.rtsp.RtspPrivateConstants.*;

public class SdpBuilder {

	private static final String CRLF = "\r\n";
	private static final String SESSION_NAME = "Just A Session";

	private final RtspConfig rtspConfig;
	private final RtspSessionInfo rtspSessionInfo;

	public SdpBuilder(
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Builds an SDP response string<br />
	 * SDP: Session Description Protocol (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC4317: SDP Offer/Answer Examples</a>)
	 * @param rtspInputSource Input Source
	 * @param rtspHostIpOrName Server's host IP address or hostname
	 * @return SDP formatted string
	 */
	public @NonNull String buildSdp(
				@NonNull RtspInputSource rtspInputSource,
				@NonNull String rtspHostIpOrName
			) {
		StringWriter sw = new StringWriter();

		// SDP Specification (RFC2327 Section 6)
		// -------------------------------------
		// v: Protocol Version
		sw.write(String.format("v=0%s", CRLF));
		// o: Origin
		final String tmpO_Username = "-";
		final String tmpO_Id = "" + System.currentTimeMillis();
		final String tmpO_Version = "1";
		final String tmpO_NetworkType = "IN";
		final String tmpO_AddressType = "IP4";
		@SuppressWarnings("UnnecessaryLocalVariable")
		final String tmpO_UnicastAddress = rtspHostIpOrName;  // can be an IP address or a hostname
		sw.write(String.format("o=%s %s %s %s %s %s%s",
				tmpO_Username, tmpO_Id, tmpO_Version, tmpO_NetworkType, tmpO_AddressType, tmpO_UnicastAddress, CRLF));
		// s: Session Name
		sw.write(String.format("s=%s%s", SESSION_NAME, CRLF));
		// i: Session Information
		sw.write(String.format("i=%s%s", rtspInputSource.getId(), CRLF));
		// t: Time Active
		sw.write(String.format("t=0 0%s", CRLF));
		// a: Session Attribute: Name and version number of the tool used to create the session description
		String tmpSdpEnc = getSdpEncoderName();
		sw.write(String.format("a=tool:%s%s", tmpSdpEnc, CRLF));
		// a: Session Attribute: Type of the conference
		sw.write(String.format("a=type:broadcast%s", CRLF));
		// a: Session Attribute: URL to be used for controlling that particular media stream (RFC7826 Section D.1.1)
		sw.write(String.format("a=control:*%s", CRLF));
		// a: Session Attribute: Range of presentation (RFC7826 Section D.1.6)
		sw.write(String.format("a=range:npt=0-%s", CRLF));

		// -------------------------------------
		// optional Video Stream
		buildSdpForSubstream(rtspInputSource, true, sw);
		// optional Audio Stream
		buildSdpForSubstream(rtspInputSource, false, sw);

		return sw.toString().substring(0, sw.toString().length() - CRLF.length());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String getSdpEncoderName() {
		String tmpAppVersion = System.getProperty(RtspPrivateConstants.SYSPROP_CSTM_APP_VERSION);
		if (tmpAppVersion == null) {
			tmpAppVersion = "0.0";
		}
		return SERVER_NAME + " " + tmpAppVersion;
	}

	private void buildSdpForSubstream(
				RtspInputSource rtspInputSource,
				boolean useVideo,
				StringWriter sw
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdpForSubstream()";

		Optional<RtspStreamSource> optSsObj =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), useVideo);
		if (optSsObj.isEmpty()) {
			return;
		}
		RtspStreamSource tmpSsObj = optSsObj.get();

		// create the Sub-Stream ID ('Input Stream and Stream Source' combination)
		Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
		final String outputSubStreamId = RtspStaticSessionInfo.addSubStream(
				rtspSessionInfo.clientIpAddr,
				rtspInputSource.getId(),
				tmpSsObj.getId()
			);

		//
		final String sdpCodecName;
		try {
			sdpCodecName = tmpSsObj.getCodec().getSdpCodecName();
		} catch (IllegalStateException e) {
			throw new IllegalStateException(FNC_NAME + ": " + e.getMessage());
		}

		final int videoRtpClockRate;
		try {
			videoRtpClockRate = (useVideo ? tmpSsObj.getCodec().getVideoCodecRtpClockrate() : 0);
		} catch (IllegalStateException e) {
			throw new IllegalStateException(FNC_NAME + ": " + e.getMessage());
		}

		// m: Media Description with available codec(s)
		boolean isEncrRequ = (
				(! rtspSessionInfo.isRtspsConnection && rtspSessionInfo.isRtpRtcpEncryptionRequired) ||
				rtspSessionInfo.forceRtpRtcpEncryption
			);
		final int tmpM_port = 0;
		sw.write(String.format("m=%s %d RTP/%sAVP %d%s",
				(useVideo ? "video" : "audio"), tmpM_port, isEncrRequ ? "S" : "",
				tmpSsObj.getCodec().getValue(), CRLF));
		// c: Connection Information (can be an IP address or a hostname)
		//sw.write(String.format("c=IN IP4 0.0.0.0%s", CRLF));
		//
		if (tmpSsObj.getCodec().isPcmAudio()) {
			if (tmpSsObj.getCodec().getPcmAudioBitsPerSample().isPresent()) {
				// b: Bandwidth Information
				sw.write(String.format("b=AS:%d%s",
						tmpSsObj.getAudioChannelCount() * tmpSsObj.getAudioSamplerateHz() *
								tmpSsObj.getCodec().getPcmAudioBitsPerSample().get(),
						CRLF));
			}
		}
		if (tmpSsObj.getCodec().isAudio() && tmpSsObj.getIsSourceFromFile()) {
			/*
			 * a: Session Attribute: Packetization interval (in milliseconds)
			 *    Length of time in milliseconds represented by the media in a packet.
			 *    This is probably only meaningful for audio data. It should not be necessary
			 *    to know ptime to decode RTP or vat audio, and it is intended
			 *    as a recommendation for the encoding/packetisation of audio.
			 */
			double tmpTimeMs;
			if (tmpSsObj.getCodec() == RtpPacketType.A_AAC) {
				final double tmpFrameDurAacSecs = ((double)tmpSsObj.getAacSamplesPerFrame() /
						(double)tmpSsObj.getAudioSamplerateHz());
				tmpTimeMs = tmpFrameDurAacSecs * 1000.0;
			} else {
				tmpTimeMs = RtspConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS;
			}
			sw.write(String.format("a=ptime:%.5f%s", tmpTimeMs, CRLF).replace(",", "."));
		}
		//
		if (useVideo && tmpSsObj.getIsSourceFromFile()) {
			// a: Session Attribute: video framerate
			sw.write(String.format("a=framerate:%.2f%s", tmpSsObj.getVideoFps(), CRLF).replace(",", "."));
		}
		// a: Session Attribute: map the codec number from the 'm' attribute to an actual codec and its clock rate
		final String tmpA_Map = sdpCodecName +
				"/" +
				(useVideo ? videoRtpClockRate : tmpSsObj.getAudioSamplerateHz()) +
				(useVideo ? "" : "/" + tmpSsObj.getAudioChannelCount());
		sw.write(String.format("a=rtpmap:%d %s%s", tmpSsObj.getCodec().getValue(), tmpA_Map, CRLF));
		//
		switch (tmpSsObj.getCodec()) {
			case RtpPacketType.A_AAC:
				sw.write(
						String.format(
								"a=fmtp:%d " +
								"streamtype=%d;" +  // required: ISO/IEC 14496-1 'streamType'
								"profile-level-id=%d;" +  // required: e.g. AAC-LC Level 4
								"mode=AAC-hbr;" +  // required: High Bit Rate mode: One or more complete AAC frames per RTP packet; each frame described by AU headers
								"config=%s;" +  // required: AudioSpecificConfig, encoded as hex
								"SizeLength=%d;" +  // optional: each RTP AU header contains a 13-bit size field describing the size (in bytes) of the AAC frame
								"IndexLength=%d;" +  // optional: identifies the order of Access Units within an RTP packet
								"IndexDeltaLength=%d;" +  // optional: used when multiple AUs are packed in a packet, defaults to 0
								"constantDuration=%d" +  // optional: 512/960/1024 samples per frame
								"%s",
								tmpSsObj.getCodec().getValue(),
								IsoIec14496_1_StreamType.AUDIOSTREAM.value,
								IsoIec14496_3_AudioProfilesAndLevels.HQ_LEV2.value,
								tmpSsObj.getAacAudioSpecificConfigHexStr(),
								RtpPacketAac.HEADER_FLD_SIZE_LENGTH_BITS,
								RtpPacketAac.HEADER_FLD_INDEX_LENGTH_BITS,
								RtpPacketAac.HEADER_FLD_INDEXDELTA_LENGTH_BITS,
								tmpSsObj.getAacSamplesPerFrame(),
								CRLF
					));
				break;
			case RtpPacketType.V_H264:
				sw.write(
						String.format(
								"a=fmtp:%d " +
								"packetization-mode=%d" +
								"%s",
								tmpSsObj.getCodec().getValue(),
								H26xPacketizationMode.NON_INTERLEAVED.value,
								CRLF
					));
				break;
		}
		// a: Session Attribute: URL to be used for controlling that particular media stream (RFC7826 Section D.1.1)
		sw.write(String.format("a=control:%s%s%s", STREAM_ID_PREFIX, outputSubStreamId, CRLF));

		// ----------------------------------------
		// create or update the StreamInfo object
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.clientIpAddr,
				outputSubStreamId,
				RandomHelper.getRandomUint32(false)
			);
		// create and send crypto parameters
		if (isEncrRequ) {
			addCryptoParams(sw, tmpStreamKmds);
		}
	}

	private void addCryptoParams(StringWriter sw, RtspStaticSessionInfo.StreamKmds streamKmds) {
		final String FNC_NAME = getClass().getSimpleName() + ".addCryptoParams()";

		// @TODO Test with a different GStreamer version -- doesn't work with 1.24.11
		// @TODO I did submit a Merge Request (#11629) to GStreamer to fix the issue with MIKEY
		boolean useLegacySdes = (! rtspSessionInfo.clientUserAgent.isBlank() &&
				rtspSessionInfo.clientUserAgent.startsWith("Lavf"));
		if (useLegacySdes) {
			/*
			 * FFplay ignores the transports RTP/AVP and RTP/SAVP and only looks for the 'a=crypto' line.
			 * Similarly, it will always request RTP/AVP transport in the SETUP request.
			 */
			streamKmds.kmdOutbound = SrtxpKmd.createForLegacySdes(streamKmds.rtspSsrcId);
		} else {
			streamKmds.kmdOutbound = SrtxpKmd.createWithDefaults(streamKmds.rtspSsrcId);
		}
		//System.out.println(">>>>>>>>>>>>>>>> " + streamInfo.streamKmds.kmdOutbound);
		try {
			if (! useLegacySdes) {
				// modern MIKEY key management
				String tmpMsg = MikeyGenerator.generate(streamKmds.kmdOutbound);
				sw.write(String.format("a=key-mgmt:mikey %s%s", tmpMsg, CRLF));
			} else {
				// legacy SDES key management (SDP Security Descriptions RFC-4568)
				org.tsitle.rtsp.buffers.BufferExt tmpKmdMkMsBe = new org.tsitle.rtsp.buffers.BufferExt();
				tmpKmdMkMsBe.append(streamKmds.kmdOutbound.masterKey());
				tmpKmdMkMsBe.append(streamKmds.kmdOutbound.masterSalt());
				byte[] tmpBa = new byte[tmpKmdMkMsBe.getUsed()];
				tmpKmdMkMsBe.copyInto(0, tmpBa, 0, tmpKmdMkMsBe.getUsed());
				String tmpSdesB64 = java.util.Base64.getEncoder().encodeToString(tmpBa);
				sw.write(String.format("a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:%s",  // only MasterKey and MasterSalt
						tmpSdesB64));

				//sw.write("|2^20");  // key lifetime

				//long tmpMkiVal = streamKmds.kmdOutbound.mkiAsLong();
				//int tmpMkiLen = streamKmds.kmdOutbound.mkiLen();
				//sw.write(String.format("|%d:%d", tmpMkiVal, tmpMkiLen)); // MKI

				sw.write(CRLF);
			}
		} catch (SrtxpSecurityException e) {
			throw new IllegalStateException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}
	}

}
