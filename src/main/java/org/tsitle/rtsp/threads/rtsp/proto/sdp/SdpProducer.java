package org.tsitle.rtsp.threads.rtsp.proto.sdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.packets.rtp.RtpPacketAac;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.security.DynInteger;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.RtspStaticSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspKeymgmtKmdsOutbound;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SdpProducer implements SdpProducerInterface {

	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull String cfgServerNameAndVersion;
	private final @NonNull RtspSessionInfo rtspSessionInfo;

	public SdpProducer(
				@NonNull RtspConfig rtspConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		if (cfgServerNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgServerNameAndVersion cannot be blank");
		}
		this.rtspConfig = rtspConfig;
		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void buildSdpForDescribe(
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull String serverIpOrName,
				@NonNull RtspProtoDataCntSdp outputSdp
			) throws RtspSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdp()";

		RtspInputSource rtspInputSource = rtspSessionInfo.getInputSourceObjForMt_nonSetup(RtspMessageType.DESCRIBE)
				.orElseThrow(() -> new RtspSdpException(FNC_NAME + ": Input Source not found"));
		if (! checkStreamsForInputSource(rtspInputSource)) {
			throw new RtspSdpException(FNC_NAME + ": No valid Stream Source found for Input Source '" +
					rtspInputSource.getId() + "'");
		}

		String sdpContentLanguage = "";  // @TODO set Content-Language

		List<@NonNull String> tmpSdpLines;
		tmpSdpLines = buildSdpLines(rtspInputSource, serverIpOrName);
		outputSdp.addAllSdpLinesAllRaw(tmpSdpLines);
		outputSdp.setContentLang(sdpContentLanguage);
	}

	public void buildUpdatedSdpForAnnounce(
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull String serverIpOrName,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@NonNull RtspProtoDataCntSdp outputSdp
			) throws RtspSdpException {
		// @TODO implement
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkStreamsForInputSource(@NonNull RtspInputSource rtspInputSource) {
		Optional<RtspStreamSource> optSsObjVideo =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), true);
		Optional<RtspStreamSource> optSsObjAudio =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), false);
		return (optSsObjVideo.isPresent() || optSsObjAudio.isPresent());
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Builds an SDP response.<br />
	 * SDP: Session Description Protocol (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param rtspInputSource Input Source
	 * @param serverIpOrName Server's IP address or hostname
	 * @return SDP lines
	 * @throws RtspSdpException If an error occurs during SDP generation
	 */
	private @NonNull List<@NonNull String> buildSdpLines(
				@NonNull RtspInputSource rtspInputSource,
				@NonNull String serverIpOrName
			) throws RtspSdpException {
		List<@NonNull String> resL = new ArrayList<>();

		// SDP Specification (RFC-2327 Section 6)
		// -------------------------------------
		// v: Protocol Version
		resL.add("v=0");
		// o: Origin
		final String tmpO_Username = "-";
		final String tmpO_Id = "" + System.currentTimeMillis();
		final String tmpO_Version = "1";
		final String tmpO_NetworkType = "IN";
		final String tmpO_AddressType = "IP4";
		@SuppressWarnings("UnnecessaryLocalVariable")
		final String tmpO_UnicastAddress = serverIpOrName;  // can be an IP address or a hostname
		resL.add(String.format("o=%s %s %s %s %s %s",
				tmpO_Username, tmpO_Id, tmpO_Version, tmpO_NetworkType,
				tmpO_AddressType, tmpO_UnicastAddress));
		// s: Session Name
		resL.add(String.format("s=%s", SdpConstants.SESSION_NAME));
		// i: Session Information
		resL.add(String.format("i=%s", rtspInputSource.getId()));
		// t: Time Active
		resL.add("t=0 0");
		// a: Session Attribute: Name and version number of the tool used to create the session description
		resL.add(String.format("a=tool:%s", cfgServerNameAndVersion));
		// a: Session Attribute: Type of the conference
		resL.add("a=type:broadcast");
		// a: Session Attribute: URL to be used for controlling that particular media stream (RFC-7826 Section D.1.1)
		resL.add("a=control:*");
		// a: Session Attribute: Range of presentation (RFC-7826 Section D.1.6)
		resL.add("a=range:npt=0-");

		// -------------------------------------
		// optional Video Stream
		buildSdpForSubStream(rtspInputSource, true, resL);
		// optional Audio Stream
		buildSdpForSubStream(rtspInputSource, false, resL);

		return resL;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildSdpForSubStream(
				@NonNull RtspInputSource rtspInputSource,
				boolean useVideo,
				@NonNull List<@NonNull String> outputList
			) throws RtspSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdpForSubStream()";

		Optional<RtspStreamSource> optSsObj =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), useVideo);
		if (optSsObj.isEmpty()) {
			return;
		}
		RtspStreamSource tmpSsObj = optSsObj.get();

		// create the Sub-Stream ID ('Input Stream and Stream Source' combination)
		final String outputSubStreamId = RtspStaticSessionInfo.addSdpSubStream(
				getClientIpAddr(),
				rtspInputSource.getId(),
				tmpSsObj.getId()
			);

		//
		final String sdpCodecName;
		try {
			sdpCodecName = tmpSsObj.getCodec().getSdpCodecName();
		} catch (IllegalStateException e) {
			throw new RtspSdpException(FNC_NAME + ": " + e.getMessage());
		}

		final int videoRtpClockRate;
		try {
			videoRtpClockRate = (useVideo ? tmpSsObj.getCodec().getVideoCodecRtpClockrate() : 0);
		} catch (IllegalStateException e) {
			throw new RtspSdpException(FNC_NAME + ": " + e.getMessage());
		}

		// m: Media Description with available codec(s)
		boolean isEncrRequ = (
				(! rtspSessionInfo.isRtspsConnection && rtspSessionInfo.isRtpRtcpEncryptionRequired) ||
				rtspSessionInfo.forceRtpRtcpEncryption
			);
		final int tmpM_port = 0;
		outputList.add(String.format("m=%s %d RTP/%sAVP %d",
				(useVideo ? "video" : "audio"), tmpM_port, isEncrRequ ? "S" : "",
				tmpSsObj.getCodec().getValue()));
		// c: Connection Information (can be an IP address or a hostname)
		//outputList.add("c=IN IP4 0.0.0.0");
		//
		if (tmpSsObj.getCodec().isPcmAudio()) {
			if (tmpSsObj.getCodec().getPcmAudioBitsPerSample().isPresent()) {
				// b: Bandwidth Information
				outputList.add(String.format("b=AS:%d",
						tmpSsObj.getAudioChannelCount() * tmpSsObj.getAudioSamplerateHz() *
								tmpSsObj.getCodec().getPcmAudioBitsPerSample().get()));
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
				tmpTimeMs = SdpConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS;
			}
			outputList.add(
					String.format("a=ptime:%.5f", tmpTimeMs).replace(",", ".")
				);
		}
		//
		if (useVideo && tmpSsObj.getIsSourceFromFile()) {
			// a: Session Attribute: video framerate
			outputList.add(
					String.format("a=framerate:%.2f", tmpSsObj.getVideoFps()).replace(",", ".")
				);
		}
		// a: Session Attribute: map the codec number from the 'm' attribute to an actual codec and its clock rate
		final String tmpA_Map = sdpCodecName +
				"/" +
				(useVideo ? videoRtpClockRate : tmpSsObj.getAudioSamplerateHz()) +
				(useVideo ? "" : "/" + tmpSsObj.getAudioChannelCount());
		outputList.add(String.format("a=rtpmap:%d %s", tmpSsObj.getCodec().getValue(), tmpA_Map));
		//
		switch (tmpSsObj.getCodec()) {
			case RtpPacketType.A_AAC:
				outputList.add(
						String.format(
								"a=fmtp:%d " +
								"streamtype=%d;" +  // required: ISO/IEC 14496-1 'streamType'
								"profile-level-id=%d;" +  // required: e.g. AAC-LC Level 4
								"mode=AAC-hbr;" +  // required: High Bit Rate mode: One or more complete AAC frames per RTP packet; each frame described by AU headers
								"config=%s;" +  // required: AudioSpecificConfig, encoded as hex
								"SizeLength=%d;" +  // optional: each RTP AU header contains a 13-bit size field describing the size (in bytes) of the AAC frame
								"IndexLength=%d;" +  // optional: identifies the order of Access Units within an RTP packet
								"IndexDeltaLength=%d;" +  // optional: used when multiple AUs are packed in a packet, defaults to 0
								"constantDuration=%d",  // optional: 512/960/1024 samples per frame
								tmpSsObj.getCodec().getValue(),
								SdpConstants.IsoIec14496_1_StreamType.AUDIOSTREAM.value,
								SdpConstants.IsoIec14496_3_AudioProfilesAndLevels.HQ_LEV2.value,
								tmpSsObj.getAacAudioSpecificConfigHexStr(),
								RtpPacketAac.HEADER_FLD_SIZE_LENGTH_BITS,
								RtpPacketAac.HEADER_FLD_INDEX_LENGTH_BITS,
								RtpPacketAac.HEADER_FLD_INDEXDELTA_LENGTH_BITS,
								tmpSsObj.getAacSamplesPerFrame()
					));
				break;
			case RtpPacketType.V_H264:
				outputList.add(
						String.format(
								"a=fmtp:%d " +
								"packetization-mode=%d",
								tmpSsObj.getCodec().getValue(),
								SdpConstants.H26xPacketizationMode.NON_INTERLEAVED.value
					));
				break;
		}
		// a: Session Attribute: URL to be used for controlling that particular media stream (RFC-7826 Section D.1.1)
		outputList.add(
				String.format("a=control:%s%s", RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX, outputSubStreamId)
			);

		// ----------------------------------------
		// create or update the StreamKmds object
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				getClientIpAddr(),
				outputSubStreamId,
				RandomHelper.getRandomUint32(false)
			);
		// create and send crypto parameters
		if (isEncrRequ) {
			addCryptoParams(outputList, tmpStreamKmds);
		}
	}

	private void addCryptoParams(
				@NonNull List<@NonNull String> outputList,
				RtspStaticSessionInfo.@NonNull StreamKmds streamKmds
			) throws RtspSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCryptoParams()";

		streamKmds.isForLegacySdes = (! rtspSessionInfo.clientUserAgent.isBlank() &&
				rtspSessionInfo.clientUserAgent.startsWith("Lavf"));
		if (! streamKmds.isForLegacySdes) {
			// @TODO The current GStreamer version 1.24.11 is buggy and does not propagate the MKI to the SRTxP decoder.
			// @TODO Try to fix this in GStreamer once my pending Merge Request (#11629) for the Auth Key length issue is accepted.
			boolean isForBuggyGstreamer = rtspSessionInfo.clientUserAgent.startsWith("GStreamer");
			if (isForBuggyGstreamer) {
				streamKmds.kmdOutbound = SrtxpKmd.createWithCustomKeySizes(
						false,
						SrtxpKmd.DEFAULT_ENCR_KEY_LEN,
						SrtxpKmd.DEFAULT_AUTH_KEY_LEN,
						SrtxpKmd.DEFAULT_AUTH_TAG_LEN,
						DynInteger.createEmpty(),  // <-- no MKI
						streamKmds.rtspSsrcId
					);
			} else {
				streamKmds.kmdOutbound = SrtxpKmd.createWithDefaults(1L, streamKmds.rtspSsrcId);
			}
		} else {
			/*
			 * FFplay ignores the transports RTP/AVP and RTP/SAVP and only looks for the 'a=crypto' line.
			 * Similarly, it will always request RTP/AVP transport in the SETUP request.
			 */
			streamKmds.kmdOutbound = SrtxpKmd.createForLegacySdes(streamKmds.rtspSsrcId);
		}
		//System.out.println(">>>>>>>>>>>>>>>> " + streamInfo.streamKmds.kmdOutbound);
		try {
			if (! streamKmds.isForLegacySdes) {
				/*
				 * modern MIKEY key management
				 */
				String tmpMsg = MikeyGenerator.generate(streamKmds.kmdOutbound);
				outputList.add(String.format("a=key-mgmt:mikey %s", tmpMsg));
			} else {
				/*
				 * legacy SDES key management (SDP Security Descriptions RFC-4568)
				 */
				String tmpSdesB64 = streamKmds.kmdOutbound.getMasterKeyAndSaltAsBase64();

				int tmpCryptoSdesTag = 1;  // @TODO increment per ANNOUNCE request

				String tmpOutpLine = String.format("a=crypto:%s AES_CM_128_HMAC_SHA1_80 inline:%s",  // only MasterKey and MasterSalt
						Integer.toUnsignedString(tmpCryptoSdesTag), tmpSdesB64);

				//tmpOutpLine += String.format("|%s",  // key lifetime, format "2^DIGITS" (not supported by Lavf)
						//RtspConstants.SRTXP_REKEYING_INTERVAL_PACKETS_EXP2_STR);

				//tmpOutpLine += String.format("|%s:%d",  // MKI, format "MKI_value:MKI_length_bytes" (not supported by Lavf)
						//Long.toUnsignedString(streamKmds.kmdOutbound.mki().value()),
						//streamKmds.kmdOutbound.mki().sizeBytes());

				outputList.add(tmpOutpLine);
			}
		} catch (SrtxpSecurityException e) {
			throw new RtspSdpException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull InetAddress getClientIpAddr() throws RtspSdpException {
		return rtspSessionInfo.getClientIpAddr()
				.orElseThrow(() -> new RtspSdpException("Client IP address is not set"));
	}

}
