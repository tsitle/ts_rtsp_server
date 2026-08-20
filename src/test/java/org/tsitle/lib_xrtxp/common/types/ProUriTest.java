package org.tsitle.lib_xrtxp.common.types;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProUriTest {

	@Test
	void testValidFileUri() throws Exception {
		// Arrange
		String uriString = "file:///home/user/documents/file.txt";

		// Act
		ProUri fileUri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.FILE, fileUri.getScheme().orElse(null), "Scheme should match.");
		assertEquals("/home/user/documents/file.txt", fileUri.getPath().orElse(null), "Path should match.");
		assertTrue(fileUri.getHost().isEmpty(), "Host for FILE scheme should be empty.");
		assertTrue(fileUri.getQuery().isEmpty(), "Query should be empty for FILE URI.");
		assertTrue(fileUri.getFragment().isEmpty(), "Fragment should be empty for FILE URI.");
	}

	@Test
	void testValidHttpUri() throws Exception {
		// Arrange
		String uriString = "http://example.com:8080/path/to/resource?query=value&some=other#fragment";

		// Act
		ProUri uri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.HTTP, uri.getScheme().orElse(null), "Scheme should match.");
		assertEquals("example.com", uri.getHost().orElse(null), "Host should match.");
		assertEquals(8080, uri.getPortIfPresent().orElse(-1), "Port should match.");
		assertEquals("/path/to/resource", uri.getPath().orElse(null), "Path should match.");
		assertEquals("query=value&some=other", uri.getQuery().orElse(null), "Query should match.");
		assertEquals("fragment", uri.getFragment().orElse(null), "Fragment should match.");
		assertFalse(uri.isEmpty(), "The URI should not be empty.");
		assertEquals(Optional.of(uriString), uri.getUriString(), "UriString should match the original URI.");
	}

	@Test
	void testValidHttpsUri() throws Exception {
		// Arrange
		String uriString = "https://example.com:8443/path/to/resource?query=value&some=other#fragment";

		// Act
		ProUri uri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.HTTPS, uri.getScheme().orElse(null), "Scheme should match.");
		assertEquals("example.com", uri.getHost().orElse(null), "Host should match.");
		assertEquals(8443, uri.getPortIfPresent().orElse(-1), "Port should match.");
		assertEquals("/path/to/resource", uri.getPath().orElse(null), "Path should match.");
		assertEquals("query=value&some=other", uri.getQuery().orElse(null), "Query should match.");
		assertEquals("fragment", uri.getFragment().orElse(null), "Fragment should match.");
		assertFalse(uri.isEmpty(), "The URI should not be empty.");
		assertEquals(Optional.of(uriString), uri.getUriString(), "UriString should match the original URI.");
	}

	@Test
	void testValidRtspUri() throws Exception {
		// Arrange
		String uriString = "rtsp://user:password@example.com/media";

		// Act
		ProUri uri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.RTSP, uri.getScheme().orElse(null), "Scheme should match.");
		assertEquals("user", uri.getCredentialsUsername().orElse(null), "Username should match.");
		assertEquals("password", uri.getCredentialsPassword().orElse(null), "Password should match.");
		assertEquals("example.com", uri.getHost().orElse(null), "Host should match.");
		assertTrue(uri.getPortIfPresent().isEmpty(), "Port(-pres) should be empty.");
		assertEquals(ProUri.RTSP_TCP_PORT_DEFAULT, uri.getPortOrDefault().orElse(-1), "Port(-def) should match.");
		assertEquals("/media", uri.getPath().orElse(null), "Path should match.");
		assertFalse(uri.isEmpty(), "The URI should not be empty.");
	}

	@Test
	void testValidRtspsUri() throws Exception {
		// Arrange
		String uriString = "rtsps://user:password@example.com/media";

		// Act
		ProUri uri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.RTSPS, uri.getScheme().orElse(null), "Scheme should match.");
		assertEquals("user", uri.getCredentialsUsername().orElse(null), "Username should match.");
		assertEquals("password", uri.getCredentialsPassword().orElse(null), "Password should match.");
		assertEquals("example.com", uri.getHost().orElse(null), "Host should match.");
		assertTrue(uri.getPortIfPresent().isEmpty(), "Port(-pres) should be empty.");
		assertEquals(ProUri.RTSPS_TCP_PORT_DEFAULT, uri.getPortOrDefault().orElse(-1), "Port(-def) should match.");
		assertEquals("/media", uri.getPath().orElse(null), "Path should match.");
		assertFalse(uri.isEmpty(), "The URI should not be empty.");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void testInvalidSchemeThrowsException() {
		// Arrange
		String invalidUriString = "invalid://example.com/resource";

		// Act & Assert
		ProUriInvalidUriException exception = assertThrows(
				ProUriInvalidUriException.class,
				() -> ProUri.of(invalidUriString),
				"Expected an invalid scheme to throw ProUriInvalidUriException."
		);
		assertTrue(exception.getMessage().contains("invalid scheme in URI"));
	}

	@Test
	void testMissingHostThrowsException() {
		// Arrange
		String invalidUriString = "http:///resource";

		// Act & Assert
		ProUriInvalidUriException exception = assertThrows(
				ProUriInvalidUriException.class,
				() -> ProUri.of(invalidUriString),
				"Expected a missing host to throw ProUriInvalidUriException."
		);
		assertTrue(exception.getMessage().contains("missing host in URI"));
	}

	@Test
	void testEmptyUriIsValid() throws Exception {
		// Act
		ProUri emptyUri = ProUri.ofEmpty();

		// Assert
		assertTrue(emptyUri.isEmpty(), "The URI should be empty.");
		assertEquals(ProUri.Scheme.NONE, emptyUri.getScheme().orElse(ProUri.Scheme.NONE), "Scheme should be NONE for an empty URI.");
		assertTrue(emptyUri.getUriString().isEmpty(), "UriString of an empty URI should be empty.");
	}

	@Test
	void testOfCopiesExistingUri() throws Exception {
		// Arrange
		String uriString = "https://secure.example.com:443/secure/path";
		ProUri originalUri = ProUri.of(uriString);

		// Act
		ProUri copiedUri = ProUri.of(originalUri);

		// Assert
		assertEquals(originalUri.getScheme(), copiedUri.getScheme(), "Copied URI scheme should match original.");
		assertEquals(originalUri.getHost(), copiedUri.getHost(), "Copied URI host should match original.");
		assertEquals(originalUri.getPortIfPresent(), copiedUri.getPortIfPresent(), "Copied URI port should match original.");
		assertEquals(originalUri.getPortOrDefault(), copiedUri.getPortOrDefault(), "Copied URI port should match original.");
		assertEquals(originalUri.getPath(), copiedUri.getPath(), "Copied URI path should match original.");
	}

	@Test
	void testClonedUriRetainsValues() throws Exception {
		// Arrange
		String uriString = "https://api.example.com:123/resource?param=value#anchor";
		ProUri originalUri = ProUri.of(uriString);

		// Act
		ProUri clonedUri = originalUri.clone();

		// Assert
		assertEquals(originalUri.getScheme(), clonedUri.getScheme(), "Cloned URI scheme should match original.");
		assertEquals(originalUri.getHost(), clonedUri.getHost(), "Cloned URI host should match original.");
		assertEquals(originalUri.getPortIfPresent(), clonedUri.getPortIfPresent(), "Cloned URI port(-pres) should match original.");
		assertEquals(originalUri.getPortOrDefault(), clonedUri.getPortOrDefault(), "Cloned URI port(-def) should match original.");
		assertEquals(originalUri.getPath(), clonedUri.getPath(), "Cloned URI path should match original.");
		assertEquals(originalUri.getQuery(), clonedUri.getQuery(), "Cloned URI query should match original.");
		assertEquals(originalUri.getFragment(), clonedUri.getFragment(), "Cloned URI fragment should match original.");
	}

	@Test
	void testUriWithoutFragmentOrQuery() throws Exception {
		// Arrange
		String uriString = "http://example.com/path";

		// Act
		ProUri uri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.HTTP, uri.getScheme().orElse(null), "Scheme should match.");
		assertEquals(ProUri.HTTP_TCP_PORT_DEFAULT, uri.getPortOrDefault().orElse(-1), "Port should match.");
		assertEquals("example.com", uri.getHost().orElse(null), "Host should match.");
		assertEquals("/path", uri.getPath().orElse(null), "Path should match.");
		assertTrue(uri.getQuery().isEmpty(), "Query should be empty.");
		assertTrue(uri.getFragment().isEmpty(), "Fragment should be empty.");
	}

	@Test
	void testUriWithoutPath() throws Exception {
		// Arrange
		String uriString = "https://example.com";

		// Act
		ProUri uri = ProUri.of(uriString);

		// Assert
		assertEquals(ProUri.Scheme.HTTPS, uri.getScheme().orElse(null), "Scheme should match.");
		assertEquals(ProUri.HTTPS_TCP_PORT_DEFAULT, uri.getPortOrDefault().orElse(-1), "Port should match.");
		assertEquals("example.com", uri.getHost().orElse(null), "Host should match.");
		assertTrue(uri.getPath().isEmpty(), "Path should be empty.");
		assertTrue(uri.getQuery().isEmpty(), "Query should be empty.");
		assertTrue(uri.getFragment().isEmpty(), "Fragment should be empty.");
	}

}
