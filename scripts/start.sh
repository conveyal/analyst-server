#!/bin/bash

# figure out how much memory to use. Stats get 512M, broker gets 1G, OS gets 1G, analyst gets rest
TOTAL_MEM=`grep MemTotal /proc/meminfo | sed s/[^0-9]//g`
ANALYST_MEM=`echo "$TOTAL_MEM - (4500 * 1024)" | bc`

# start analyst server, detaching it from the terminal
# One would think that the nohup command should do this but it doesn't detach standard error
cd /ebs/scratch
java -Xmx${ANALYST_MEM}k -jar /opt/otp/analyst-server.jar /etc/analyst.conf > /home/ec2-user/analyst.log < /dev/null 2>&1 &
echo $! > /var/lock/ANALYST_PID

# start the broker.
java -Xmx2G -cp /opt/otp/analyst-server.jar org.opentripplanner.analyst.broker.BrokerMain /etc/broker.conf > /home/ec2-user/broker.log < /dev/null 2>&1 &
echo $! > /var/lock/BROKER_PID
