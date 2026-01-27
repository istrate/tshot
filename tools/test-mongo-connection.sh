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