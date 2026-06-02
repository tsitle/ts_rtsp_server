package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.net.URI;

final class ResourceUrlParsingVars {

	final @NonNull RtspMessageType requestType;
	final @NonNull String fullRscUrl;
	final @NonNull String rscUrlPathOrg;

	@NonNull String rscUrlPathMod;
	@NonNull String subStreamId = "";
	@Nullable RtspStreamSource streamSourceObjPtr = null;

	ResourceUrlParsingVars(@NonNull RtspMessageType requestType, @NonNull String fullRscUrl) throws RtspInvalidUriException {
		this.requestType = requestType;
		this.fullRscUrl = fullRscUrl;
		this.rscUrlPathOrg = extractResourceUrlPath(fullRscUrl);
		this.rscUrlPathMod = this.rscUrlPathOrg;
	}

	/**
	 * Returns the resource URL path from the given resource URL string
	 * @param resourceUrlStr Full resource URL string (e.g. 'rtsp://localhost:1051/movie.sdp/streamid0')
	 * @return The resource URL path (e.g. 'movie.sdp/streamid0')
	 * @throws RtspInvalidUriException If the resource URL is invalid
	 */
	private static @NonNull String extractResourceUrlPath(@NonNull String resourceUrlStr) throws RtspInvalidUriException {
		URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
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
