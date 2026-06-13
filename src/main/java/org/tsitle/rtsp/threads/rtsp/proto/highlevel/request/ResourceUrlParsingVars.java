package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;

import java.net.URI;

final class ResourceUrlParsingVars {

	final @NonNull RtspMessageType requestType;
	final @NonNull String fullRscUrl;
	final @NonNull String rscUrlStrPathOrg;

	@NonNull String rscUrlStrPathMod;
	@NonNull String subStreamIdStr = "";
	@Nullable RtspStreamSource streamSourceObjPtr = null;

	ResourceUrlParsingVars(@NonNull RtspMessageType requestType, @NonNull String fullRscUrlStr) throws RtspInvalidUriException {
		this.requestType = requestType;
		this.fullRscUrl = fullRscUrlStr;
		this.rscUrlStrPathOrg = extractResourceUrlPath(fullRscUrlStr);
		this.rscUrlStrPathMod = this.rscUrlStrPathOrg;
	}

	/**
	 * Returns the resource URL path from the given resource URL string
	 * @param resourceUrlStr Full resource URL string (e.g. 'rtsp://localhost:1051/movie.sdp/streamid0')
	 * @return The resource URL path (e.g. 'movie.sdp/streamid0')
	 * @throws RtspInvalidUriException If the resource URL is invalid
	 */
	private static @NonNull String extractResourceUrlPath(@NonNull String resourceUrlStr) throws RtspInvalidUriException {
		URI rscUriObj;
		try {
			rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
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

}
