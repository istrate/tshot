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