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