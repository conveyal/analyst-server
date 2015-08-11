#!/bin/bash

# start analyst server, detaching it from the terminal
# One would think that the nohup command should do this but it doesn't detach standard error
cd /ebs/scratch
java -Xmx8G -Dlogback.configurationFile=/etc/analyst-logentries.xml -jar /opt/otp/analyst-server.jar /etc/analyst.conf > /home/ubuntu/analyst.log < /dev/null 2>&1 &
echo $! > /tmp/ANALYST_PID

# start the broker. conveniently enough we can use the analyst server jar, which just happens to already have the
# logentries client baked into it.
java -Xmx1G -Dlogback.configurationFile=/etc/broker-logentries.xml -cp /opt/otp/analyst-server.jar org.opentripplanner.analyst.broker.BrokerMain /etc/broker.conf > /home/ubuntu/broker.log < /dev/null 2>&1 &
echo $! > /tmp/BROKER_PID
