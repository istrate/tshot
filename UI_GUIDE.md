# MongoDB Troubleshooting Tool - UI Guide

## Certificate Upload Feature

### How to Upload a CA Certificate

The UI has been enhanced with a certificate upload feature in the SSL/TLS section. Here's how to use it:

### Step-by-Step Instructions

#### 1. Enable SSL/TLS

![SSL Enable](docs/ssl-enable.png)

- Locate the "Advanced Options" section in the Connection Configuration card
- Check the **"Enable SSL/TLS"** checkbox
- This will reveal additional SSL options

#### 2. Upload Certificate

![Certificate Upload](docs/cert-upload.png)

Once SSL/TLS is enabled, you'll see:

```
☑ Enable SSL/TLS
    ☐ Allow Invalid Certificates (tlsAllowInvalidCertificates)
    ☐ Allow Invalid Hostnames (tlsAllowInvalidHostnames)
    
    Upload MongoDB CA Certificate (optional)
    [Choose File] No file chosen
    Upload the CA certificate to establish trust with MongoDB server
```

**To upload a certificate:**

1. Click the **"Choose File"** button
2. Select your MongoDB CA certificate file
   - Supported formats: `.pem`, `.crt`, `.cer`
3. The file will upload automatically
4. You'll see a status message:
   - ✓ **Success**: "Certificate uploaded successfully (ID: xxx-xxx-xxx)"
   - ✗ **Error**: Error message explaining what went wrong

#### 3. Configure Connection

After uploading the certificate:

1. Fill in your MongoDB connection details:
   - **Hosts**: MongoDB server addresses
   - **Database Name**: Target database
   - **Username/Password**: Authentication credentials
   - **Replica Set Name**: If using replica sets

2. The uploaded certificate will be automatically used for SSL connections

#### 4. Test Connection

Click the **"Test Connection"** button to verify:
- The connection establishes successfully
- SSL/TLS handshake completes
- Certificate validation passes

### UI Layout

```
┌─────────────────────────────────────────────────────────────┐
│ Connection Configuration                                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ Basic Connection                                             │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ Hosts: [localhost        ] [27017]  [✕]             │   │
│ │        [+ Add Host]                                  │   │
│ │ Database Name: [admin                              ] │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                              │
│ Authentication                                               │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ Username: [admin    ] Password: [••••••]            │   │
│ │ Auth Database: [admin                              ] │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                              │
│ Advanced Options                                             │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ ☑ Enable SSL/TLS                                     │   │
│ │   ☐ Allow Invalid Certificates                       │   │
│ │   ☐ Allow Invalid Hostnames                          │   │
│ │                                                       │   │
│ │   Upload MongoDB CA Certificate (optional)           │   │
│ │   [Choose File] mongodb-ca.pem                       │   │
│ │   Upload the CA certificate to establish trust       │   │
│ │   ✓ Certificate uploaded successfully (ID: 550e...)  │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                              │
│ Connection String Preview                                    │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ mongodb://admin:****@localhost:27017/admin?ssl=true  │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                              │
│ [Test Connection]  [Update Preview]                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Certificate Upload Status Messages

#### Success
```
✓ Certificate uploaded successfully (ID: 550e8400-e29b-41d4-a716-446655440000)
```

#### Uploading
```
Uploading certificate...
```

#### Errors
```
✗ Upload failed: Invalid file type. Only .pem, .crt, and .cer files are allowed
✗ Upload failed: Invalid certificate content
✗ Upload failed (HTTP 500)
✗ Network error
```

### Certificate File Requirements

Your certificate file must:
- Be in PEM, CRT, or CER format
- Contain valid certificate data
- Include BEGIN/END CERTIFICATE markers
- Be less than 10 MB in size

### Example Certificate File

**mongodb-ca.pem:**
```
-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAKL0UG+mRKSzMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX
aWRnaXRzIFB0eSBMdGQwHhcNMTcwODI4MTUyMzI0WhcNMjcwODI2MTUyMzI0WjBF
...
-----END CERTIFICATE-----
```

### Workflow Example

1. **Obtain Certificate**
   ```bash
   # From MongoDB Atlas - download from connection dialog
   # From self-hosted - extract using openssl
   openssl s_client -connect mongodb-host:27017 -showcerts < /dev/null 2>/dev/null | \
     openssl x509 -outform PEM > mongodb-ca.pem
   ```

2. **Open UI**
   - Navigate to http://your-app:9080

3. **Enable SSL**
   - Check "Enable SSL/TLS"

4. **Upload Certificate**
   - Click "Choose File"
   - Select `mongodb-ca.pem`
   - Wait for success message

5. **Configure Connection**
   - Enter MongoDB host, port, credentials
   - Connection string will show `ssl=true`

6. **Test**
   - Click "Test Connection"
   - View results

### Troubleshooting

#### Certificate Won't Upload

**Problem**: File selection doesn't trigger upload

**Solution**:
- Ensure SSL/TLS is enabled first
- Check file extension (.pem, .crt, or .cer)
- Try a different browser
- Check browser console for errors

#### Upload Fails

**Problem**: "Invalid certificate content"

**Solution**:
- Open the file in a text editor
- Verify it contains `-----BEGIN CERTIFICATE-----`
- Ensure it's not encrypted or password-protected
- Try converting to PEM format:
  ```bash
  openssl x509 -in cert.crt -out cert.pem -outform PEM
  ```

#### Connection Still Fails

**Problem**: "PKIX path building failed" after upload

**Solution**:
- Verify you uploaded the correct CA certificate
- Check that the certificate matches your MongoDB server
- Ensure the certificate hasn't expired
- Try checking "Allow Invalid Certificates" temporarily for testing

### Browser Compatibility

The certificate upload feature works with:
- ✅ Chrome/Chromium 90+
- ✅ Firefox 88+
- ✅ Safari 14+
- ✅ Edge 90+

### Security Notes

- Certificates are stored server-side with unique IDs
- Each upload creates a new certificate entry
- Certificates are used only for the current session
- No certificate data is sent to the client after upload

### Additional Features

The UI also provides:
- Multiple host configuration for replica sets
- Read preference selection
- Connection timeout configuration
- Connection pool settings
- Write concern options
- Real-time connection string preview

For more details, see [CERTIFICATE_UPLOAD.md](CERTIFICATE_UPLOAD.md)