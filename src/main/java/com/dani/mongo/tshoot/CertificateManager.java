package com.dani.mongo.tshoot;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public class CertificateManager {

    private static final Logger logger = LoggerFactory.getLogger(CertificateManager.class);
    private static final String CERT_STORAGE_DIR = "/tmp/mongo-certs";
    private static final String TRUSTSTORE_DIR = "/tmp/mongo-truststores";
    private static final String TRUSTSTORE_PASSWORD = "changeit";

    /**
     * Create a MongoDB client with custom truststore containing the uploaded certificate
     */
    public static MongoClient createMongoClientWithCertificate(String connectionString, String certificateId) 
            throws Exception {

        logger.info("Creating MongoDB client with certificate ID: {}", certificateId);

        // Get certificate path
        Path certPath = Paths.get(CERT_STORAGE_DIR, certificateId + ".pem");
        if (!Files.exists(certPath)) {
            throw new IllegalArgumentException("Certificate not found: " + certificateId);
        }

        // Create truststore with the certificate
        Path truststorePath = createTruststore(certPath, certificateId);

        // Create SSL context with the truststore
        SSLContext sslContext = createSSLContext(truststorePath);

        // Build MongoDB client settings
        ConnectionString connString = new ConnectionString(connectionString);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .applyToSslSettings(builder -> {
                    builder.enabled(true);
                    builder.context(sslContext);
                })
                .build();

        logger.info("MongoDB client created successfully with custom certificate");
        return MongoClients.create(settings);
    }

    /**
     * Create a truststore containing the specified certificate
     */
    private static Path createTruststore(Path certPath, String certificateId) throws Exception {
        logger.info("Creating truststore for certificate: {}", certificateId);

        // Create truststore directory if it doesn't exist
        Path truststoreDir = Paths.get(TRUSTSTORE_DIR);
        if (!Files.exists(truststoreDir)) {
            Files.createDirectories(truststoreDir);
        }

        // Generate unique truststore filename
        String truststoreFileName = "truststore-" + certificateId + ".jks";
        Path truststorePath = truststoreDir.resolve(truststoreFileName);

        // Load the certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (FileInputStream fis = new FileInputStream(certPath.toFile())) {
            cert = cf.generateCertificate(fis);
            logger.info("Certificate loaded: {}", cert.getType());
        }

        // Create a new keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, TRUSTSTORE_PASSWORD.toCharArray());

        // Add the certificate to the keystore
        String alias = "mongodb-ca-" + UUID.randomUUID().toString();
        keyStore.setCertificateEntry(alias, cert);
        logger.info("Certificate added to truststore with alias: {}", alias);

        // Save the keystore to file
        try (FileOutputStream fos = new FileOutputStream(truststorePath.toFile())) {
            keyStore.store(fos, TRUSTSTORE_PASSWORD.toCharArray());
        }

        logger.info("Truststore created successfully: {}", truststorePath);
        return truststorePath;
    }

    /**
     * Create SSL context from truststore
     */
    private static SSLContext createSSLContext(Path truststorePath) throws Exception {
        logger.info("Creating SSL context from truststore: {}", truststorePath);

        // Load the truststore
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(truststorePath.toFile())) {
            trustStore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
        }

        // Initialize TrustManagerFactory with the truststore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        logger.info("SSL context created successfully");
        return sslContext;
    }

    /**
     * Clean up old truststore files
     */
    public static void cleanupOldTruststores() {
        try {
            Path truststoreDir = Paths.get(TRUSTSTORE_DIR);
            if (Files.exists(truststoreDir)) {
                Files.list(truststoreDir)
                    .filter(path -> path.toString().endsWith(".jks"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            logger.debug("Deleted old truststore: {}", path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete truststore: {}", path, e);
                        }
                    });
            }
        } catch (IOException e) {
            logger.error("Error cleaning up old truststores", e);
        }
    }

    /**
     * Delete a specific certificate and its truststore
     */
    public static void deleteCertificate(String certificateId) {
        try {
            // Delete certificate file
            Path certPath = Paths.get(CERT_STORAGE_DIR, certificateId + ".pem");
            Files.deleteIfExists(certPath);
            logger.info("Deleted certificate: {}", certPath);

            // Delete associated truststore
            Path truststorePath = Paths.get(TRUSTSTORE_DIR, "truststore-" + certificateId + ".jks");
            Files.deleteIfExists(truststorePath);
            logger.info("Deleted truststore: {}", truststorePath);

        } catch (IOException e) {
            logger.error("Error deleting certificate: {}", certificateId, e);
        }
    }
}