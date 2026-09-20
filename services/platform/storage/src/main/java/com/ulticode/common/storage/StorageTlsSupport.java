package com.ulticode.common.storage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Builds an additive trust context for operator-provided RustFS CA certificates. */
final class StorageTlsSupport {

    private static final String PEM_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_END = "-----END CERTIFICATE-----";

    private StorageTlsSupport() {
    }

    static Optional<SSLContext> sslContext(StorageProperties.S3 properties) {
        String configuredPath = properties.getCaCertificatePath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return Optional.empty();
        }
        try {
            List<X509Certificate> certificates = parseCertificates(Path.of(configuredPath.trim()));
            return Optional.of(buildContext(certificates));
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            throw invalidConfiguration(configuredPath, exception);
        }
    }

    static void validatePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return;
        }
        try {
            parseCertificates(Path.of(configuredPath.trim()));
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            throw invalidConfiguration(configuredPath, exception);
        }
    }

    static List<X509Certificate> parseCertificates(Path path)
            throws IOException, CertificateException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("file is missing or unreadable");
        }
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        if (!pem.contains(PEM_BEGIN) || !pem.contains(PEM_END)) {
            throw new CertificateException("PEM certificate markers are missing");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        try (InputStream input = Files.newInputStream(path)) {
            Collection<? extends Certificate> parsed = factory.generateCertificates(input);
            for (Certificate certificate : parsed) {
                if (!(certificate instanceof X509Certificate x509)) {
                    throw new CertificateException("certificate is not X.509");
                }
                certificates.add(x509);
            }
        }
        if (certificates.isEmpty()) {
            throw new CertificateException("no X.509 certificates found");
        }
        return List.copyOf(certificates);
    }

    private static SSLContext buildContext(List<X509Certificate> additionalCertificates)
            throws GeneralSecurityException, IOException {
        X509TrustManager defaultTrustManager = trustManager(null);
        KeyStore additionalStore = KeyStore.getInstance(KeyStore.getDefaultType());
        additionalStore.load(null, null);
        int index = 0;
        for (X509Certificate certificate : additionalCertificates) {
            additionalStore.setCertificateEntry("rustfs-ca-" + index++, certificate);
        }
        X509TrustManager additionalTrustManager = trustManager(additionalStore);
        X509TrustManager combined = new CombinedTrustManager(defaultTrustManager, additionalTrustManager);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{combined}, new SecureRandom());
        return context;
    }

    private static X509TrustManager trustManager(KeyStore keyStore) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new KeyStoreException("No X509 trust manager available");
    }

    private static IllegalStateException invalidConfiguration(String path, Exception cause) {
        return new IllegalStateException(
                "app.storage.s3.ca-certificate-path must name a readable PEM X.509 certificate file: " + path,
                cause);
    }

    private static final class CombinedTrustManager implements X509TrustManager {
        private final X509TrustManager defaults;
        private final X509TrustManager additional;

        private CombinedTrustManager(X509TrustManager defaults, X509TrustManager additional) {
            this.defaults = defaults;
            this.additional = additional;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                defaults.checkClientTrusted(chain, authType);
            } catch (CertificateException ignored) {
                additional.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                defaults.checkServerTrusted(chain, authType);
            } catch (CertificateException ignored) {
                additional.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] defaultIssuers = defaults.getAcceptedIssuers();
            X509Certificate[] additionalIssuers = additional.getAcceptedIssuers();
            X509Certificate[] combined = new X509Certificate[defaultIssuers.length + additionalIssuers.length];
            System.arraycopy(defaultIssuers, 0, combined, 0, defaultIssuers.length);
            System.arraycopy(additionalIssuers, 0, combined, defaultIssuers.length, additionalIssuers.length);
            return combined;
        }
    }

}
