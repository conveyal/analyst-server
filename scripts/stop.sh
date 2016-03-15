#!/bin/bash

# stop analyst server
if [ -e /var/lock/ANALYST_PID ]; then
  kill `cat /var/lock/ANALYST_PID`
  rm /var/lock/ANALYST_PID
fi

if [ -e /var/lock/BROKER_PID ]; then
  kill `cat /var/lock/BROKER_PID`
  rm /var/lock/BROKER_PID
fi

if [ -e /opt/otp/analyst-server.jar ]; then
  rm /opt/otp/analyst-server.jar
fi

if [ -e /usr/nginx/html/analyst-server.jar ]; then
  rm /opt/otp/analyst-server.jar
fi
