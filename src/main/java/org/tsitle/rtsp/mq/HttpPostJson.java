package org.tsitle.rtsp.mq;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public class HttpPostJson {

	private final HttpClient httpClient;
	private final Gson gson;
	private final String authorizationHeaderValue;

	public HttpPostJson(String username, String password) throws Exception {
		this.httpClient = HttpClient.newBuilder()
				.sslContext(createInsecureSslContext())  // @TODO
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		this.gson = new Gson();

		String auth = username + ":" + password;
		String encodedAuth = Base64.getEncoder()
				.encodeToString(auth.getBytes(StandardCharsets.UTF_8));
		this.authorizationHeaderValue = "Basic " + encodedAuth;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public <T> T postJson(String url, Map<String, Object> requestBody, Type T) throws IOException, InterruptedException {
		String jsonBody = gson.toJson(requestBody);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("Authorization", authorizationHeaderValue)
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

		HttpResponse<String> response = httpClient.send(
				request,
				HttpResponse.BodyHandlers.ofString()
			);

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode() + " returned: " + response.body());
		}

		return gson.fromJson(JsonParser.parseString(response.body()), T);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static SSLContext createSslContextFromPem(Path certificatePath) throws Exception {
		System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

		CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

		Certificate certificate;
		try (InputStream inputStream = Files.newInputStream(certificatePath)) {
			certificate = certificateFactory.generateCertificate(inputStream);
		}

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setCertificateEntry("remote-server", certificate);

		TrustManagerFactory trustManagerFactory =
				TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(keyStore);

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

		return sslContext;
	}

	private static SSLContext createInsecureSslContext() throws Exception {
		System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

		TrustManager[] trustAllManagers = new TrustManager[] {
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType) {
					}
					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType) {
					}
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				}
			};

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustAllManagers, new SecureRandom());
		return sslContext;
	}

}
