package com.dani.mongo.tshoot;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/config")
public class ConfigServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ConfigServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // Get application name from environment variable, default to "MongoDB Troubleshooting Tool"
            String appName = System.getenv("APP_NAME");
            if (appName == null || appName.trim().isEmpty()) {
                appName = "MongoDB Troubleshooting Tool";
            }

            // Get hostname
            String hostname = "unknown";
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                logger.warn("Failed to get hostname: {}", e.getMessage());
                // Try environment variable as fallback
                String envHostname = System.getenv("HOSTNAME");
                if (envHostname != null && !envHostname.trim().isEmpty()) {
                    hostname = envHostname;
                }
            }

            logger.info("Config request - App Name: {}, Hostname: {}", appName, hostname);

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"appName\": \"").append(escapeJson(appName)).append("\",");
            json.append("\"hostname\": \"").append(escapeJson(hostname)).append("\"");
            json.append("}");

            out.print(json.toString());

        } catch (Exception e) {
            logger.error("Error getting configuration", e);
            
            // Return error response
            out.print("{");
            out.print("\"appName\": \"MongoDB Troubleshooting Tool\",");
            out.print("\"hostname\": \"unknown\",");
            out.print("\"error\": \"" + escapeJson(e.getMessage()) + "\"");
            out.print("}");
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}