package org.tsitle.rtsp.mq;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.SslException;
import org.tsitle.rtsp.security.SslContextFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * HTTP client for JSON-based HTTP requests with basic authentication.
 */
public class HttpClientJson {

	private final HttpClient httpClient;
	private final Gson gson;
	private final String authorizationHeaderValue;

	/**
	 * Constructor.
	 * @param username Username (can be empty)
	 * @param password Password (can be empty, will be ignored if {@code username} is empty)
	 * @param useCompletelyInsecureSsl Use completely insecure SSL?
	 * @param useRemoteCertForSsl Use a certificate to validate the remote SSL server?
	 * @param remoteCertPath Path to certificate to validate the remote SSL server
	 */
	private HttpClientJson(
				@NonNull String username,
				@NonNull String password,
				boolean useCompletelyInsecureSsl,
				boolean useRemoteCertForSsl,
				@NonNull Path remoteCertPath
			) throws SslException {
		HttpClient.Builder builder = HttpClient.newBuilder();
		builder.connectTimeout(Duration.ofSeconds(10));
		if (useCompletelyInsecureSsl) {
			builder.sslContext(SslContextFactory.createClientInsecureSslContext());
		} else if (useRemoteCertForSsl) {
			builder.sslContext(SslContextFactory.createClientSslContextFromPem(remoteCertPath));
		}
		this.httpClient = builder.build();
		this.gson = new Gson();

		if (! (username.isBlank() || password.isBlank())) {
			String tmpAuthPlain = username + ":" + password;
			String tmpAuthEnc = Base64.getEncoder()
					.encodeToString(tmpAuthPlain.getBytes(StandardCharsets.UTF_8));
			this.authorizationHeaderValue = "Basic " + tmpAuthEnc;
		} else {
			this.authorizationHeaderValue = "";
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new HttpClientJson instance without SSL support.
	 * @param username Username (can be empty)
	 * @param password Password (can be empty, will be ignored if {@code username} is empty)
	 */
	@SuppressWarnings("unused")
	public static HttpClientJson createClientWithoutSsl(
				@NonNull String username,
				@NonNull String password
			) throws SslException {
		return new HttpClientJson(username, password, false, false, Path.of(""));
	}

	/**
	 * Create a new HttpClientJson instance with completely insecure SSL.
	 * @param username Username (can be empty)
	 * @param password Password (can be empty, will be ignored if {@code username} is empty)
	 */
	public static HttpClientJson createClientWithCompletelyInsecureSsl(
				@NonNull String username,
				@NonNull String password
			) throws SslException {
		return new HttpClientJson(username, password, true, false, Path.of(""));
	}

	/**
	 * Create a new HttpClientJson instance that uses a certificate to validate the remote SSL server.
	 * @param username Username (can be empty)
	 * @param password Password (can be empty, will be ignored if {@code username} is empty)
	 * @param remoteCertPath Path to certificate to validate the remote SSL server
	 */
	public static HttpClientJson createClientWithRemoteCertForSsl(
				@NonNull String username,
				@NonNull String password,
				@NonNull Path remoteCertPath
			) throws SslException {
		return new HttpClientJson(username, password, false, true, remoteCertPath);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Send a POST request with a JSON body to the specified URL and parse the response as JSON.
	 * @param url URL to send the request to
	 * @param requestBody JSON body dictionary
	 * @param T Type of the response
	 * @return Response parsed into an object
	 * @throws IOException If an I/O error occurred
	 * @throws InterruptedException If the operation has been interrupted
	 */
	public <T> T postJson(@NonNull String url, @NonNull Map<String, Object> requestBody, @NonNull Type T)
			throws IOException, InterruptedException {
		String jsonBody = gson.toJson(requestBody);

		HttpRequest.Builder tmpBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json");
		if (! authorizationHeaderValue.isBlank()) {
			tmpBuilder.header("Authorization", authorizationHeaderValue);
		}
		HttpRequest request = tmpBuilder
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

		HttpResponse<String> response;
		try {
			response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString()
				);
		} catch (IOException e) {
			if (e.getMessage() != null && e.getMessage().contains("sun.security.provider.certpath.SunCertPathBuilderException")) {
				throw new IOException("Remote SSL certificate does not match the trusted local certificate");
			}
			throw e;
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode() + " returned: " + response.body());
		}

		return gson.fromJson(JsonParser.parseString(response.body()), T);
	}

}
