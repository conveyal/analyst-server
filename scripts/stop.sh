#!/bin/bash

# stop analyst server
if [ -e /tmp/ANALYST_PID ]; then
  kill `cat /tmp/ANALYST_PID`
  rm /tmp/ANALYST_PID
fi

if [ -e /tmp/BROKER_PID ]; then
  kill `cat /tmp/BROKER_PID`
  rm /tmp/BROKER_PID
fi

if [ -e /opt/otp/analyst-server.jar ]; then
  rm /opt/otp/analyst-server.jar
fi

if [ -e /usr/nginx/html/analyst-server.jar ]; then
  rm /opt/otp/analyst-server.jar
fi
