package com.dani.mongo.tshoot;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/mongo")
public class MongoTroubleshootServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MongoTroubleshootServlet.class);
    private static final String SESSION_MONGO_CLIENT = "mongoClient";
    private static final String SESSION_CONNECTION_STRING = "connectionString";
    private static final String SESSION_CERTIFICATE_ID = "certificateId";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = null;
        try {
            out = response.getWriter();
            String action = request.getParameter("action");

            logger.info("=== MongoDB Troubleshoot Request ===");
            logger.info("Action: {}", action);
            logger.info("Remote Address: {}", request.getRemoteAddr());
            logger.info("Timestamp: {}", new java.util.Date());

            if (action == null || action.trim().isEmpty()) {
                logger.error("No action specified");
                writeJsonResponse(out, false, "No action specified", 0);
                return;
            }

            if ("openConnection".equals(action)) {
                handleOpenConnection(request, out);
            } else if ("closeConnection".equals(action)) {
                handleCloseConnection(request, out);
            } else if ("testConnection".equals(action)) {
                handleTestConnection(request, out);
            } else if ("executeQuery".equals(action)) {
                handleExecuteQuery(request, out);
            } else if ("getStats".equals(action)) {
                handleGetStats(request, out);
            } else if ("executeMongosh".equals(action)) {
                handleExecuteMongosh(request, out);
            } else {
                logger.error("Unknown action: {}", action);
                writeJsonResponse(out, false, "Unknown action: " + action, 0);
            }
        } catch (Exception e) {
            logger.error("Exception in doPost: {}", e.getMessage(), e);
            if (out != null) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                writeJsonResponse(out, false, errorMsg, 0);
            }
        } finally {
            if (out != null) {
                out.flush();
            }
            logger.info("=== Request Complete ===");
        }
    }

    private void writeJsonResponse(PrintWriter out, boolean success, String message, long duration) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\": ").append(success).append(",");
        json.append("\"message\": \"").append(escapeJson(message)).append("\"");
        if (duration > 0) {
            json.append(",\"duration\": ").append(duration);
        }
        json.append("}");
        out.print(json.toString());
    }

    private void handleOpenConnection(HttpServletRequest request, PrintWriter out) {
        String connectionString = request.getParameter("connectionString");
        String certificateId = request.getParameter("certificateId");

        logger.info("--- Open Connection ---");
        logger.info("Connection String: {}", maskPassword(connectionString));
        logger.info("Certificate ID: {}", certificateId != null ? certificateId : "none");

        if (connectionString == null || connectionString.trim().isEmpty()) {
            logger.error("Connection string is required");
            writeJsonResponse(out, false, "Connection string is required", 0);
            return;
        }

        HttpSession session = request.getSession(true);
        
        // Close existing connection if any
        MongoClient existingClient = (MongoClient) session.getAttribute(SESSION_MONGO_CLIENT);
        if (existingClient != null) {
            logger.info("Closing existing connection before opening new one");
            try {
                existingClient.close();
            } catch (Exception e) {
                logger.warn("Error closing existing connection: {}", e.getMessage());
            }
        }

        long startTime = System.currentTimeMillis();
        MongoClient mongoClient = null;

        try {
            logger.info("Creating new MongoDB connection...");
            
            // Create MongoDB client with or without certificate
            if (certificateId != null && !certificateId.trim().isEmpty()) {
                logger.info("Using uploaded certificate for connection");
                mongoClient = CertificateManager.createMongoClientWithCertificate(connectionString, certificateId);
            } else {
                mongoClient = MongoClients.create(connectionString);
            }
            
            logger.info("Connection established, sending ping command...");
            MongoDatabase database = mongoClient.getDatabase("admin");
            Document ping = database.runCommand(new Document("ping", 1));
            long duration = System.currentTimeMillis() - startTime;

            // Store connection in session
            session.setAttribute(SESSION_MONGO_CLIENT, mongoClient);
            session.setAttribute(SESSION_CONNECTION_STRING, connectionString);
            session.setAttribute(SESSION_CERTIFICATE_ID, certificateId);

            logger.info("SUCCESS: Connection opened and stored in session");
            logger.info("Ping response: {}", ping.toJson());
            logger.info("Duration: {}ms", duration);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true,");
            json.append("\"message\": \"Connection opened successfully and will remain active\",");
            json.append("\"duration\": ").append(duration).append(",");
            json.append("\"sessionId\": \"").append(session.getId()).append("\",");
            json.append("\"ping\": ").append(ping.toJson());
            json.append("}");
            out.print(json.toString());
        } catch (Exception e) {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception closeEx) {
                    logger.error("Error closing MongoDB client after failure: {}", closeEx.getMessage());
                }
            }
            
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Connection failed: " + e.getClass().getSimpleName();
            
            logger.error("Connection failed");
            logger.error("Error message: {}", errorMsg, e);
            
            writeJsonResponse(out, false, errorMsg, 0);
        }
    }

    private void handleCloseConnection(HttpServletRequest request, PrintWriter out) {
        logger.info("--- Close Connection ---");

        HttpSession session = request.getSession(false);
        if (session == null) {
            logger.warn("No session found");
            writeJsonResponse(out, false, "No active session", 0);
            return;
        }

        MongoClient mongoClient = (MongoClient) session.getAttribute(SESSION_MONGO_CLIENT);
        if (mongoClient == null) {
            logger.warn("No active connection in session");
            writeJsonResponse(out, false, "No active connection", 0);
            return;
        }

        try {
            logger.info("Closing MongoDB connection...");
            mongoClient.close();
            
            // Remove from session
            session.removeAttribute(SESSION_MONGO_CLIENT);
            session.removeAttribute(SESSION_CONNECTION_STRING);
            session.removeAttribute(SESSION_CERTIFICATE_ID);

            logger.info("SUCCESS: Connection closed");
            writeJsonResponse(out, true, "Connection closed successfully", 0);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Error closing connection: " + e.getClass().getSimpleName();
            
            logger.error("Error closing connection");
            logger.error("Error message: {}", errorMsg, e);
            
            writeJsonResponse(out, false, errorMsg, 0);
        }
    }

    private MongoClient getOrCreateMongoClient(HttpServletRequest request) throws Exception {
        HttpSession session = request.getSession(false);
        
        if (session != null) {
            MongoClient mongoClient = (MongoClient) session.getAttribute(SESSION_MONGO_CLIENT);
            if (mongoClient != null) {
                logger.info("Using existing connection from session");
                return mongoClient;
            }
        }
        
        // No session connection, create temporary one
        logger.info("No session connection found, creating temporary connection");
        String connectionString = request.getParameter("connectionString");
        String certificateId = request.getParameter("certificateId");
        
        if (connectionString == null || connectionString.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection string is required");
        }
        
        if (certificateId != null && !certificateId.trim().isEmpty()) {
            return CertificateManager.createMongoClientWithCertificate(connectionString, certificateId);
        } else {
            return MongoClients.create(connectionString);
        }
    }

    private void handleTestConnection(HttpServletRequest request, PrintWriter out) {
        logger.info("--- Test Connection ---");

        long startTime = System.currentTimeMillis();
        MongoClient mongoClient = null;
        boolean isSessionConnection = false;

        try {
            mongoClient = getOrCreateMongoClient(request);
            HttpSession session = request.getSession(false);
            isSessionConnection = (session != null && session.getAttribute(SESSION_MONGO_CLIENT) != null);
            
            logger.info("Using {} connection", isSessionConnection ? "session" : "temporary");
            logger.info("Sending ping command...");
            
            MongoDatabase database = mongoClient.getDatabase("admin");
            Document ping = database.runCommand(new Document("ping", 1));
            long duration = System.currentTimeMillis() - startTime;

            logger.info("SUCCESS: Connection test successful in {}ms", duration);
            logger.debug("Ping response: {}", ping.toJson());

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true,");
            json.append("\"message\": \"Connection test successful\",");
            json.append("\"isSessionConnection\": ").append(isSessionConnection).append(",");
            json.append("\"duration\": ").append(duration).append(",");
            json.append("\"response\": ").append(ping.toJson());
            json.append("}");
            out.print(json.toString());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Connection test failed: " + e.getClass().getSimpleName();

            logger.error("MongoDB connection test failed after {}ms", duration);
            logger.error("Error message: {}", errorMsg, e);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": false,");
            json.append("\"message\": \"").append(escapeJson(errorMsg)).append("\",");
            json.append("\"duration\": ").append(duration);
            json.append("}");
            out.print(json.toString());
        } finally {
            // Only close if it's a temporary connection
            if (mongoClient != null && !isSessionConnection) {
                try {
                    logger.debug("Closing temporary MongoDB client...");
                    mongoClient.close();
                    logger.debug("Temporary MongoDB client closed successfully");
                } catch (Exception e) {
                    logger.error("Error closing MongoDB client: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void handleExecuteQuery(HttpServletRequest request, PrintWriter out) {
        String databaseName = request.getParameter("database");
        String collectionName = request.getParameter("collection");
        String queryJson = request.getParameter("query");

        logger.info("--- Execute Query ---");
        logger.info("Database: {}", databaseName);
        logger.info("Collection: {}", collectionName);
        logger.info("Query: {}", queryJson);

        if (databaseName == null || collectionName == null || queryJson == null) {
            logger.error("Missing required parameters");
            writeJsonResponse(out, false, "Missing required parameters", 0);
            return;
        }

        long startTime = System.currentTimeMillis();
        MongoClient mongoClient = null;
        boolean isSessionConnection = false;

        try {
            mongoClient = getOrCreateMongoClient(request);
            HttpSession session = request.getSession(false);
            isSessionConnection = (session != null && session.getAttribute(SESSION_MONGO_CLIENT) != null);
            
            logger.info("Using {} connection", isSessionConnection ? "session" : "temporary");
            
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);

            logger.info("Parsing query...");
            Document query = Document.parse(queryJson);
            List<Document> results = new ArrayList<>();

            logger.info("Executing query (limit 100)...");
            collection.find(query).limit(100).into(results);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("SUCCESS: Query executed in {}ms, found {} documents", duration, results.size());

            StringBuilder jsonResults = new StringBuilder("[");
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) jsonResults.append(",");
                jsonResults.append(results.get(i).toJson());
            }
            jsonResults.append("]");

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true,");
            json.append("\"count\": ").append(results.size()).append(",");
            json.append("\"duration\": ").append(duration).append(",");
            json.append("\"results\": ").append(jsonResults.toString());
            json.append("}");
            out.print(json.toString());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Query execution failed: " + e.getClass().getSimpleName();

            logger.error("Query execution failed after {}ms", duration);
            logger.error("Error message: {}", errorMsg, e);

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": false,");
            json.append("\"message\": \"").append(escapeJson(errorMsg)).append("\",");
            json.append("\"duration\": ").append(duration);
            json.append("}");
            out.print(json.toString());
        } finally {
            // Only close if it's a temporary connection
            if (mongoClient != null && !isSessionConnection) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    logger.error("Error closing MongoDB client: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void handleGetStats(HttpServletRequest request, PrintWriter out) {
        String databaseName = request.getParameter("database");

        logger.info("--- Get Database Stats ---");
        logger.info("Database: {}", databaseName);

        if (databaseName == null) {
            logger.error("Missing required parameters");
            writeJsonResponse(out, false, "Missing required parameters", 0);
            return;
        }

        MongoClient mongoClient = null;
        boolean isSessionConnection = false;

        try {
            mongoClient = getOrCreateMongoClient(request);
            HttpSession session = request.getSession(false);
            isSessionConnection = (session != null && session.getAttribute(SESSION_MONGO_CLIENT) != null);
            
            logger.info("Using {} connection", isSessionConnection ? "session" : "temporary");
            
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            
            logger.info("Retrieving database stats...");
            Document stats = database.runCommand(new Document("dbStats", 1));
            
            logger.info("Retrieving server status...");
            Document serverStatus = database.runCommand(new Document("serverStatus", 1));

            logger.info("SUCCESS: Stats retrieved successfully");

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true,");
            json.append("\"dbStats\": ").append(stats.toJson()).append(",");
            json.append("\"serverStatus\": ").append(serverStatus.toJson());
            json.append("}");
            out.print(json.toString());
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Stats retrieval failed: " + e.getClass().getSimpleName();
            
            logger.error("Stats retrieval failed");
            logger.error("Error message: {}", errorMsg, e);
            
            writeJsonResponse(out, false, errorMsg, 0);
        } finally {
            // Only close if it's a temporary connection
            if (mongoClient != null && !isSessionConnection) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    logger.error("Error closing MongoDB client: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void handleExecuteMongosh(HttpServletRequest request, PrintWriter out) {
        String databaseName = request.getParameter("database");
        String command = request.getParameter("command");

        logger.info("--- Execute Mongosh Command ---");
        logger.info("Database: {}", databaseName);
        logger.info("Command: {}", command);

        if (databaseName == null || databaseName.trim().isEmpty()) {
            logger.error("Database name is required");
            writeJsonResponse(out, false, "Database name is required", 0);
            return;
        }

        if (command == null || command.trim().isEmpty()) {
            logger.error("Command is required");
            writeJsonResponse(out, false, "Command is required", 0);
            return;
        }

        MongoClient mongoClient = null;
        boolean isSessionConnection = false;

        try {
            mongoClient = getOrCreateMongoClient(request);
            HttpSession session = request.getSession(false);
            isSessionConnection = (session != null && session.getAttribute(SESSION_MONGO_CLIENT) != null);
            
            logger.info("Using {} connection", isSessionConnection ? "session" : "temporary");

            MongoDatabase database = mongoClient.getDatabase(databaseName);
            
            // Parse the mongosh command
            MongoshCommandResult result = parseMongoshCommand(mongoClient, database, command);
            
            logger.info("SUCCESS: Command executed successfully");
            logger.info("Operation: {}, Collection: {}, Result count: {}",
                result.operation, result.collection, result.resultCount);

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true,");
            json.append("\"database\": \"").append(escapeJson(databaseName)).append("\",");
            json.append("\"command\": \"").append(escapeJson(command)).append("\",");
            json.append("\"operation\": \"").append(escapeJson(result.operation)).append("\",");
            json.append("\"collection\": \"").append(escapeJson(result.collection)).append("\",");
            json.append("\"resultCount\": ").append(result.resultCount).append(",");
            json.append("\"results\": ");
            
            if (result.results != null) {
                json.append("[");
                for (int i = 0; i < result.results.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(result.results.get(i).toJson());
                }
                json.append("]");
            } else if (result.scalarResult != null) {
                json.append(result.scalarResult);
            } else {
                json.append("null");
            }
            
            json.append("}");
            out.print(json.toString());
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Command execution failed: " + e.getClass().getSimpleName();
            
            logger.error("Mongosh command execution failed");
            logger.error("Error message: {}", errorMsg, e);
            
            writeJsonResponse(out, false, errorMsg, 0);
        } finally {
            // Only close if it's a temporary connection
            if (mongoClient != null && !isSessionConnection) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    logger.error("Error closing MongoDB client: {}", e.getMessage(), e);
                }
            }
        }
    }

    private MongoshCommandResult parseMongoshCommand(MongoClient mongoClient, MongoDatabase database, String command) throws Exception {
        command = command.trim();
        
        // Handle "show" commands
        if (command.startsWith("show ")) {
            return handleShowCommand(mongoClient, database, command);
        }
        
        // Pattern: db.collection.operation(args) or db.operation(args)
        if (!command.startsWith("db.")) {
            throw new IllegalArgumentException("Command must start with 'db.' or 'show'");
        }
        
        // Extract what comes after "db."
        String afterDb = command.substring(3); // Remove "db."
        int dotIndex = afterDb.indexOf('.');
        
        // Check if this is a database-level operation (no collection specified)
        if (dotIndex == -1) {
            return handleDatabaseOperation(database, afterDb);
        }
        
        // Collection-level operation
        String collectionName = afterDb.substring(0, dotIndex);
        String operationPart = afterDb.substring(dotIndex + 1);
        
        // Extract operation name and arguments
        int parenIndex = operationPart.indexOf('(');
        if (parenIndex == -1) {
            throw new IllegalArgumentException("Invalid command format. Missing parentheses");
        }
        
        String operation = operationPart.substring(0, parenIndex);
        String argsString = operationPart.substring(parenIndex + 1, operationPart.lastIndexOf(')')).trim();
        
        logger.info("Parsed command - Collection: {}, Operation: {}, Args: {}",
            collectionName, operation, argsString);
        
        MongoCollection<Document> collection = database.getCollection(collectionName);
        MongoshCommandResult result = new MongoshCommandResult();
        result.collection = collectionName;
        result.operation = operation;
        
        switch (operation) {
            case "countDocuments":
                Document countQuery = argsString.isEmpty() ? new Document() : Document.parse(argsString);
                long count = collection.countDocuments(countQuery);
                result.scalarResult = String.valueOf(count);
                result.resultCount = 1;
                break;
                
            case "find":
                Document findQuery = argsString.isEmpty() ? new Document() : Document.parse(argsString);
                List<Document> findResults = new ArrayList<>();
                collection.find(findQuery).limit(100).into(findResults);
                result.results = findResults;
                result.resultCount = findResults.size();
                break;
                
            case "findOne":
                Document findOneQuery = argsString.isEmpty() ? new Document() : Document.parse(argsString);
                Document findOneResult = collection.find(findOneQuery).first();
                result.results = new ArrayList<>();
                if (findOneResult != null) {
                    result.results.add(findOneResult);
                    result.resultCount = 1;
                } else {
                    result.resultCount = 0;
                }
                break;
                
            case "distinct":
                // Parse: distinct("field", {query})
                String[] distinctArgs = argsString.split(",", 2);
                if (distinctArgs.length == 0) {
                    throw new IllegalArgumentException("distinct requires a field name");
                }
                String field = distinctArgs[0].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                Document distinctQuery = distinctArgs.length > 1 ? Document.parse(distinctArgs[1].trim()) : new Document();
                List<String> distinctValues = collection.distinct(field, distinctQuery, String.class).into(new ArrayList<>());
                result.results = new ArrayList<>();
                for (String value : distinctValues) {
                    result.results.add(new Document("value", value));
                }
                result.resultCount = distinctValues.size();
                break;
                
            case "aggregate":
                // Parse aggregation pipeline
                List<Document> pipeline = new ArrayList<>();
                if (!argsString.isEmpty()) {
                    // Assuming args is an array: [{$match: {...}}, {$group: {...}}]
                    if (argsString.startsWith("[")) {
                        String pipelineStr = argsString.substring(1, argsString.lastIndexOf(']'));
                        // Simple parsing - split by }, { pattern
                        String[] stages = pipelineStr.split("\\},\\s*\\{");
                        for (String stage : stages) {
                            stage = stage.trim();
                            if (!stage.startsWith("{")) stage = "{" + stage;
                            if (!stage.endsWith("}")) stage = stage + "}";
                            pipeline.add(Document.parse(stage));
                        }
                    } else {
                        pipeline.add(Document.parse(argsString));
                    }
                }
                List<Document> aggResults = new ArrayList<>();
                collection.aggregate(pipeline).into(aggResults);
                result.results = aggResults;
                result.resultCount = aggResults.size();
                break;
                
            case "getIndexes":
                // Get all indexes for the collection
                List<Document> indexes = new ArrayList<>();
                collection.listIndexes().into(indexes);
                result.results = indexes;
                result.resultCount = indexes.size();
                logger.info("Retrieved {} indexes for collection {}", indexes.size(), collectionName);
                break;
                
            case "stats":
                // Get collection statistics
                Document statsCommand = new Document("collStats", collectionName);
                if (!argsString.isEmpty()) {
                    // Parse optional scale parameter: stats(1024) or stats({scale: 1024})
                    try {
                        if (argsString.startsWith("{")) {
                            Document statsOptions = Document.parse(argsString);
                            statsCommand.putAll(statsOptions);
                        } else {
                            int scale = Integer.parseInt(argsString);
                            statsCommand.append("scale", scale);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse stats arguments, using defaults: {}", e.getMessage());
                    }
                }
                Document statsResult = database.runCommand(statsCommand);
                result.results = new ArrayList<>();
                result.results.add(statsResult);
                result.resultCount = 1;
                logger.info("Retrieved stats for collection {}", collectionName);
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        
        return result;
    }
    
    private MongoshCommandResult handleShowCommand(MongoClient mongoClient, MongoDatabase database, String command) throws Exception {
        MongoshCommandResult result = new MongoshCommandResult();
        result.operation = command;
        
        if (command.equals("show collections") || command.equals("show tables")) {
            // List all collections in the current database
            List<Document> collections = new ArrayList<>();
            for (String collectionName : database.listCollectionNames()) {
                collections.add(new Document("name", collectionName));
            }
            result.results = collections;
            result.resultCount = collections.size();
            logger.info("Listed {} collections in database {}", collections.size(), database.getName());
            
        } else if (command.equals("show dbs") || command.equals("show databases")) {
            // List all databases - requires admin access
            List<Document> databases = new ArrayList<>();
            for (String dbName : mongoClient.listDatabaseNames()) {
                databases.add(new Document("name", dbName));
            }
            result.results = databases;
            result.resultCount = databases.size();
            logger.info("Listed {} databases", databases.size());
            
        } else {
            throw new IllegalArgumentException("Unsupported show command: " + command);
        }
        
        return result;
    }
    
    private MongoshCommandResult handleDatabaseOperation(MongoDatabase database, String operationPart) throws Exception {
        MongoshCommandResult result = new MongoshCommandResult();
        
        // Extract operation name and arguments
        int parenIndex = operationPart.indexOf('(');
        if (parenIndex == -1) {
            throw new IllegalArgumentException("Invalid command format. Missing parentheses");
        }
        
        String operation = operationPart.substring(0, parenIndex);
        String argsString = operationPart.substring(parenIndex + 1, operationPart.lastIndexOf(')')).trim();
        
        result.operation = operation;
        logger.info("Parsed database operation: {}, Args: {}", operation, argsString);
        
        switch (operation) {
            case "serverStatus":
                // Get server status
                Document serverStatusResult = database.runCommand(new Document("serverStatus", 1));
                
                // Check if we need to extract a nested property
                // Example: db.serverStatus().mem -> extract "mem" property
                String fullCommand = "db." + operationPart;
                int closeParen = fullCommand.indexOf(')');
                if (closeParen != -1 && closeParen < fullCommand.length() - 1) {
                    String afterParen = fullCommand.substring(closeParen + 1).trim();
                    if (afterParen.startsWith(".")) {
                        String[] properties = afterParen.substring(1).split("\\.");
                        Document current = serverStatusResult;
                        for (String prop : properties) {
                            Object value = current.get(prop);
                            if (value instanceof Document) {
                                current = (Document) value;
                            } else {
                                // Leaf value reached
                                result.results = new ArrayList<>();
                                result.results.add(new Document(prop, value));
                                result.resultCount = 1;
                                logger.info("Extracted nested property: {}", afterParen);
                                return result;
                            }
                        }
                        // If we get here, the final value is a Document
                        result.results = new ArrayList<>();
                        result.results.add(current);
                        result.resultCount = 1;
                        logger.info("Extracted nested document: {}", afterParen);
                        return result;
                    }
                }
                
                result.results = new ArrayList<>();
                result.results.add(serverStatusResult);
                result.resultCount = 1;
                logger.info("Retrieved server status");
                break;
                
            case "currentOp":
                // Get current operations
                Document currentOpCommand = new Document("currentOp", 1);
                
                // Parse optional filter: db.currentOp({$all: true}) or db.currentOp(true)
                if (!argsString.isEmpty()) {
                    try {
                        if (argsString.equals("true") || argsString.equals("1")) {
                            currentOpCommand.append("$all", true);
                        } else if (argsString.startsWith("{")) {
                            Document filter = Document.parse(argsString);
                            currentOpCommand.putAll(filter);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse currentOp arguments, using defaults: {}", e.getMessage());
                    }
                }
                
                Document currentOpResult = database.runCommand(currentOpCommand);
                result.results = new ArrayList<>();
                result.results.add(currentOpResult);
                result.resultCount = 1;
                logger.info("Retrieved current operations");
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported database operation: " + operation);
        }
        
        return result;
    }

    private static class MongoshCommandResult {
        String collection;
        String operation;
        List<Document> results;
        String scalarResult;
        int resultCount;
    }

    private String maskPassword(String connectionString) {
        if (connectionString == null) return "null";
        // Mask password in connection string for logging
        // Format: mongodb://username:password@host:port/database
        if (connectionString.contains("@")) {
            int atIndex = connectionString.indexOf("@");
            int colonIndex = connectionString.indexOf(":", 10); // Skip mongodb://
            if (colonIndex > 0 && colonIndex < atIndex) {
                return connectionString.substring(0, colonIndex + 1) + "****" + connectionString.substring(atIndex);
            }
        }
        return connectionString;
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