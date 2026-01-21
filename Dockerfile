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

# Add labels
LABEL maintainer="Daniel Istrate" \
      description="MongoDB Troubleshooting Tool in OpenLiberty" \
      version="1.0.0" \
      tools="mongosh,curl,wget,telnet,nc,nmap,tcpdump,traceroute,dig,nslookup"

# Set environment variables for Liberty logging
# Ensure all logs go to console (stdout) for pod visibility in plain text format
ENV WLP_LOGGING_CONSOLE_FORMAT=simple \
    WLP_LOGGING_CONSOLE_LOGLEVEL=info \
    WLP_LOGGING_CONSOLE_SOURCE=message,trace,accessLog,ffdc,audit \
    WLP_LOGGING_MESSAGE_FORMAT=simple \
    WLP_LOGGING_MESSAGE_SOURCE=message,trace,accessLog,ffdc,audit \
    APP_NAME="MongoDB Troubleshooting Tool"

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
RUN cat > /etc/yum.repos.d/mongodb-org-7.0.repo << 'EOF'
[mongodb-org-7.0]
name=MongoDB Repository
baseurl=https://repo.mongodb.org/yum/redhat/9/mongodb-org/7.0/x86_64/
gpgcheck=1
enabled=1
gpgkey=https://www.mongodb.org/static/pgp/server-7.0.asc
EOF

RUN dnf install -yq mongodb-mongosh && dnf clean all

# Create directory for network tools scripts
RUN mkdir -p /opt/tools

# Create helpful troubleshooting scripts
RUN cat > /opt/tools/test-mongo-connection.sh << 'EOF'
#!/bin/bash
echo "=== MongoDB Connection Test ==="
echo "Usage: ./test-mongo-connection.sh <host> <port>"
HOST=${1:-localhost}
PORT=${2:-27017}
echo "Testing connection to $HOST:$PORT"
echo ""
echo "1. DNS Resolution:"
nslookup $HOST || host $HOST
echo ""
echo "2. Ping Test:"
ping -c 3 $HOST || echo "Ping failed or not allowed"
echo ""
echo "3. Port Connectivity:"
nc -zv $HOST $PORT
echo ""
echo "4. Telnet Test:"
timeout 5 telnet $HOST $PORT || echo "Telnet test completed"
echo ""
echo "5. Traceroute:"
traceroute -m 10 $HOST || echo "Traceroute completed"
EOF

RUN cat > /opt/tools/mongo-health-check.sh << 'EOF'
#!/bin/bash
echo "=== MongoDB Health Check ==="
CONNECTION_STRING=${1:-"mongodb://localhost:27017"}
echo "Connection String: $CONNECTION_STRING"
echo ""
mongosh "$CONNECTION_STRING" --eval "
  print('=== Server Status ===');
  printjson(db.serverStatus());
  print('');
  print('=== Database Stats ===');
  printjson(db.stats());
  print('');
  print('=== Current Operations ===');
  printjson(db.currentOp());
"
EOF

RUN cat > /opt/tools/network-diagnostics.sh << 'EOF'
#!/bin/bash
echo "=== Network Diagnostics ==="
echo ""
echo "1. Network Interfaces:"
ip addr show
echo ""
echo "2. Routing Table:"
ip route show
echo ""
echo "3. DNS Configuration:"
cat /etc/resolv.conf
echo ""
echo "4. Active Connections:"
netstat -tuln || ss -tuln
echo ""
echo "5. Listening Ports:"
netstat -tlnp || ss -tlnp
EOF

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

# Run Liberty server
CMD ["/opt/ol/wlp/bin/server", "run", "defaultServer"]
