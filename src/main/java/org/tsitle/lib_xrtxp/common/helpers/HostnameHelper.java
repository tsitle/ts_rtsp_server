package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;

import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hostname/IP Helper.
 */
public final class HostnameHelper {

	private static final Pattern IPV4_MATCHER = Pattern.compile(
			"(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}"
					+ "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?![\\d.])"
		);

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
	public static Optional<InetAddress> firstAvailableLocalIpv4AddressForHostname(@NonNull String hostname, boolean allowLoopback)
			throws UnknownHostException, SocketException {
		if (IPV4_MATCHER.matcher(hostname).matches()) {
			return Optional.of(InetAddress.getByName(hostname));
		}

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
	public static @NonNull Set<@NonNull InetAddress> allLocalIpv4AddressesForHostname(@NonNull String hostname, boolean allowLoopback)
			throws UnknownHostException, SocketException {
		if (IPV4_MATCHER.matcher(hostname).matches()) {
			return Set.of(InetAddress.getByName(hostname));
		}

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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull Set<@NonNull InetAddress> getAllLocalIpv4InterfaceAddresses(boolean allowLoopback) throws SocketException {
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
