package com.dani.mongo.tshoot;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

@WebServlet("/api/cert")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,    // 1 MB
    maxFileSize = 1024 * 1024 * 10,      // 10 MB
    maxRequestSize = 1024 * 1024 * 10    // 10 MB
)
public class CertificateUploadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CertificateUploadServlet.class);
    private static final String CERT_STORAGE_DIR = "/tmp/mongo-certs";

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            // Create certificate storage directory
            Path certDir = Paths.get(CERT_STORAGE_DIR);
            if (!Files.exists(certDir)) {
                Files.createDirectories(certDir);
                logger.info("Created certificate storage directory: {}", CERT_STORAGE_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create certificate storage directory", e);
            throw new ServletException("Failed to initialize certificate storage", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            logger.info("=== Certificate Upload Request ===");
            logger.info("Remote Address: {}", request.getRemoteAddr());

            Part filePart = request.getPart("certificate");
            
            if (filePart == null) {
                logger.error("No certificate file provided");
                writeJsonResponse(out, false, "No certificate file provided", null);
                return;
            }

            String fileName = getFileName(filePart);
            logger.info("Uploading certificate file: {}", fileName);

            // Validate file extension
            if (!isValidCertificateFile(fileName)) {
                logger.error("Invalid certificate file type: {}", fileName);
                writeJsonResponse(out, false, "Invalid file type. Only .pem, .crt, and .cer files are allowed", null);
                return;
            }

            // Generate unique certificate ID
            String certificateId = UUID.randomUUID().toString();
            String storedFileName = certificateId + ".pem";
            Path certPath = Paths.get(CERT_STORAGE_DIR, storedFileName);

            // Save the certificate file
            try (InputStream fileContent = filePart.getInputStream()) {
                Files.copy(fileContent, certPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Certificate saved successfully: {}", certPath);
            }

            // Validate certificate content
            if (!validateCertificateContent(certPath)) {
                Files.deleteIfExists(certPath);
                logger.error("Invalid certificate content");
                writeJsonResponse(out, false, "Invalid certificate content", null);
                return;
            }

            logger.info("Certificate uploaded successfully with ID: {}", certificateId);
            writeJsonResponse(out, true, "Certificate uploaded successfully", certificateId);

        } catch (Exception e) {
            logger.error("Error uploading certificate: {}", e.getMessage(), e);
            writeJsonResponse(out, false, "Error uploading certificate: " + e.getMessage(), null);
        } finally {
            logger.info("=== Certificate Upload Complete ===");
        }
    }

    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition != null) {
            for (String token : contentDisposition.split(";")) {
                if (token.trim().startsWith("filename")) {
                    return token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                }
            }
        }
        return "unknown";
    }

    private boolean isValidCertificateFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".pem") || 
               lowerFileName.endsWith(".crt") || 
               lowerFileName.endsWith(".cer");
    }

    private boolean validateCertificateContent(Path certPath) {
        try {
            String content = Files.readString(certPath);
            // Basic validation: check if it looks like a PEM certificate
            return content.contains("BEGIN CERTIFICATE") && content.contains("END CERTIFICATE");
        } catch (IOException e) {
            logger.error("Error validating certificate content", e);
            return false;
        }
    }

    private void writeJsonResponse(PrintWriter out, boolean success, String message, String certificateId) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\": ").append(success).append(",");
        json.append("\"message\": \"").append(escapeJson(message)).append("\"");
        if (certificateId != null) {
            json.append(",\"certificateId\": \"").append(escapeJson(certificateId)).append("\"");
        }
        json.append("}");
        out.print(json.toString());
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}