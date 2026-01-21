# Deployment Guide - MongoDB Troubleshooting Tool

## Important: Rebuild Required

After making code changes, you **MUST** rebuild and redeploy the application for changes to take effect.

## Quick Rebuild & Deploy

### Option 1: Maven + Docker Compose (Recommended)

```bash
# Navigate to project directory
cd projects/tshot

# Clean and build the application
mvn clean package

# Rebuild Docker image and restart
docker-compose down
docker-compose build --no-cache
docker-compose up -d

# View logs
docker-compose logs -f mongo-troubleshoot
```

### Option 2: Docker Build & Run

```bash
# Navigate to project directory
cd projects/tshot

# Build the application
mvn clean package

# Build Docker image
docker build -t mongo-troubleshoot:latest .

# Stop and remove old container
docker stop mongo-troubleshoot
docker rm mongo-troubleshoot

# Run new container
docker run -d \
  --name mongo-troubleshoot \
  -p 9080:9080 \
  -p 9443:9443 \
  mongo-troubleshoot:latest

# View logs
docker logs -f mongo-troubleshoot
```

### Option 3: Kubernetes/OpenShift

```bash
# Build and push image
cd projects/tshot
mvn clean package
docker build -t quay.io/istrate/mongo-troubleshoot:latest .
docker push quay.io/istrate/mongo-troubleshoot:latest

# Delete and recreate pod
kubectl delete pod <pod-name> -n <namespace>

# Or rollout restart
kubectl rollout restart deployment mongo-troubleshoot -n <namespace>

# View logs
kubectl logs -f <pod-name> -n <namespace>
```

## Verify Pagination is Working

### 1. Check Application Logs

Look for pagination parameters in the logs:

```bash
# Docker
docker logs mongo-troubleshoot | grep "Limit:"

# Kubernetes
kubectl logs <pod-name> | grep "Limit:"
```

Expected output:
```
[INFO] com.ibm.mas.tshot.MongoTroubleshootServlet - Limit: 10, Skip: 0
[INFO] com.ibm.mas.tshot.MongoTroubleshootServlet - Total documents matching query: 47
[INFO] com.ibm.mas.tshot.MongoTroubleshootServlet - SUCCESS: Query executed in 125ms, returned 10 documents
```

### 2. Test in Browser

1. Open http://localhost:9080
2. Configure MongoDB connection
3. Execute a query that returns more than 10 results
4. **Verify**: Only 10 results are displayed
5. **Check**: Pagination controls appear at bottom
6. **Test**: Click "Next" to see next 10 results

### 3. Check Browser Console

Open browser developer tools (F12) and check for:
- No JavaScript errors
- Network requests include `limit` and `skip` parameters
- Response includes `totalCount` field

Example request:
```
action=executeQuery
&connectionString=mongodb://...
&database=test
&collection=users
&query={}
&limit=10
&skip=0
```

Example response:
```json
{
  "success": true,
  "count": 10,
  "totalCount": 47,
  "duration": 125,
  "results": [...]
}
```

## Troubleshooting

### Pagination Still Not Working

**Problem**: All results still returned after rebuild

**Solutions**:

1. **Clear Browser Cache**
   ```
   - Chrome: Ctrl+Shift+Delete → Clear cached images and files
   - Firefox: Ctrl+Shift+Delete → Cached Web Content
   - Or use Incognito/Private mode
   ```

2. **Hard Refresh**
   ```
   - Windows: Ctrl+F5
   - Mac: Cmd+Shift+R
   ```

3. **Verify Build**
   ```bash
   # Check WAR file was rebuilt
   ls -lh target/mongo-troubleshoot.war
   
   # Check timestamp is recent
   stat target/mongo-troubleshoot.war
   ```

4. **Verify Docker Image**
   ```bash
   # Check image was rebuilt
   docker images | grep mongo-troubleshoot
   
   # Verify container is using new image
   docker inspect mongo-troubleshoot | grep Image
   ```

5. **Check Application Logs**
   ```bash
   # Look for pagination logs
   docker logs mongo-troubleshoot 2>&1 | grep -A 5 "Execute Query"
   ```

### Build Errors

**Problem**: Maven build fails

**Solution**:
```bash
# Clean Maven cache
mvn clean

# Rebuild with debug
mvn clean package -X

# Skip tests if needed
mvn clean package -DskipTests
```

**Problem**: Docker build fails

**Solution**:
```bash
# Remove old images
docker rmi mongo-troubleshoot:latest

# Build with no cache
docker build --no-cache -t mongo-troubleshoot:latest .
```

### Container Won't Start

**Problem**: Container exits immediately

**Solution**:
```bash
# Check logs
docker logs mongo-troubleshoot

# Run interactively
docker run -it --rm mongo-troubleshoot:latest /bin/bash

# Check Liberty logs
docker exec mongo-troubleshoot cat /logs/messages.log
```

## Verification Checklist

After deployment, verify:

- [ ] Container/Pod is running
- [ ] Application accessible at http://localhost:9080
- [ ] Logs show "Server started" message
- [ ] Query execution logs show `Limit:` and `Skip:` parameters
- [ ] Browser shows pagination controls
- [ ] Only specified number of results displayed
- [ ] Navigation buttons work (Next, Previous, etc.)
- [ ] Page size selector works (10, 20, 50, 100)
- [ ] Total count is displayed correctly

## Performance Notes

### Build Time
- Maven build: ~30-60 seconds
- Docker build: ~2-5 minutes (first time)
- Docker build: ~30-60 seconds (cached layers)

### Startup Time
- Liberty server: ~30-60 seconds
- Application ready: ~60-90 seconds total

### Resource Usage
- Memory: ~512MB-1GB
- CPU: Minimal when idle
- Disk: ~500MB (image size)

## Production Deployment

### Environment Variables

```bash
# Logging
WLP_LOGGING_CONSOLE_FORMAT=simple
WLP_LOGGING_CONSOLE_LOGLEVEL=info
WLP_LOGGING_CONSOLE_SOURCE=message,trace,accessLog,ffdc,audit

# Java Options
JAVA_OPTS=-Xmx1024m -Xms512m
```

### Health Checks

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:9080/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### Resource Limits

```yaml
resources:
  limits:
    memory: "1Gi"
    cpu: "1000m"
  requests:
    memory: "512Mi"
    cpu: "500m"
```

## Support

If pagination still doesn't work after following this guide:

1. Check all logs (application, Liberty, Docker)
2. Verify browser cache is cleared
3. Test in incognito/private mode
4. Check network tab in browser dev tools
5. Verify backend is receiving limit/skip parameters
6. Confirm MongoDB query is using skip/limit

For additional help, review:
- LOGGING.md - Logging configuration
- CERTIFICATE_UPLOAD.md - Certificate setup
- UI_GUIDE.md - UI usage instructions