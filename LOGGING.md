# Logging Configuration

## Overview

The MongoDB Troubleshooting application uses SLF4J (Simple Logging Facade for Java) with the SLF4J Simple implementation for logging. All logs are configured to output to stdout, making them visible in Kubernetes pod logs.

## Logging Framework

- **Framework**: SLF4J 2.0.9
- **Implementation**: SLF4J Simple
- **Output**: stdout (System.out)

## Configuration

### SLF4J Simple Configuration

The logging behavior is configured in `src/main/resources/simplelogger.properties`:

```properties
# Default log level
org.slf4j.simpleLogger.defaultLogLevel=info

# Application-specific log level
org.slf4j.simpleLogger.log.com.ibm.mas.tshot=info

# Timestamp format (ISO 8601)
org.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd'T'HH:mm:ss.SSSXXX

# Output to stdout
org.slf4j.simpleLogger.logFile=System.out
```

### Liberty Server Configuration

The Liberty server is configured in `src/main/liberty/config/server.xml`:

```xml
<logging traceSpecification="*=info:com.ibm.mas.tshot.*=all"
         maxFileSize="20"
         maxFiles="10"
         consoleLogLevel="INFO" />
```

### Docker/Kubernetes Configuration

The Dockerfile sets environment variables for Liberty logging:

```dockerfile
ENV WLP_LOGGING_CONSOLE_FORMAT=json \
    WLP_LOGGING_CONSOLE_LOGLEVEL=info \
    WLP_LOGGING_CONSOLE_SOURCE=message,trace,accessLog,ffdc
```

## Log Levels

The application uses the following log levels:

- **INFO**: Normal operations, connection attempts, query executions
- **ERROR**: Errors, exceptions, failed operations
- **DEBUG**: Detailed information (e.g., ping responses, connection closures)

## Viewing Logs

### In Kubernetes/OpenShift

View pod logs in real-time:
```bash
kubectl logs -f <pod-name>
```

View logs from a specific container:
```bash
kubectl logs -f <pod-name> -c <container-name>
```

View logs with timestamps:
```bash
kubectl logs --timestamps=true <pod-name>
```

View recent logs:
```bash
kubectl logs --tail=100 <pod-name>
```

### In Docker

View container logs:
```bash
docker logs -f mongo-troubleshoot
```

View logs with timestamps:
```bash
docker logs -t mongo-troubleshoot
```

### Using docker-compose

View service logs:
```bash
docker-compose logs -f mongo-troubleshoot
```

## Log Format

Logs are output in the following format:

```
[LEVEL] yyyy-MM-dd'T'HH:mm:ss.SSSXXX [thread-name] logger-name - message
```

Example:
```
[INFO] 2026-01-19T14:10:15.123+02:00 [Default Executor-thread-1] com.ibm.mas.tshot.MongoTroubleshootServlet - === MongoDB Troubleshoot Request ===
[INFO] 2026-01-19T14:10:15.124+02:00 [Default Executor-thread-1] com.ibm.mas.tshot.MongoTroubleshootServlet - Action: testConnection
[INFO] 2026-01-19T14:10:15.125+02:00 [Default Executor-thread-1] com.ibm.mas.tshot.MongoTroubleshootServlet - Remote Address: 10.0.0.1
```

## Logged Operations

### Test Connection
- Connection attempt
- Ping command execution
- Connection success/failure
- Duration metrics

### Execute Query
- Connection establishment
- Query parsing
- Query execution
- Result count
- Duration metrics

### Get Stats
- Connection establishment
- Database stats retrieval
- Server status retrieval
- Success/failure status

## Security

- **Password Masking**: Connection strings are automatically masked in logs using the `maskPassword()` method
- **Sensitive Data**: Query parameters and results are logged but can be filtered if needed

## Troubleshooting

### Logs not appearing in pod logs

1. Check that the application is running:
   ```bash
   kubectl get pods
   ```

2. Verify the pod is not in CrashLoopBackOff:
   ```bash
   kubectl describe pod <pod-name>
   ```

3. Check Liberty server logs:
   ```bash
   kubectl exec <pod-name> -- cat /logs/messages.log
   ```

### Changing log levels at runtime

To change log levels, update the `simplelogger.properties` file and rebuild the application, or use Liberty's dynamic logging configuration.

### Viewing Liberty-specific logs

Liberty generates additional logs in `/logs/` directory:
- `messages.log`: Server messages
- `trace.log`: Detailed trace information
- `ffdc/`: First Failure Data Capture logs

## Best Practices

1. **Use appropriate log levels**: INFO for normal operations, ERROR for failures, DEBUG for detailed troubleshooting
2. **Include context**: Use parameterized logging (e.g., `logger.info("Action: {}", action)`)
3. **Log exceptions properly**: Always include the exception object as the last parameter
4. **Avoid logging sensitive data**: Passwords and secrets should be masked
5. **Use structured logging**: Consider JSON format for better parsing in log aggregation systems

## Integration with Log Aggregation

The JSON format output from Liberty is compatible with:
- **Elasticsearch/Kibana (ELK Stack)**
- **Splunk**
- **Datadog**
- **CloudWatch Logs**
- **Azure Monitor**
- **Google Cloud Logging**

Configure your log aggregation tool to parse the JSON format for better searchability and analysis.