# Multi-stage build for MongoDB Troubleshooting Application
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage with OpenLiberty
FROM openliberty/open-liberty:25.0.0.6-full-java21-openj9-ubi-minimal


ARG SECRET_MESSAGE="Message from Docker file"

# Add labels
LABEL maintainer="Daniel Istrate" \
      description="MongoDB Troubleshooting Tool in OpenLiberty" \
      version="1.0.6" \
      tools="mongosh,curl,wget,telnet,nc,nmap,tcpdump,traceroute,dig,nslookup"

# Set environment variables for Liberty logging
# Ensure all logs go to console (stdout) for pod visibility in plain text format
ENV WLP_LOGGING_CONSOLE_FORMAT=simple \
    WLP_LOGGING_CONSOLE_LOGLEVEL=info \
    WLP_LOGGING_CONSOLE_SOURCE=message,trace,accessLog,ffdc,audit \
    WLP_LOGGING_MESSAGE_FORMAT=simple \
    WLP_LOGGING_MESSAGE_SOURCE=message,trace,accessLog,ffdc,audit \
    APP_NAME="MongoDB Troubleshooting Tool" \
    MESSAGE=${SECRET_MESSAGE}

    # Install network troubleshooting tools and MongoDB shell
USER root

# Install dnf first using microdnf, then use dnf for all other packages
RUN microdnf install -y dnf && microdnf clean all

# Install network tools, utilities, and MongoDB shell using dnf
# Note: curl-minimal is pre-installed, so we skip curl to avoid conflicts
RUN dnf update -yq \
    dnf install -yq \
    bind-utils \
    iputils \
    net-tools \
    nmap-ncat \
    wget \
    vim \
    procps-ng \
    iproute \
    nano \
    && dnf clean all
# Install MongoDB Shell (mongosh)
# For RHEL/UBI minimal, use RPM installation
COPY repos/mongo7.repo /etc/yum.repos.d/mongodb-org-7.0.repo

RUN dnf install -yq mongodb-mongosh && dnf clean all

# Create directory for network tools scripts
RUN mkdir -p /opt/tools

# Create helpful troubleshooting scripts
COPY tools/test-mongo-connection.sh /opt/tools/test-mongo-connection.sh

COPY tools/mongo-health-check.sh /opt/tools/mongo-health-check.sh

COPY tools/network-diagnostics.sh /opt/tools/network-diagnostics.sh


# Make scripts executable
RUN chmod +x /opt/tools/*.sh

# Switch back to liberty user
USER 1001

# Copy the built WAR file from builder stage
COPY --chown=1001:0 --from=builder /build/target/mongo-troubleshoot.war /config/apps/

# Copy Liberty server configuration
COPY --chown=1001:0 src/main/liberty/config/server.xml /config/

# Configure Liberty
RUN configure.sh

# Expose ports
EXPOSE 9080 9443

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:9080/health || exit 1

# Write secret message


# Run Liberty server
CMD ["bash","-c", "echo $MESSAGE && echo $MESSAGE> /tmp/secret_message && /opt/ol/wlp/bin/server run defaultServer"]