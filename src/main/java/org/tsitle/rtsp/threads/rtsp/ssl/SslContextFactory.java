package org.tsitle.rtsp.threads.rtsp.ssl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.SslException;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * SSL Context factory
 */
public final class SslContextFactory {

	@SuppressWarnings("unused")
	public SslContextFactory() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create an SSLContext for server-side SSL/TLS connections.
	 * @param rtspsCertPath Path to the server certificate
	 * @param rtspsKeyPath Path to the private key
	 * @param rtspsCaPath Path to the CA certificate (optional)
	 * @return SSL server socket factory
	 * @throws SslException If any kind of error occurred
	 */
	public static @NonNull SSLContext createServerSocketFactory(
				@NonNull Path rtspsCertPath,
				@NonNull Path rtspsKeyPath,
				@Nullable Path rtspsCaPath
			) throws SslException {
		try {
			return buildServerContext(
					buildServerKeyStore(
							loadKey(rtspsKeyPath),
							loadCert(rtspsCertPath),
							rtspsCaPath != null ? loadCert(rtspsCaPath) : null
						),
					rtspsCaPath != null ? loadCert(rtspsCaPath) : null
				);
		} catch (Exception e) {
			throw new SslException("Failed to create SSL server socket factory: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create an SSLContext for client-side SSL/TLS connections that accepts only the given certificate.
	 * @param certificatePath Path to the client certificate
	 * @return SSLContext configured for client-side SSL/TLS
	 * @throws SslException If any kind of error occurred
	 */
	public static @NonNull SSLContext createClientSslContextFromPem(@NonNull Path certificatePath) throws SslException {
		System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

		try {
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
		} catch (CertificateException e) {
			throw new SslException("CertificateException: " + e.getMessage());
		} catch (IOException e) {
			throw new SslException("IOException: " + e.getMessage());
		} catch (KeyStoreException e) {
			throw new SslException("KeyStoreException: " + e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new SslException("NoSuchAlgorithmException: " + e.getMessage());
		} catch (KeyManagementException e) {
			throw new SslException("KeyManagementException: " + e.getMessage());
		}
	}

	/**
	 * Create an SSLContext for client-side SSL/TLS connections without any kind of verification.
	 * @return SSLContext configured for client-side SSL/TLS
	 * @throws SslException If any kind of error occurred
	 */
	public static @NonNull SSLContext createClientInsecureSslContext() throws SslException {
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

		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustAllManagers, new SecureRandom());
			return sslContext;
		} catch (NoSuchAlgorithmException e) {
			throw new SslException("NoSuchAlgorithmException: " + e.getMessage());
		} catch (KeyManagementException e) {
			throw new SslException("KeyManagementException: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Load an X.509 certificate
	 * @param filePath Certificate file
	 * @return X.509 certificate
	 * @throws Exception If any kind of error occurred
	 */
	private static @NonNull X509Certificate loadCert(@NonNull Path filePath) throws Exception {
		try (InputStream in = Files.newInputStream(filePath)) {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			return (X509Certificate)cf.generateCertificate(in);
		}
	}

	/**
	 * Load a private key (PKCS#8 PEM)
	 * @param filePath Private key file
	 * @return Private key
	 * @throws Exception If any kind of error occurred
	 */
	private static @NonNull PrivateKey loadKey(@NonNull Path filePath) throws Exception {
		/*
		 * If the key file is PKCS#1 (BEGIN RSA PRIVATE KEY), it must be converted to PKCS#8:
		 *   $ openssl pkcs8 -topk8 -nocrypt -in server.key -out server.pk8
		 */
		String pem = Files.readString(filePath)
				.replaceAll("-----BEGIN (.*)-----", "")
				.replaceAll("-----END (.*)-----", "")
				.replaceAll("\\s", "");

		byte[] decoded = Base64.getDecoder().decode(pem);
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
		return KeyFactory.getInstance("RSA").generatePrivate(spec);
	}

	/**
	 * Build a KeyStore with the given private key and certificate chain
	 * @param key Private key
	 * @param cert Server certificate
	 * @param ca CA certificate (optional)
	 * @return KeyStore
	 * @throws Exception If any kind of error occurred
	 */
	private static @NonNull KeyStore buildServerKeyStore(
				@NonNull PrivateKey key,
				@NonNull X509Certificate cert,
				@Nullable X509Certificate ca
			) throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		ks.load(null, null);

		Certificate[] chain = (ca != null ? new Certificate[]{cert, ca} : new Certificate[]{cert});

		ks.setKeyEntry("server", key, "pass".toCharArray(), chain);
		return ks;
	}

	/**
	 * Build an SSLContext with the given KeyStore and CA certificate (optional)
	 * @param ks KeyStore containing the server certificate and private key
	 * @param ca CA certificate (optional)
	 * @return SSLContext configured for server-side SSL/TLS
	 * @throws Exception If any kind of error occurred
	 */
	private static @NonNull SSLContext buildServerContext(@NonNull KeyStore ks, @Nullable X509Certificate ca) throws Exception {
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, "pass".toCharArray());

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

		if (ca != null) {
			KeyStore ts = KeyStore.getInstance("PKCS12");
			ts.load(null, null);
			ts.setCertificateEntry("ca", ca);
			tmf.init(ts);
		} else {
			tmf.init((KeyStore) null);  // default JVM truststore
		}

		SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return ctx;
	}

}
