package org.tsitle.lib_xrtxp.rtsp.highlevel;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdSubStreamNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidUriException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;

import java.util.HashSet;
import java.util.Set;

/**
 * Resource URL processor for the RTSP protocol.
 */
public final class ResourceUrlProcessor {

	/** Sub-Stream IDs that are available in the current session */
	private final @NonNull Set<@NonNull RtspProtoIdSubStream> availableSubStreamIds;
	/** Full Resource URL (e.g. 'rtsp://localhost:1051/movie.stream/substreamid1234') */
	private final @NonNull String fullRscUrlStr;

	private @NonNull String rscUrlStrPathOrg = "";
	private @NonNull String rscUrlStrPathMod = "";
	private @NonNull String subStreamIdStr = "";

	private ResourceUrlProcessor(
				@NonNull Set<@NonNull RtspProtoIdSubStream> availableSubStreamIds,
				@NonNull String fullRscUrlStr
			) {
		this.availableSubStreamIds = new HashSet<>(availableSubStreamIds);
		this.fullRscUrlStr = fullRscUrlStr;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse a Resource URL into an RtspProtoRscUrl object.
	 * @param availableStreamsInterface Available streams instance (only required on the server-side)
	 * @param globalSessionInfoInterface Global Session Info instance (only required on the server-side)
	 * @param fullRscUrlStr Full Resource URL string
	 * @param clientIpAddr Client IP address (only required on the server-side)
	 * @param inpAvailableSubStreamIds Input for available Sub-Stream IDs
	 * @return Parsed RtspProtoRscUrl object
	 * @throws RtspProtoInvalidUriException If the URI is invalid
	 * @throws RtspProtoIdSubStreamNotFoundException If the Sub-Stream ID could not be found
	 * @throws RtspProtoIdInputSourceNotFoundException If the Input Source ID could not be found
	 */
	public static @NonNull RtspProtoRscUrl parseUrlIntoRscUrlObject(
				@Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull String fullRscUrlStr,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds
			) throws RtspProtoInvalidUriException, RtspProtoIdSubStreamNotFoundException, RtspProtoIdInputSourceNotFoundException {
		RtspProtoRscUrl resObj = RtspProtoRscUrl.of(fullRscUrlStr);

		ResourceUrlProcessor rscUrlProcObj = new ResourceUrlProcessor(
				inpAvailableSubStreamIds,
				fullRscUrlStr
			);

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
			if (tmpPath.lastIndexOf('/') > 0) {
				tmpPath = tmpPath.substring(tmpPath.lastIndexOf('/') + 1);
			}
			/*
			 * In case that the Sub-Stream ID is present but invalid, the Input Source ID will actually be the invalid Sub-Stream ID.
			 * But since the Input Source ID will be validated at the end of this method, that should not be a problem.
			 */
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
		 */

		if (globalSessionInfoInterface != null && ! resObj.idSubStream.isEmpty()) {
			RtspProtoIdInputSource tmpIdIs = RtspProtoIdInputSource.ofEmpty();
			tmpIdIs.copyFrom(
					// this also checks if the Sub-Stream ID exists
					globalSessionInfoInterface.getInputSourceIdBySubStreamId(resObj.idSubStream, clientIpAddr)
				);
			if (! resObj.idInputSource.isEmpty() && ! tmpIdIs.equals(resObj.idInputSource)) {
				throw new RtspProtoInvalidUriException("Input Source ID resolved from Sub-Stream ID does not match");
			}
			resObj.idInputSource.copyFrom(tmpIdIs);
		}

		if (resObj.idInputSource.isEmpty()) {
			throw new RtspProtoInvalidUriException("Input Source ID is missing");
		}
		if (availableStreamsInterface != null && ! availableStreamsInterface.existsInputSourceId(resObj.idInputSource)) {
			throw new RtspProtoIdInputSourceNotFoundException("Input Source ID is invalid");
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Extract the Resource URL Path from the given (full) Resource URL.
	 * @return The Resource URL Path (e.g. 'movie.stream/streamid0')
	 * @throws RtspProtoInvalidUriException If the Resource URL is invalid
	 */
	private @NonNull String extractResourceUrlPath() throws RtspProtoInvalidUriException {
		ProUri rscUriObj;
		try {
			rscUriObj = ProUri.of(fullRscUrlStr);
		} catch (ProUriInvalidUriException e) {
			throw new RtspProtoInvalidUriException(e.getMessage());
		}
		String tmpPath = rscUriObj.getPath().orElse("");
		tmpPath = tmpPath.strip();
		if (tmpPath.startsWith("/")) {
			tmpPath = tmpPath.substring(1).strip();
		}
		if (tmpPath.startsWith("../") || tmpPath.contains("/../")) {
			throw new RtspProtoInvalidUriException("Path contains '../'");
		}
		if (tmpPath.isBlank()) {
			throw new RtspProtoInvalidUriException("Empty path");
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
		 */
		String tmpPath = rscUrlStrPathOrg;
		int tmpIdxA = tmpPath.lastIndexOf("/");
		if (tmpIdxA < 1) {
			// the Resource URL Path cannot contain an SDP Control ID
			return;
		}

		String controlIdStr = tmpPath.substring(tmpIdxA + 1);
		RtspProtoIdSubStream tmpControlIdAsSsId = RtspProtoIdSubStream.of(controlIdStr);
		if (! availableSubStreamIds.contains(tmpControlIdAsSsId)) {
			// the Resource URL Path does not contain a valid SDP Control ID
			return;
		}
		// the Resource URL Path contains the Input Source ID and the Sub-Stream ID
		subStreamIdStr = controlIdStr;
		rscUrlStrPathMod = rscUrlStrPathMod.substring(0, tmpIdxA);
	}

}
