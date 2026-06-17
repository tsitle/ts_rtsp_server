package org.tsitle.lib_xrtxp.common.helpers;

import org.tsitle.lib_xrtxp.common.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;

import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Hostname/IP Helper.
 */
public final class HostnameHelper {

	private HostnameHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Returns the first IPv4 address that the given hostname resolves to
	 * which is also assigned to a local network interface.
	 * @param hostname Hostname
	 * @param allowLoopback Whether to include loopback addresses in the search
	 * @return IP address
	 */
	public static Optional<InetAddress> firstAvailableLocalIpv4AddressForHostname(String hostname, boolean allowLoopback)
			throws UnknownHostException, SocketException {
		InetAddress[] resolved = InetAddress.getAllByName(hostname);
		if (resolved.length == 0) {
			return Optional.empty();
		}

		Set<InetAddress> localInterfaceAddrs = getAllLocalIpv4InterfaceAddresses(allowLoopback);

		return Arrays.stream(resolved)
				.filter(localInterfaceAddrs::contains)
				.findFirst();
	}

	/**
	 * Get all IPv4 addresses that the given hostname resolves to.
	 * @param hostname Hostname
	 * @param allowLoopback Whether to include loopback addresses in the search
	 * @return IP addresses
	 */
	@SuppressWarnings("unused")
	public static Set<InetAddress> allLocalIpv4AddressesForHostname(String hostname, boolean allowLoopback)
			throws UnknownHostException, SocketException {
		InetAddress[] resolved = InetAddress.getAllByName(hostname);
		Set<InetAddress> localInterfaceAddrs = getAllLocalIpv4InterfaceAddresses(allowLoopback);
		Set<InetAddress> matches = new HashSet<>();
		for (InetAddress addr : resolved) {
			if (localInterfaceAddrs.contains(addr)) {
				matches.add(addr);
			}
		}
		return matches;
	}

	/**
	 * Convert an RTSP URL into a URI object.
	 * @param url RTSP URL
	 * @return URI object
	 * @throws HostnameHelperInvalidUriException If the URL is invalid
	 */
	public static URI convertRtspUrlIntoURI(String url) throws HostnameHelperInvalidUriException {
		try {
			if (! (url.startsWith(RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://") ||
						url.startsWith(RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://"))) {
				throw new HostnameHelperInvalidUriException("Invalid protocol in URL: '" + url + "'");
			}
			// we need to replace the protocol here since the URI class does not support 'rtsp://'
			@SuppressWarnings("HttpUrlsUsage") URI resObj = new URI(
					url
							.replaceAll("^" + RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://", "https://")
							.replaceAll("^" + RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://", "http://"
						));
			if (! (resObj.getScheme().equals("https") || resObj.getScheme().equals("http"))) {
				throw new HostnameHelperInvalidUriException("Invalid protocol: " + resObj.getScheme());
			}
			return resObj;
		} catch (URISyntaxException e) {
			throw new HostnameHelperInvalidUriException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static Set<InetAddress> getAllLocalIpv4InterfaceAddresses(boolean allowLoopback) throws SocketException {
		Set<InetAddress> resSet = new HashSet<>();
		Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
		while (ifaces.hasMoreElements()) {
			NetworkInterface ni = ifaces.nextElement();
			if (! ni.isUp() || (! allowLoopback && ni.isLoopback())) {
				continue;
			}
			Enumeration<InetAddress> addrs = ni.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress tmpAddr = addrs.nextElement();
				if (! tmpAddr.getClass().equals(Inet4Address.class)) {
					continue;
				}
				resSet.add(tmpAddr);
			}
		}
		return resSet;
	}

}
