package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInputSourceIdNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSubStreamIdNotFoundException;
import org.tsitle.rtsp.threads.rtsp.RtspStaticSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.net.InetAddress;
import java.util.Optional;

final class ResourceUrlProcessor {

	private ResourceUrlProcessor() { }

	static void extractSubStreamId(@NonNull ResourceUrlParsingVars resourceUrlParsingVars) {
		/*
		 * Extract the Sub-Stream ID from the URL.
		 * For DESCRIBE/PLAY/PAUSE/TEARDOWN requests, the Resource URL needs to contain only the Input Source ID (== SDP name):
		 *   rtsp://localhost:1051/movie.sdp
		 * For SETUP/GET_PARAMETER/SET_PARAMETER requests, the Resource URL can contain the Input Source ID and the Sub-Stream ID:
		 *   rtsp://localhost:1051/movie.sdp/substreamid1234
		 * or it only contains the Sub-Stream ID:
		 *   rtsp://localhost:1051/substreamid1234
		 *
		 * The output for "/substreamid1234" would be "1234"
		 */
		String tmpPath = resourceUrlParsingVars.rscUrlPathOrg;
		int tmpIdxA = tmpPath.lastIndexOf("/" + RtspProtoConstants.SUBSTREAM_ID_PREFIX);
		if (tmpIdxA > 0) {
			// the Resource URL Path contains the Input Source ID and the Sub-Stream ID
			resourceUrlParsingVars.subStreamId = tmpPath
					.substring(tmpIdxA + 1 + RtspProtoConstants.SUBSTREAM_ID_PREFIX.length());
			resourceUrlParsingVars.rscUrlPathMod = resourceUrlParsingVars.rscUrlPathMod.substring(0, tmpIdxA);
		} else if (tmpPath.startsWith(RtspProtoConstants.SUBSTREAM_ID_PREFIX)) {
			// the Resource URL Path contains only the Sub-Stream ID
			resourceUrlParsingVars.subStreamId = tmpPath.substring(RtspProtoConstants.SUBSTREAM_ID_PREFIX.length());
		}
	}

	static void findStreamSourceObj(
				@NonNull ResourceUrlParsingVars resourceUrlParsingVars,
				@NonNull InetAddress clientIpAddr,
				@NonNull RtspConfig rtspConfig
			) throws RtspSubStreamIdNotFoundException {
		Optional<RtspStaticSessionInfo.SdpSubStreamInfo> tmpSdpSubStream = RtspStaticSessionInfo.getSdpSubStream(
				clientIpAddr,
				resourceUrlParsingVars.subStreamId
			);
		if (tmpSdpSubStream.isEmpty()) {
			throw new RtspSubStreamIdNotFoundException("Sub-Stream ID='" + resourceUrlParsingVars.subStreamId + "'");
		}
		//
		resourceUrlParsingVars.streamSourceObjPtr = rtspConfig.getStreamSourceObj(tmpSdpSubStream.get().streamSourceId())
				.orElse(null);
		if (resourceUrlParsingVars.streamSourceObjPtr == null) {  // sanity check
			throw new RtspSubStreamIdNotFoundException("Non-existing Stream Source ID in Sub-Stream ID " +
					"'" + resourceUrlParsingVars.subStreamId + "'");
		}
	}

	static void createSetupSubStreamRecord(
				@NonNull ResourceUrlParsingVars resourceUrlParsingVars,
				@NonNull InetAddress clientIpAddr
			) throws RtspInvalidUriException, RtspSubStreamIdNotFoundException {
		if (resourceUrlParsingVars.subStreamId.isBlank()) {  // sanity check
			throw new RtspInvalidUriException("Missing Sub-Stream ID in URL path: '" +
					resourceUrlParsingVars.rscUrlPathOrg + "'");
		}
		if (resourceUrlParsingVars.streamSourceObjPtr == null) {  // sanity check
			throw new RtspSubStreamIdNotFoundException("Missing Stream Source object");
		}
		//
		final String tmpErrMsgSsid = resourceUrlParsingVars.subStreamId;
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getStreamKmds(
				clientIpAddr,
				resourceUrlParsingVars.subStreamId
			).orElseThrow(() -> new RtspInvalidUriException("No StreamKmds for Sub-Stream ID '" + tmpErrMsgSsid + "'"));
		RtspStaticSessionInfo.addSetupSubStream(
				resourceUrlParsingVars.subStreamId,
				tmpStreamKmds,
				resourceUrlParsingVars.streamSourceObjPtr,
				resourceUrlParsingVars.fullRscUrl
			);
	}

	static RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource buildRuiossForSetupRequest(
				@NonNull ResourceUrlParsingVars resourceUrlParsingVars,
				@NonNull InetAddress clientIpAddr
			) throws RtspInvalidUriException {
		if (resourceUrlParsingVars.subStreamId.isBlank()) {  // sanity check
			throw new RtspInvalidUriException("Missing Sub-Stream ID in URL path: '" +
					resourceUrlParsingVars.rscUrlPathOrg + "'");
		}

		Optional<RtspStaticSessionInfo.SdpSubStreamInfo> tmpSdpSubStream = RtspStaticSessionInfo.getSdpSubStream(
				clientIpAddr,
				resourceUrlParsingVars.subStreamId
			);
		RtspRequestBasics.RequestUrlInputOrStreamSource resObj = new RtspRequestBasics.RequestUrlInputOrStreamSource();
		resObj.subStreamId = resourceUrlParsingVars.subStreamId;
		resObj.inputSourceId = tmpSdpSubStream.orElseThrow().inputSourceId();
		resObj.streamSourceId = tmpSdpSubStream.orElseThrow().streamSourceId();
		return resObj;
	}

	static void storeSubStreamAndStreamSourceIdsForNonSetupRequests(
				@NonNull ResourceUrlParsingVars resourceUrlParsingVars,
				@NonNull InetAddress clientIpAddr,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource outputRuioss
			) {
		if ((resourceUrlParsingVars.requestType != RtspMessageType.GET_PARAMETER &&
					resourceUrlParsingVars.requestType != RtspMessageType.SET_PARAMETER) ||
				resourceUrlParsingVars.subStreamId.isBlank()) {
			return;
		}
		Optional<RtspStaticSessionInfo.SdpSubStreamInfo> tmpSdpSubStream = RtspStaticSessionInfo.getSdpSubStream(
				clientIpAddr,
				resourceUrlParsingVars.subStreamId
			);

		outputRuioss.subStreamId = resourceUrlParsingVars.subStreamId;
		outputRuioss.streamSourceId = tmpSdpSubStream.orElseThrow().streamSourceId();
	}

	static @NonNull RtspInputSource findInputSourceObjectForNonSetupRequests(
				@NonNull ResourceUrlParsingVars resourceUrlParsingVars,
				@NonNull RtspConfig rtspConfig
			) throws RtspInputSourceIdNotFoundException {
		if (resourceUrlParsingVars.rscUrlPathMod.endsWith("/")) {
			resourceUrlParsingVars.rscUrlPathMod = resourceUrlParsingVars.rscUrlPathMod
					.substring(0, resourceUrlParsingVars.rscUrlPathMod.length() - 1);
		}
		Optional<RtspInputSource> optInputSource = rtspConfig.getInputSourceObj(resourceUrlParsingVars.rscUrlPathMod);
		if (optInputSource.isEmpty()) {
			throw new RtspInputSourceIdNotFoundException("URL path: '" + resourceUrlParsingVars.rscUrlPathMod + "'");
		}
		if (! optInputSource.get().getEnabled()) {
			throw new RtspInputSourceIdNotFoundException("Disabled Input Source used in URL path: '" +
					resourceUrlParsingVars.rscUrlPathMod + "'");
		}
		return optInputSource.get();
	}

}
