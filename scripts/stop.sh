#!/bin/bash

# stop analyst server
if [ -e /home/ec2-user/ANALYST_PID ]; then
  kill `cat /home/ec2-user/ANALYST_PID`
  rm /home/ec2-user/ANALYST_PID
fi

if [ -e /home/ec2-user/BROKER_PID ]; then
  kill `cat /home/ec2-user/BROKER_PID`
  rm /home/ec2-user/BROKER_PID
fi

if [ -e /opt/otp/analyst-server.jar ]; then
  rm /opt/otp/analyst-server.jar
fi

if [ -e /usr/nginx/html/analyst-server.jar ]; then
  rm /opt/otp/analyst-server.jar
fi
