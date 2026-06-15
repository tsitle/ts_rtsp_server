package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdInputSourceNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdSubStreamNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;

import java.net.URI;

final class ResourceUrlProcessorNg {

	/** Full Resource URL (e.g. 'rtsp://localhost:1051/movie.stream/substreamid1234') */
	private final @NonNull String fullRscUrlStr;

	private @NonNull String rscUrlStrPathOrg = "";
	private @NonNull String rscUrlStrPathMod = "";
	private @NonNull String subStreamIdStr = "";

	private ResourceUrlProcessorNg(@NonNull String fullRscUrlStr) {
		this.fullRscUrlStr = fullRscUrlStr;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspProtoRscUrl parseUrlIntoRscUrlObject(
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull String fullRscUrlStr,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspInvalidUriException, RtspIdSubStreamNotFoundException, RtspIdInputSourceNotFoundException {
		RtspProtoRscUrl resObj = new RtspProtoRscUrl();
		resObj.setUrlStr(fullRscUrlStr);

		ResourceUrlProcessorNg rscUrlProcObj = new ResourceUrlProcessorNg(fullRscUrlStr);

		rscUrlProcObj.rscUrlStrPathOrg = rscUrlProcObj.extractResourceUrlPath();
		rscUrlProcObj.rscUrlStrPathMod = rscUrlProcObj.rscUrlStrPathOrg;

		rscUrlProcObj.extractSubStreamId();
		if (! rscUrlProcObj.subStreamIdStr.isBlank()) {
			resObj.idSubStream.setIdStr(rscUrlProcObj.subStreamIdStr);
		}

		if (! rscUrlProcObj.rscUrlStrPathMod.isBlank()) {
			String tmpPath = rscUrlProcObj.rscUrlStrPathMod;
			while (tmpPath.endsWith("/")) {
				tmpPath = tmpPath.substring(0, tmpPath.length() - 1);
			}
			resObj.idInputSource.setIdStr(tmpPath);
		}

		/*
		 * Input:
		 *   rtsp://localhost:1051/movie.stream
		 * Output:
		 *   rscUrlStrPathMod = "movie.stream"
		 *   subStreamIdStr   = ""
		 * 
		 * Input:
		 *   rtsp://localhost:1051/movie.stream/substreamid1234
		 * Output:
		 *   rscUrlStrPathMod = "movie.stream"
		 *   subStreamIdStr   = "1234"
		 *
		 * Input:
		 *   rtsp://localhost:1051/substreamid1234
		 * Output:
		 *   rscUrlStrPathMod = ""
		 *   subStreamIdStr   = "1234"
		 */

		if (! resObj.idSubStream.isEmpty()) {
			RtspProtoIdInputSource tmpIdIs = new RtspProtoIdInputSource();
			tmpIdIs.copyFrom(
					// this also checks if the Sub-Stream ID exists
					globalSessionInfoInterface.getInputSourceIdBySubStreamId(resObj.idSubStream, clientIpAddr)
				);
			if (! resObj.idInputSource.isEmpty() && ! tmpIdIs.equals(resObj.idInputSource)) {
				throw new RtspInvalidUriException("Input Source ID resolved from Sub-Stream ID does not match");
			}
			resObj.idInputSource.copyFrom(tmpIdIs);
			resObj.idStreamSource.copyFrom(
					globalSessionInfoInterface.getStreamSourceIdBySubStreamId(resObj.idSubStream, clientIpAddr)
				);
		}

		if (resObj.idInputSource.isEmpty()) {
			throw new RtspInvalidUriException("Input Source ID is missing");
		}
		if (! availableStreamsInterface.existsInputSourceId(resObj.idInputSource)) {
			throw new RtspIdInputSourceNotFoundException("Input Source ID is invalid");
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Extract the Resource URL Path from the given (full) Resource URL.
	 * @return The Resource URL Path (e.g. 'movie.stream/streamid0')
	 * @throws RtspInvalidUriException If the Resource URL is invalid
	 */
	private @NonNull String extractResourceUrlPath() throws RtspInvalidUriException {
		URI rscUriObj;
		try {
			rscUriObj = HostnameHelper.convertRtspUrlIntoURI(fullRscUrlStr);
		} catch (HostnameHelperInvalidUriException e) {
			throw new RtspInvalidUriException(e.getMessage());
		}
		String tmpPath = rscUriObj.getPath();
		tmpPath = tmpPath.strip();
		if (tmpPath.startsWith("/")) {
			tmpPath = tmpPath.substring(1).strip();
		}
		if (tmpPath.startsWith("../") || tmpPath.contains("/../")) {
			throw new RtspInvalidUriException("Path contains '../'");
		}
		if (tmpPath.isBlank()) {
			throw new RtspInvalidUriException("Empty path");
		}
		return tmpPath;
	}

	/**
	 * Extract the Sub-Stream ID from the given Resource URL Path.
	 */
	private void extractSubStreamId() {
		/*
		 * For DESCRIBE/PLAY/PAUSE/TEARDOWN requests, the Resource URL needs to contain only the Input Source ID (== SDP name):
		 *   rtsp://localhost:1051/movie.stream
		 * For SETUP/GET_PARAMETER/SET_PARAMETER requests, the Resource URL can contain the Input Source ID and the Sub-Stream ID:
		 *   rtsp://localhost:1051/movie.stream/substreamid1234
		 * or it only contains the Sub-Stream ID:
		 *   rtsp://localhost:1051/substreamid1234
		 *
		 * The output for "/substreamid1234" would be "1234"
		 */
		String tmpPath = rscUrlStrPathOrg;
		final String tmpDefSsIdPfx = RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX;
		int tmpIdxA = tmpPath.lastIndexOf("/" + tmpDefSsIdPfx);
		if (tmpIdxA > 0) {
			// the Resource URL Path contains the Input Source ID and the Sub-Stream ID
			subStreamIdStr = tmpPath.substring(tmpIdxA + 1 + tmpDefSsIdPfx.length());
			rscUrlStrPathMod = rscUrlStrPathMod.substring(0, tmpIdxA);
		} else if (tmpPath.startsWith(tmpDefSsIdPfx)) {
			// the Resource URL Path contains only the Sub-Stream ID
			subStreamIdStr = tmpPath.substring(tmpDefSsIdPfx.length());
		}
	}

}
