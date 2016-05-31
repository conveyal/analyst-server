#!/bin/bash

# figure out how much memory to use. Stats get 512M, broker gets 4G, OS gets 2G, analyst gets rest
TOTAL_MEM=`grep MemTotal /proc/meminfo | sed s/[^0-9]//g`
ANALYST_MEM=`echo "$TOTAL_MEM - (6500 * 1024)" | bc`

# start analyst server, detaching it from the terminal
# One would think that the nohup command should do this but it doesn't detach standard error
cd /ebs/scratch

java8 -Xmx${ANALYST_MEM}k -jar /opt/otp/analyst-server.jar /etc/analyst.conf > /home/ec2-user/analyst.log < /dev/null 2>&1 &
echo $! > /home/ec2-user/ANALYST_PID

# start the broker.
java8 -Xmx4G -cp /opt/otp/analyst-server.jar com.conveyal.r5.analyst.broker.BrokerMain /etc/broker.conf > /home/ec2-user/broker.log < /dev/null 2>&1 &
echo $! > /home/ec2-user/BROKER_PID

