# MongoDB Troubleshooting Tool

A comprehensive web-based application for troubleshooting MongoDB connection and performance issues, deployed on OpenLiberty.

## Features

- 🔌 **Connection Testing**: Test MongoDB connections with detailed diagnostics
- 🔍 **Query Executor**: Run MongoDB queries directly from the web interface
- 📊 **Performance Statistics**: View database and server statistics
- 🛠️ **Network Tools**: Includes comprehensive network troubleshooting utilities
- 🐚 **MongoDB Shell**: mongosh client included in the container
- 🌐 **Modern UI**: Clean, responsive web interface

## Architecture

- **Frontend**: HTML5, CSS3, JavaScript
- **Backend**: Java Servlet (Jakarta EE 9.1)
- **Application Server**: OpenLiberty
- **Database Driver**: MongoDB Java Driver 4.11.1
- **Container**: Docker with UBI base image

## Prerequisites

- Docker
- Maven (for local development)
- Java 21+ (for local development)
- Access to quay.io (for pushing images)

## Quick Start

### Using Docker

1. **Build the Docker image:**
   ```bash
   # Linux/Mac
   chmod +x build-and-push.sh
   ./build-and-push.sh --build-only

   # Windows PowerShell
   .\build-and-push.ps1 -BuildOnly
   ```

2. **Run the container:**
   ```bash
   docker run -p 9080:9080 quay.io/istrate/mongo-troubleshoot:latest
   ```

3. **Access the application:**
   Open your browser to `http://localhost:9080`

### Local Development

1. **Build with Maven:**
   ```bash
   cd tshot
   mvn clean package
   ```

2. **Run with Liberty:**
   ```bash
   mvn liberty:dev
   ```

3. **Access the application:**
   Open your browser to `http://localhost:9080`

## Building and Pushing to Quay.io

### Linux/Mac

```bash
# Set environment variables
export QUAY_NAMESPACE=istrate
export QUAY_USERNAME=your-username
export QUAY_PASSWORD=your-password

# Build and push
./build-and-push.sh

# Or with options
./build-and-push.sh --tag v1.0.0
./build-and-push.sh --build-only
./build-and-push.sh --test
```

### Windows PowerShell

```powershell
# Set environment variables
$env:QUAY_NAMESPACE = "istrate"
$env:QUAY_USERNAME = "your-username"
$env:QUAY_PASSWORD = "your-password"

# Build and push
.\build-and-push.ps1

# Or with options
.\build-and-push.ps1 -Tag v1.0.0
.\build-and-push.ps1 -BuildOnly
.\build-and-push.ps1 -Test
```

## Using the Application

### 1. Test MongoDB Connection

Enter your MongoDB connection string in the format:
```
mongodb://username:password@host:port/database?authSource=admin
```

Click "Test Connection" to verify connectivity and measure response time.

### 2. Execute Queries

- Enter the database name
- Enter the collection name
- Write your query in JSON format (e.g., `{"status": "active"}`)
- Click "Execute Query" to run the query

### 3. View Statistics

- Enter the database name
- Click "Get Statistics" to view:
  - Database statistics
  - Server status
  - Performance metrics

## Network Troubleshooting Tools

The container includes several network troubleshooting tools:

### Available Tools

- `mongosh` - MongoDB Shell
- `curl` - HTTP client
- `wget` - File downloader
- `telnet` - Network connectivity testing
- `nc` (netcat) - Network utility
- `nmap` - Network scanner
- `tcpdump` - Packet analyzer
- `traceroute` - Route tracing
- `dig` - DNS lookup
- `nslookup` - DNS query
- `ping` - Network connectivity
- `netstat` / `ss` - Network statistics
- `ip` - Network configuration

### Using Tools in Container

```bash
# Connect to running container
docker exec -it <container-name> bash

# Test MongoDB connection
/opt/tools/test-mongo-connection.sh mongodb-host 27017

# MongoDB health check
/opt/tools/mongo-health-check.sh "mongodb://user:pass@host:27017/db"

# Network diagnostics
/opt/tools/network-diagnostics.sh

# Use mongosh directly
mongosh "mongodb://localhost:27017"

# Test network connectivity
nc -zv mongodb-host 27017
telnet mongodb-host 27017
ping mongodb-host
traceroute mongodb-host

# DNS lookup
nslookup mongodb-host
dig mongodb-host

# Check network configuration
ip addr show
ip route show
netstat -tuln
```

## Deployment

### Kubernetes/OpenShift

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mongo-troubleshoot
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mongo-troubleshoot
  template:
    metadata:
      labels:
        app: mongo-troubleshoot
    spec:
      containers:
      - name: mongo-troubleshoot
        image: quay.io/istrate/mongo-troubleshoot:latest
        ports:
        - containerPort: 9080
          name: http
        - containerPort: 9443
          name: https
        livenessProbe:
          httpGet:
            path: /health
            port: 9080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health
            port: 9080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: mongo-troubleshoot
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 9080
    name: http
  selector:
    app: mongo-troubleshoot
```

Deploy with:
```bash
kubectl apply -f deployment.yaml
```

### Docker Compose

```yaml
version: '3.8'
services:
  mongo-troubleshoot:
    image: quay.io/istrate/mongo-troubleshoot:latest
    ports:
      - "9080:9080"
      - "9443:9443"
    environment:
      - WLP_LOGGING_CONSOLE_LOGLEVEL=info
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

## Configuration

### Environment Variables

- `WLP_LOGGING_CONSOLE_FORMAT` - Log format (default: json)
- `WLP_LOGGING_CONSOLE_LOGLEVEL` - Log level (default: info)
- `WLP_LOGGING_CONSOLE_SOURCE` - Log sources

### Server Configuration

Edit `src/main/liberty/config/server.xml` to customize:
- HTTP/HTTPS ports
- Features
- Logging levels
- CORS settings

## Security Considerations

⚠️ **Important Security Notes:**

1. **Authentication**: This tool does not include built-in authentication. Deploy behind a secure gateway or add authentication.
2. **Network Access**: Restrict network access to trusted users only.
3. **Connection Strings**: Never expose MongoDB credentials in logs or UI.
4. **Production Use**: This tool is designed for troubleshooting. Use with caution in production environments.

## Troubleshooting

### Container won't start

```bash
# Check logs
docker logs <container-name>

# Check if port is already in use
netstat -an | grep 9080  # Linux/Mac
Get-NetTCPConnection -LocalPort 9080  # Windows
```

### Cannot connect to MongoDB

1. Verify MongoDB is accessible from the container
2. Check firewall rules
3. Verify connection string format
4. Use network tools in the container:
   ```bash
   docker exec -it <container-name> bash
   /opt/tools/test-mongo-connection.sh <mongodb-host> 27017
   ```

### Application errors

Check Liberty logs:
```bash
docker exec -it <container-name> cat /logs/messages.log
```

## Project Structure

```
tshot/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ibm/mas/tshot/
│   │   │       └── MongoTroubleshootServlet.java
│   │   ├── liberty/
│   │   │   └── config/
│   │   │       └── server.xml
│   │   └── webapp/
│   │       ├── index.html
│   │       └── WEB-INF/
├── Dockerfile
├── pom.xml
├── build-and-push.sh
├── build-and-push.ps1
└── README.md
```

## Contributing

Contributions are welcome! Please ensure:
- Code follows Java conventions
- UI is responsive and accessible
- Docker image builds successfully
- Documentation is updated

## License

Free to use

## Support

For issues and questions:
- Check the troubleshooting section
- Review container logs
- Use the included network diagnostic tools
