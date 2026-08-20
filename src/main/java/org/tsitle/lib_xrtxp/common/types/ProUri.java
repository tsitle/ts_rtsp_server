package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * URI data container with support for RTSP.
 */
public final class ProUri implements Cloneable {

	public enum Scheme {
		NONE, FILE, HTTP, HTTPS, RTSP, RTSPS
	}

	public static final String SCHEME_FILE = "file";
	public static final String SCHEME_HTTP = "http";
	public static final String SCHEME_HTTPS = "https";
	public static final String SCHEME_RTSP = "rtsp";
	public static final String SCHEME_RTSPS = "rtsps";

	/** Default TCP port for an HTTP server (without SSL/TLS) */
	public static final int HTTP_TCP_PORT_DEFAULT = 80;
	/** Default TCP port for an HTTPS server (with SSL/TLS) */
	public static final int HTTPS_TCP_PORT_DEFAULT = 443;
	/** Default TCP port for a RTSP server (without SSL/TLS) */
	public static final int RTSP_TCP_PORT_DEFAULT = 554;
	/** Default TCP port for a RTSPS server (with SSL/TLS) */
	public static final int RTSPS_TCP_PORT_DEFAULT = 322;

	private @NonNull Scheme mScheme;
	private @NonNull String mCredentials;
	private @NonNull String mHost;
	private int mPort;
	private @NonNull String mPath;
	private @NonNull String mQuery;
	private @NonNull String mFragment;

	private ProUri(@NonNull String uriString) throws ProUriInvalidUriException {
		final String FNC_NAME = getClass().getSimpleName() + ".ctor()";

		uriString = uriString.strip();

		if (uriString.startsWith(SCHEME_FILE + "://")) {
			this.mScheme = Scheme.FILE;
		} else if (uriString.startsWith(SCHEME_HTTP + "://")) {
			this.mScheme = Scheme.HTTP;
		} else if (uriString.startsWith(SCHEME_HTTPS + "://")) {
			this.mScheme = Scheme.HTTPS;
		} else if (uriString.startsWith(SCHEME_RTSP + "://")) {
			this.mScheme = Scheme.RTSP;
		} else if (uriString.startsWith(SCHEME_RTSPS + "://")) {
			this.mScheme = Scheme.RTSPS;
		} else {
			if (! uriString.isBlank()) {
				throw new ProUriInvalidUriException(FNC_NAME + ": invalid scheme in URI");
			}
			this.mScheme = Scheme.NONE;
		}

		//
		if (this.mScheme != Scheme.NONE && ! uriString.isBlank()) {
			uriString = uriString
					.replace(SCHEME_RTSP + "://", SCHEME_HTTP + "://")
					.replace(SCHEME_RTSPS + "://", SCHEME_HTTPS + "://");
			try {
				URI tmpPlainUri = URI.create(uriString);  // throws IllegalArgumentException
				this.mCredentials = (tmpPlainUri.getUserInfo() != null ? tmpPlainUri.getUserInfo() : "");
				this.mHost = (tmpPlainUri.getHost() != null ? tmpPlainUri.getHost() : "");
				this.mPort = (tmpPlainUri.getPort() > 0 ? tmpPlainUri.getPort() : -1);
				this.mPath = (tmpPlainUri.getPath() != null ? tmpPlainUri.getPath() : "");
				this.mQuery = (tmpPlainUri.getQuery() != null ? tmpPlainUri.getQuery() : "");
				this.mFragment = (tmpPlainUri.getFragment() != null ? tmpPlainUri.getFragment() : "");
			} catch (IllegalArgumentException e) {
				throw new ProUriInvalidUriException(FNC_NAME + ": invalid URI: " + e.getMessage());
			}
			if (this.mScheme != Scheme.FILE && this.mHost.isBlank()) {
				throw new ProUriInvalidUriException(FNC_NAME + ": missing host in URI");
			}
		} else {
			this.mCredentials = "";
			this.mHost = "";
			this.mPort = -1;
			this.mPath = "";
			this.mQuery = "";
			this.mFragment = "";
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull ProUri ofEmpty() throws ProUriInvalidUriException {
		return new ProUri("");
	}

	public static @NonNull ProUri ofFile(@NonNull String path) throws ProUriInvalidUriException {
		return of(Scheme.FILE, "", "", "", -1, path, "", "");
	}

	public static @NonNull ProUri of(
				@NonNull Scheme scheme,
				@NonNull String host,
				int port,
				@NonNull String path
			) throws ProUriInvalidUriException {
		return of(scheme, "", "", host, port, path, "", "");
	}

	public static @NonNull ProUri of(
				@NonNull Scheme scheme,
				@NonNull String credUsername,
				@NonNull String credPassword,
				@NonNull String host,
				int port,
				@NonNull String path
			) throws ProUriInvalidUriException {
		return of(scheme, credUsername, credPassword, host, port, path, "", "");
	}

	public static @NonNull ProUri of(
				@NonNull Scheme scheme,
				@NonNull String credUsername,
				@NonNull String credPassword,
				@NonNull String host,
				int port,
				@NonNull String path,
				@NonNull String query
			) throws ProUriInvalidUriException {
		return of(scheme, credUsername, credPassword, host, port, path, query, "");
	}

	public static @NonNull ProUri of(
				@NonNull Scheme scheme,
				@NonNull String credUsername,
				@NonNull String credPassword,
				@NonNull String host,
				int port,
				@NonNull String path,
				@NonNull String query,
				@NonNull String fragment
			) throws ProUriInvalidUriException {
		if (credUsername.isBlank() && ! credPassword.isBlank()) {
			credPassword = "";
		}
		if (query.startsWith("?")) {
			query = query.substring(1);
		}
		if (fragment.startsWith("#")) {
			fragment = fragment.substring(1);
		}
		ProUri resObj = new ProUri("");
		resObj.mScheme = scheme;
		resObj.mCredentials = (! credUsername.isBlank() ? credUsername + ":" + credPassword : "");
		resObj.mHost = host;
		resObj.mPort = (port < 1 || port > 65535 ? -1 : port);
		resObj.mPath = path;
		resObj.mQuery = query;
		resObj.mFragment = fragment;

		// by calling of() here we ensure that the URI gets validated
		return of(resObj.getUriString().orElse(""));
	}

	public static @NonNull ProUri of(@NonNull ProUri other) throws ProUriInvalidUriException {
		return new ProUri(other.getUriString().orElse(""));
	}

	public static @NonNull ProUri of(@NonNull String uriString) throws ProUriInvalidUriException {
		return new ProUri(uriString);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Scheme> getScheme() {
		return (mScheme == Scheme.NONE ? Optional.empty() : Optional.of(mScheme));
	}

	public Optional<String> getCredentialsUsername() {
		if (mScheme == Scheme.NONE || mScheme == Scheme.FILE || mCredentials.isBlank()) {
			return Optional.empty();
		}
		String resS = mCredentials.split(":", 2)[0];
		return (resS.isBlank() ? Optional.empty() : Optional.of(resS));
	}

	public Optional<String> getCredentialsPassword() {
		if (mScheme == Scheme.NONE || mScheme == Scheme.FILE || mCredentials.isBlank()) {
			return Optional.empty();
		}
		String[] tmpSplit = mCredentials.split(":", 2);
		if (tmpSplit.length < 2) {
			return Optional.empty();
		}
		return (tmpSplit[1].isBlank() ? Optional.empty() : Optional.of(tmpSplit[1]));
	}

	public Optional<String> getHost() {
		return (mScheme == Scheme.NONE || mScheme == Scheme.FILE || mHost.isBlank() ?
				Optional.empty() : Optional.of(mHost));
	}

	public Optional<Integer> getPortIfPresent() {
		return (mScheme == Scheme.NONE || mScheme == Scheme.FILE || mPort < 1 ?
				Optional.empty() : Optional.of(mPort));
	}

	public Optional<Integer> getPortOrDefault() {
		if (mScheme == Scheme.NONE || mScheme == Scheme.FILE) {
			return Optional.empty();
		}
		if (mPort > 0) {
			return Optional.of(mPort);
		}
		return Optional.of(
				switch (mScheme) {
					case HTTP -> HTTP_TCP_PORT_DEFAULT;
					case HTTPS -> HTTPS_TCP_PORT_DEFAULT;
					case RTSP -> RTSP_TCP_PORT_DEFAULT;
					case RTSPS -> RTSPS_TCP_PORT_DEFAULT;
					default -> throw new RuntimeException("Unsupported scheme: " + mScheme);  // this should never happen
				}
			);
	}

	public Optional<String> getPath() {
		return (mScheme == Scheme.NONE || mPath.isBlank() ?
				Optional.empty() : Optional.of(mPath));
	}

	public Optional<String> getQuery() {
		return (mScheme == Scheme.NONE || mScheme == Scheme.FILE || mQuery.isBlank() ?
				Optional.empty() : Optional.of(mQuery));
	}

	public Optional<String> getFragment() {
		return (mScheme == Scheme.NONE || mScheme == Scheme.FILE || mFragment.isBlank() ?
				Optional.empty() : Optional.of(mFragment));
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getUriString() {
		if (mScheme == Scheme.NONE) {
			return Optional.empty();
		}
		StringBuilder sb = new StringBuilder();

		String tmpSch = switch (mScheme) {
				case FILE -> "file";
				case HTTP -> "http";
				case HTTPS -> "https";
				case RTSP -> "rtsp";
				case RTSPS -> "rtsps";
				default -> "-none-";
			};
		sb.append(tmpSch).append("://");

		Optional<String> tmpOptStr = getCredentialsUsername();
		if (tmpOptStr.isPresent()) {
			sb.append(tmpOptStr.get());
			tmpOptStr = getCredentialsPassword();
			tmpOptStr.ifPresent(s -> sb.append(":").append(s));
			sb.append("@");
		}

		tmpOptStr = getHost();
		tmpOptStr.ifPresent(s -> sb.append(s.toLowerCase()));

		Optional<Integer> tmpOptInt = getPortIfPresent();
		tmpOptInt.ifPresent(s -> sb.append(":").append(s));

		tmpOptStr = getPath();
		tmpOptStr.ifPresent(sb::append);

		tmpOptStr = getQuery();
		tmpOptStr.ifPresent(s -> sb.append("?").append(s));

		tmpOptStr = getFragment();
		tmpOptStr.ifPresent(s -> sb.append("#").append(s));

		return Optional.of(sb.toString());
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (mScheme == Scheme.NONE);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof ProUri other)) {
			return false;
		}
		String otherStr = other.getUriString().orElse("-empty-");
		String thisStr = getUriString().orElse("-empty-");
		return thisStr.equals(otherStr);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUriString().orElse("-empty-"));
	}

	@Override
	public @NonNull ProUri clone() {
		try {
			return (ProUri)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				(isEmpty() ? "empty" : "'" + getUriString().orElse("-") + "'") +
				"]";
	}

}
