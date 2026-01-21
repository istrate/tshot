# MongoDB CA Certificate Upload Feature

## Overview

The MongoDB Troubleshooting Tool now supports uploading CA certificates to establish trust with MongoDB servers using SSL/TLS connections. This feature allows you to securely connect to MongoDB instances with custom or self-signed certificates.

## How It Works

1. **Upload Certificate**: Upload your MongoDB server's CA certificate through the web UI
2. **Certificate Storage**: The certificate is securely stored on the server with a unique ID
3. **Truststore Creation**: A Java truststore is automatically created containing your certificate
4. **SSL Context**: MongoDB connections use the custom truststore for SSL/TLS validation

## Usage Instructions

### Step 1: Enable SSL/TLS

1. Open the MongoDB Troubleshooting Tool web interface
2. In the "Connection Configuration" section, find "Advanced Options"
3. Check the "Enable SSL/TLS" checkbox
4. The SSL options will appear

### Step 2: Upload CA Certificate

1. In the SSL options section, locate "Upload MongoDB CA Certificate"
2. Click "Choose File" and select your CA certificate file
3. Supported formats: `.pem`, `.crt`, `.cer`
4. The certificate will be uploaded automatically
5. You'll see a success message with a certificate ID

### Step 3: Configure Connection

1. Fill in your MongoDB connection details (host, port, credentials, etc.)
2. Ensure SSL/TLS is enabled
3. The uploaded certificate will be automatically used for the connection

### Step 4: Test Connection

1. Click "Test Connection" to verify the SSL connection works
2. The application will use your uploaded certificate to validate the MongoDB server
3. Check the logs for detailed connection information

## Certificate Requirements

### Supported Formats

- **PEM** (`.pem`): Privacy Enhanced Mail format
- **CRT** (`.crt`): Certificate file
- **CER** (`.cer`): Certificate file

### Certificate Content

The certificate file must contain:
- `-----BEGIN CERTIFICATE-----`
- Base64 encoded certificate data
- `-----END CERTIFICATE-----`

### Example Certificate

```
-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAKL0UG+mRKSzMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX
...
-----END CERTIFICATE-----
```

## Obtaining MongoDB CA Certificate

### From MongoDB Atlas

1. Log in to MongoDB Atlas
2. Go to your cluster
3. Click "Connect"
4. Download the CA certificate from the connection dialog

### From Self-Hosted MongoDB

If you're using a self-signed certificate:

```bash
# Extract CA certificate from MongoDB server
openssl s_client -connect mongodb-host:27017 -showcerts < /dev/null 2>/dev/null | \
  openssl x509 -outform PEM > mongodb-ca.pem
```

### From Kubernetes/OpenShift

If MongoDB is running in Kubernetes with a secret:

```bash
# Extract certificate from secret
kubectl get secret mongodb-cert -n namespace -o jsonpath='{.data.ca\.crt}' | base64 -d > mongodb-ca.pem
```

## Technical Details

### Certificate Storage

- **Location**: `/tmp/mongo-certs/`
- **Naming**: `{certificate-id}.pem`
- **Permissions**: Readable by the application user

### Truststore Creation

- **Location**: `/tmp/mongo-truststores/`
- **Format**: Java KeyStore (JKS)
- **Naming**: `truststore-{certificate-id}.jks`
- **Password**: `changeit` (default Java truststore password)

### SSL Context

The application creates a custom SSL context for each connection:

1. Loads the uploaded certificate
2. Creates a Java KeyStore
3. Adds the certificate to the keystore
4. Initializes TrustManagerFactory with the keystore
5. Creates SSL context with the trust managers
6. Configures MongoDB client with the SSL context

## Security Considerations

### Certificate Validation

- Certificates are validated to ensure they contain proper PEM format
- Only `.pem`, `.crt`, and `.cer` files are accepted
- File size limit: 10 MB

### Storage Security

- Certificates are stored in temporary directories
- Each certificate has a unique UUID identifier
- Truststores are created per-certificate to avoid conflicts

### Cleanup

Truststore files can be cleaned up using the `CertificateManager.cleanupOldTruststores()` method.

## Troubleshooting

### Certificate Upload Fails

**Problem**: "Invalid certificate content"

**Solution**: 
- Ensure the file contains `BEGIN CERTIFICATE` and `END CERTIFICATE` markers
- Verify the file is in PEM format
- Check that the file is not corrupted

### SSL Connection Fails

**Problem**: "PKIX path building failed"

**Solution**:
- Verify you uploaded the correct CA certificate
- Ensure SSL/TLS is enabled in the connection settings
- Check that the certificate matches the MongoDB server's certificate chain

### Certificate Not Found

**Problem**: "Certificate not found: {id}"

**Solution**:
- Re-upload the certificate
- Check that the certificate ID is correct
- Verify the `/tmp/mongo-certs/` directory exists and is writable

## API Reference

### Upload Certificate

**Endpoint**: `POST /api/cert`

**Content-Type**: `multipart/form-data`

**Parameters**:
- `certificate`: File (required) - The CA certificate file

**Response**:
```json
{
  "success": true,
  "message": "Certificate uploaded successfully",
  "certificateId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Test Connection with Certificate

**Endpoint**: `POST /api/mongo`

**Parameters**:
- `action`: "testConnection"
- `connectionString`: MongoDB connection string
- `certificateId`: Certificate ID (optional)

## Example Connection Strings

### With SSL/TLS

```
mongodb://username:password@host:27017/database?ssl=true&tls=true
```

### Replica Set with SSL

```
mongodb://username:password@host1:27017,host2:27017,host3:27017/database?replicaSet=rs0&ssl=true&tls=true
```

### MongoDB Atlas

```
mongodb+srv://username:password@cluster.mongodb.net/database?retryWrites=true&w=majority
```

## Logging

All certificate operations are logged:

```
[INFO] com.ibm.mas.tshot.CertificateUploadServlet - Certificate uploaded successfully with ID: 550e8400-e29b-41d4-a716-446655440000
[INFO] com.ibm.mas.tshot.CertificateManager - Creating MongoDB client with certificate ID: 550e8400-e29b-41d4-a716-446655440000
[INFO] com.ibm.mas.tshot.CertificateManager - Truststore created successfully: /tmp/mongo-truststores/truststore-550e8400-e29b-41d4-a716-446655440000.jks
[INFO] com.ibm.mas.tshot.MongoTroubleshootServlet - Using uploaded certificate for connection
```

## Support

For issues or questions:
1. Check the application logs in the pod console
2. Verify certificate format and content
3. Test MongoDB connectivity without SSL first
4. Review the LOGGING.md file for log analysis

## Future Enhancements

Potential improvements:
- Certificate expiration warnings
- Multiple certificate management
- Certificate chain validation
- Automatic certificate renewal
- Certificate metadata display