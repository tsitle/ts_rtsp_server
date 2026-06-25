package org.tsitle.lib_xrtxp.rtsp.sdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.helpers.NtpTimestamp;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.kmd.MikeyGenerator;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdStreamSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoDescribeRespSrtxpTypeDeciderInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpProducerInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpConstants;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Producer for Session Description Protocol (SDP) messages (according to RFC-2327 Section 6).
 */
public final class RtspProtoSdpProducer implements RtspProtoSdpProducerInterface {

	private final @NonNull String cfgServerNameAndVersion;
	private final @NonNull String cfgContentLanguage;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @Nullable RtspProtoDescribeRespSrtxpTypeDeciderInterface srtxpKmdsTypeDeciderInterface;

	/**
	 * Constructor.
	 * @param cfgServerNameAndVersion Server's software name and version
	 * @param cfgContentLanguage Content language (can be empty)
	 * @param availableStreamsInterface Available streams instance
	 * @param globalSessionInfoInterface Global session info instance
	 * @param srtxpKmdsTypeDeciderInterface SRTxP KMDs type decider instance (can be null)
	 */
	public RtspProtoSdpProducer(
				@NonNull String cfgServerNameAndVersion,
				@NonNull String cfgContentLanguage,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoDescribeRespSrtxpTypeDeciderInterface srtxpKmdsTypeDeciderInterface
			) {
		if (cfgServerNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgServerNameAndVersion cannot be blank");
		}
		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.cfgContentLanguage = cfgContentLanguage;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.srtxpKmdsTypeDeciderInterface = srtxpKmdsTypeDeciderInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void buildSdpForDescribe(
				@NonNull String cfgSubStreamIdPrefix,
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataCntSdpRaw outputSdp,
				@NonNull RtspProtoAdSettingsStream outputAdStreamSett,
				@NonNull RtspProtoKmdsStream outputKmdsOutbound
			) throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdpForDescribe()";

		internalBuildSdp(
				FNC_NAME,
				true,
				cfgSubStreamIdPrefix,
				requireSrtp,
				idInputSource,
				serverIpOrName,
				clientUserAgent,
				clientIpAddr,
				outputKmdsOutbound,
				outputAdStreamSett,
				outputSdp
			);
	}

	@Override
	public void buildUpdatedSdpForAnnounce(
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoAdSettingsStream inputAdStreamSett,
				@Nullable RtspProtoKmdsStream inputKmdsOutbound,
				@NonNull RtspProtoDataCntSdpRaw outputSdp
			) throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildUpdatedSdpForAnnounce()";

		/*
		 * Re-keying legacy SDES keys:
		 * we need to send an ANNOUNCE request that contains the entire SDP.
		 * Only the 'a=crypto' line must change and use a different tag.
		 * The initial SDP would contain something like 'a=crypto:1 ...' and the new SDP
		 * would contain something like 'a=crypto:2 ...'.
		 */

		internalBuildSdp(
				FNC_NAME,
				false,
				"",
				requireSrtp,
				idInputSource,
				serverIpOrName,
				clientUserAgent,
				clientIpAddr,
				inputKmdsOutbound,
				inputAdStreamSett,
				outputSdp
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalBuildSdp(
				@NonNull String fncName,
				boolean isForDescribe,
				@NonNull String cfgSubStreamIdPrefixForDescribe,
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@Nullable RtspProtoKmdsStream ioKmdsOutbound,
				@NonNull RtspProtoAdSettingsStream ioAdStreamSett,
				@NonNull RtspProtoDataCntSdpRaw outputSdp
			) throws RtspProtoSdpException {
		if (isForDescribe) {
			ioAdStreamSett.clear();
		}

		//
		RtspProtoInputSource inputSourceObj;
		try {
			inputSourceObj = availableStreamsInterface.getInputSourceObj(idInputSource);
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			throw new RtspProtoSdpException(fncName + ": Input Source not found");
		}
		if (! checkStreamsForInputSource(inputSourceObj)) {
			throw new RtspProtoSdpException(fncName + ": No valid Stream Source found for Input Source '" +
					idInputSource.getIdStr().orElse("-unset-") + "'");
		}

		//
		if (isForDescribe) {
			ioAdStreamSett.setIdInputSource(idInputSource);
		}

		//
		List<@NonNull String> tmpSdpLines;
		tmpSdpLines = buildSdpLines(
				isForDescribe,
				requireSrtp,
				cfgSubStreamIdPrefixForDescribe,
				inputSourceObj,
				serverIpOrName,
				clientUserAgent,
				clientIpAddr,
				ioAdStreamSett,
				ioKmdsOutbound
			);
		outputSdp.addAllSdpLinesAllRaw(tmpSdpLines);
		if (! cfgContentLanguage.isBlank()) {
			outputSdp.setContentLang(cfgContentLanguage);
		}

		//
		ioAdStreamSett.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkStreamsForInputSource(@NonNull RtspProtoInputSource inputSourceObj) {
		Optional<RtspProtoStreamSource> optSsObjVideo =
				availableStreamsInterface.getFirstVideoStreamSourceObj(inputSourceObj.getIdInputSource());
		Optional<RtspProtoStreamSource> optSsObjAudio =
				availableStreamsInterface.getFirstAudioStreamSourceObj(inputSourceObj.getIdInputSource());
		return (optSsObjVideo.isPresent() || optSsObjAudio.isPresent());
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull List<@NonNull String> buildSdpLines(
				boolean isForDescribe,
				boolean requireSrtp,
				@NonNull String cfgSubStreamIdPrefixForDescribe,
				@NonNull RtspProtoInputSource inputSourceObj,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoAdSettingsStream ioAdStreamSett,
				@Nullable RtspProtoKmdsStream ioKmdsOutbound
			) throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdpLines()";

		if (serverIpOrName.isEmpty()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Server IP address must be set");
		}
		if (inputSourceObj.getIdInputSource().getIdStr().isEmpty()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Input Source's ID must be set");
		}

		List<@NonNull String> resL = new ArrayList<>();

		// -------------------------------------
		// v: Protocol Version
		resL.add("v=0");
		// o: Origin
		final String tmpO_Username = "-";
		final String tmpO_Id = Long.toUnsignedString(NtpTimestamp.ofNow().getTsAsUnsigned64bit().orElseThrow());
		final String tmpO_Version = Long.toUnsignedString(NtpTimestamp.ofNow().getTsAsUnsigned64bit().orElseThrow());
		final String tmpO_NetworkType = "IN";
		final String tmpO_AddressType = "IP4";
		final String tmpO_UnicastAddress = serverIpOrName.getIpAddrStr().orElseThrow();  // can be an IP address or a hostname
		resL.add(String.format("o=%s %s %s %s %s %s",
				tmpO_Username, tmpO_Id, tmpO_Version, tmpO_NetworkType,
				tmpO_AddressType, tmpO_UnicastAddress));
		// s: Session Name
		resL.add(String.format("s=%s", RtspProtoSdpPrivateConstants.SESSION_NAME));
		// i: Session Information
		resL.add(String.format("i=%s", inputSourceObj.getIdInputSource().getIdStr().orElseThrow()));
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
		try {
			// optional Video Stream
			buildSdpForSubStream(
					isForDescribe,
					requireSrtp,
					cfgSubStreamIdPrefixForDescribe,
					inputSourceObj,
					clientUserAgent,
					clientIpAddr,
					ioKmdsOutbound,
					ioAdStreamSett,
					true,
					resL
				);
			// optional Audio Stream
			buildSdpForSubStream(
					isForDescribe,
					requireSrtp,
					cfgSubStreamIdPrefixForDescribe,
					inputSourceObj,
					clientUserAgent,
					clientIpAddr,
					ioKmdsOutbound,
					ioAdStreamSett,
					false,
					resL
				);
		} catch (RtspProtoIdStreamSourceNotFoundException e) {
			throw new RtspProtoSdpException(FNC_NAME + ": Stream Source ID not found: " + e.getMessage());
		}

		return resL;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildSdpForSubStream(
				boolean isForDescribe,
				boolean requireSrtp,
				@NonNull String cfgSubStreamIdPrefixForDescribe,
				@NonNull RtspProtoInputSource inputSourceObj,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@Nullable RtspProtoKmdsStream ioKmdsOutbound,
				@NonNull RtspProtoAdSettingsStream ioAdStreamSett,
				boolean useVideo,
				@NonNull List<@NonNull String> outputList
			) throws RtspProtoSdpException, RtspProtoIdStreamSourceNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdpForSubStream()";

		Optional<RtspProtoStreamSource> tmpOptSsObj;
		if (useVideo) {
			tmpOptSsObj = availableStreamsInterface.getFirstVideoStreamSourceObj(inputSourceObj.getIdInputSource());
		} else {
			tmpOptSsObj = availableStreamsInterface.getFirstAudioStreamSourceObj(inputSourceObj.getIdInputSource());
		}
		if (tmpOptSsObj.isEmpty()) {
			return;
		}
		RtspProtoStreamSource ssObj = tmpOptSsObj.get();
		final RtspProtoIdStreamSource ssId = ssObj.getIdStreamSource();

		//
		final RtspProtoIdSubStream tmpOutSubStreamId;
		final long tmpOutRtspSsrcIdLong;
		Optional<RtspProtoAdSettingsForSubStream> tmpInpAdSubStreamSetts = ioAdStreamSett.getSettingsByStreamSourceId(ssId);
		if (tmpInpAdSubStreamSetts.isPresent()) {
			tmpOutSubStreamId = tmpInpAdSubStreamSetts.get().idSubStream;
			if (tmpOutSubStreamId.isEmpty()) {
				throw new RtspProtoSdpException("Sub-Stream ID must be set in AdSettingsForSubStream");
			}
			if (tmpInpAdSubStreamSetts.get().ssrcOutbound.isEmpty()) {
				throw new RtspProtoSdpException("SSRC must be set in AdSettingsForSubStream");
			}
			tmpOutRtspSsrcIdLong = tmpInpAdSubStreamSetts.get().ssrcOutbound.getId32bit().orElse(-1L);
		} else if (! isForDescribe) {
			throw new RtspProtoSdpException("Stream Source ID must be set in AdSettingsForSubStream");
		} else {  // only DESCRIBE
			// create the Sub-Stream ID ('Input Stream and Stream Source' combination)
			tmpOutSubStreamId = globalSessionInfoInterface.createSubStreamId(
					cfgSubStreamIdPrefixForDescribe,
					inputSourceObj.getIdInputSource(),
					ssId,
					clientIpAddr
				);
			// generate SSRC ID
			tmpOutRtspSsrcIdLong = Integer.toUnsignedLong(RandomHelper.getRandomUint32(false));
		}
		tmpOutSubStreamId.writeProtect();

		//
		final String tmpSdpControlIdForSubStream = tmpOutSubStreamId.getIdStr().orElseThrow();

		//
		RtspProtoIdXsrc tmpOutRtspSsrcIdObj;
		try {
			tmpOutRtspSsrcIdObj = RtspProtoIdXsrc.of(tmpOutRtspSsrcIdLong);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
			tmpOutRtspSsrcIdObj = RtspProtoIdXsrc.ofEmpty();
		}

		//
		RtspProtoAdSettingsForSubStream settSubStream = new RtspProtoAdSettingsForSubStream();
		if (isForDescribe) {
			settSubStream.idStreamSource.copyFrom(ssId);
			settSubStream.idSubStream.copyFrom(tmpOutSubStreamId);
			settSubStream.ssrcOutbound.copyFrom(tmpOutRtspSsrcIdObj);
			settSubStream.setUrlSubPathForSubStream(tmpSdpControlIdForSubStream);
		}

		// ----------------------------------------

		buildSdpForSubStream_output(
				requireSrtp,
				useVideo,
				ssId,
				tmpSdpControlIdForSubStream,
				outputList
			);

		// ----------------------------------------

		if (requireSrtp) {
			if (ioKmdsOutbound == null) {
				throw new RtspProtoSdpException(FNC_NAME + ": ioKmdsOutbound is null");
			}
			SrtxpKmd kmdOutboundForSs;
			boolean isNewKmd = false;
			if (! ioKmdsOutbound.containsKmdForSubStreamId(tmpOutSubStreamId)) {
				kmdOutboundForSs = generateKmdsOutbound(clientUserAgent, tmpOutRtspSsrcIdObj);
				isNewKmd = true;
			} else {
				kmdOutboundForSs = ioKmdsOutbound.getKmdBySubStreamId(tmpOutSubStreamId).orElseThrow();
			}
			// add crypto parameters to SDP output
			addCryptoParams(outputList, kmdOutboundForSs);
			//
			if (isNewKmd) {
				ioKmdsOutbound.putKmdForSubStream(kmdOutboundForSs, tmpOutSubStreamId);
			}
		}

		// ----------------------------------------

		if (isForDescribe) {
			ioAdStreamSett.putSettingsForSubStream(settSubStream);
		}
	}

	private @NonNull SrtxpKmd generateKmdsOutbound(@NonNull String clientUserAgent, @NonNull RtspProtoIdXsrc ssrcId) {
		final boolean useLegacySdesForSrtxpKmds = (srtxpKmdsTypeDeciderInterface != null &&
				srtxpKmdsTypeDeciderInterface.useLegacySdesForSrtxpKmds(clientUserAgent));
		if (! useLegacySdesForSrtxpKmds) {
			// @TODO The current GStreamer version 1.24.11 is buggy and does not propagate the MKI to the SRTxP decoder.
			// @TODO Try to fix this in GStreamer once my pending Merge Request (#11629) for the Auth Key length issue is accepted.
			boolean isForBuggyGstreamer = clientUserAgent.startsWith("GStreamer");
			if (isForBuggyGstreamer) {
				return SrtxpKmd.createForMikeyWithCustomKeySizes(
						SrtxpKmd.DEFAULT_ENCR_KEY_LEN,
						SrtxpKmd.DEFAULT_AUTH_KEY_LEN,
						SrtxpKmd.DEFAULT_AUTH_TAG_LEN,
						SrtxpMki.ofEmpty(),  // <-- no MKI
						ssrcId
					);
			}
			return SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.of(1L, SrtxpKmd.DEFAULT_MKI_LEN), ssrcId);
		}
		/*
		 * FFplay ignores the transports RTP/AVP and RTP/SAVP and only looks for the 'a=crypto' line.
		 * Similarly, it will always request RTP/AVP transport in the SETUP request.
		 */
		return SrtxpKmd.createForLegacySdesWithDefaults(1);
	}

	private void buildSdpForSubStream_output(
				boolean requireSrtp,
				boolean useVideo,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull String sdpControlIdForSubStream,
				@NonNull List<@NonNull String> outputList
			) throws RtspProtoSdpException, RtspProtoIdStreamSourceNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSdpForSubStream_output()";

		final RtspProtoAvailableStreamsInterface.StreamSourceInfo ssInfo = availableStreamsInterface.getStreamSourceInfo(idStreamSource);
		final int ssVideoRtpClockRate;
		try {
			ssVideoRtpClockRate = (useVideo ? ssInfo.codec().getVideoCodecRtpClockrate() : 0);
		} catch (IllegalStateException e) {
			throw new RtspProtoSdpException(FNC_NAME + ": " + e.getMessage());
		}

		// m: Media Description with available codec(s)
		final int tmpM_port = 0;
		outputList.add(String.format("m=%s %d %s %d",
				(useVideo ? RtspProtoSdpMediaType.VIDEO.name() : RtspProtoSdpMediaType.AUDIO.name()).toLowerCase(),
				tmpM_port,
				(requireSrtp ? RtspProtoSdpTransport.RTP_SAVP : RtspProtoSdpTransport.RTP_AVP).getStrValue(),
				ssInfo.codec().getValue()));
		// c: Connection Information (can be an IP address or a hostname)
		//outputList.add("c=IN IP4 0.0.0.0");
		//
		if (ssInfo.codec().isPcmAudio() && ssInfo.codec().getPcmAudioBitsPerSample().isPresent()) {
			int tmpBw = (ssInfo.audioChannelCount() * ssInfo.audioSampleRateHz() *
					ssInfo.codec().getPcmAudioBitsPerSample().get());
			// b: Bandwidth Information
			outputList.add(String.format("b=AS:%d", tmpBw));
		}
		if (ssInfo.codec().isAudio() && ssInfo.isSourceFromFile()) {
			/*
			 * a: Session Attribute: Packetization interval (in milliseconds)
			 *    Length of time in milliseconds represented by the media in a packet.
			 *    This is probably only meaningful for audio data. It should not be necessary
			 *    to know ptime to decode RTP or vat audio, and it is intended
			 *    as a recommendation for the encoding/packetisation of audio.
			 */
			double tmpTimeMs;
			if (ssInfo.codec() == RtpPacketType.A_AAC) {
				final double tmpFrameDurAacSecs = ((double)ssInfo.audioAacSpf() /
						(double)ssInfo.audioSampleRateHz());
				tmpTimeMs = tmpFrameDurAacSecs * 1000.0;
			} else {
				tmpTimeMs = RtspProtoSdpConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS;
			}
			outputList.add(
					String.format("a=ptime:%.5f", tmpTimeMs).replace(",", ".")
				);
		}
		//
		if (useVideo && ssInfo.isSourceFromFile()) {
			// a: Session Attribute: video framerate
			outputList.add(
					String.format("a=framerate:%.2f", ssInfo.videoFps()).replace(",", ".")
				);
		}
		// a: Session Attribute: map the codec number from the 'm' attribute to an actual codec and its clock rate
		final String tmpA_Map = ssInfo.codec().getSdpCodecName() +
				"/" +
				(useVideo ? ssVideoRtpClockRate : ssInfo.audioSampleRateHz()) +
				(useVideo ? "" : "/" + ssInfo.audioChannelCount());
		outputList.add(String.format("a=rtpmap:%d %s", ssInfo.codec().getValue(), tmpA_Map));
		//
		switch (ssInfo.codec()) {
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
								ssInfo.codec().getValue(),
								RtspProtoSdpPrivateConstants.IsoIec14496_1_StreamType.AUDIOSTREAM.value,
								RtspProtoSdpPrivateConstants.IsoIec14496_3_AudioProfilesAndLevels.HQ_LEV2.value,
								ssInfo.audioAacHexCfg(),
								RtspProtoSdpConstants.AAC_HEADER_FLD_SIZE_LENGTH_BITS,
								RtspProtoSdpConstants.AAC_HEADER_FLD_INDEX_LENGTH_BITS,
								RtspProtoSdpConstants.AAC_HEADER_FLD_INDEXDELTA_LENGTH_BITS,
								ssInfo.audioAacSpf()
					));
				break;
			case RtpPacketType.V_H264:
				outputList.add(
						String.format(
								"a=fmtp:%d " +
								"packetization-mode=%d",
								ssInfo.codec().getValue(),
								RtspProtoSdpPrivateConstants.H26xPacketizationMode.NON_INTERLEAVED.value
					));
				break;
		}
		// a: Session Attribute: URL to be used for controlling that particular media stream (RFC-7826 Section D.1.1)
		outputList.add(
				String.format("a=control:%s", sdpControlIdForSubStream)
			);
	}

	private void addCryptoParams(
				@NonNull List<@NonNull String> outputList,
				@NonNull SrtxpKmd kmdOutboundForSs
			) throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCryptoParams()";

		//System.out.println(">>>>>>>>>>>>>>>> " + kmdOutboundForSs);
		try {
			if (! kmdOutboundForSs.getMetaIsForLegacySdes()) {
				/*
				 * modern MIKEY key management
				 */
				String tmpMsg = MikeyGenerator.generate(kmdOutboundForSs);
				outputList.add(String.format("a=key-mgmt:mikey %s", tmpMsg));
			} else {
				/*
				 * legacy SDES key management (SDP Security Descriptions RFC-4568)
				 */
				String tmpSdesB64 = kmdOutboundForSs.getMasterKeyAndSaltAsBase64();

				String tmpOutpLine = String.format("a=crypto:%s AES_CM_128_HMAC_SHA1_80 inline:%s",  // only MasterKey and MasterSalt
						Integer.toUnsignedString(kmdOutboundForSs.getMetaTagForLegacySdes().orElse(1)), tmpSdesB64);

				//tmpOutpLine += String.format("|%s",  // key lifetime, format "2^DIGITS" or "INTEGER" (not supported by Lavf)
						//Long.toUnsignedString(kmdOutboundForSs.kdr().getValue()));

				//tmpOutpLine += String.format("|%s:%d",  // MKI, format "MKI_value:MKI_length_bytes" (not supported by Lavf)
						//Long.toUnsignedString(kmdOutboundForSs.mki().getValue()),
						//kmdOutboundForSs.mki().getSizeBytes());

				outputList.add(tmpOutpLine);
			}
		} catch (SrtxpSecurityException e) {
			throw new RtspProtoSdpException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}
	}

}
