#!/bin/bash

# Start 10 subscribers in separate xterms (Unix only)

echo "Try publish:"
echo "java -Dcom.sun.management.jmxremote javaclients.HelloWorldPublish -numPublish 500 -session.name pubisher/1 -persistent true -oid Hello -interactive false -sleep 0 -contentSize 300000"


export COUNT=10
if [ "$1" != "" ] ; then
   export COUNT=$1
fi

echo "Starting $COUNT subscribers ..."

export CP="$HOME/xmlBlaster/lib/xmlBlaster.jar"
export PROG="java -Dcom.sun.management.jmxremote -cp $CP javaclients.HelloWorldSubscribe"
export ARGS=" -oid Hello -session.name subscriber/$i -persistentSession true -persistentSubscribe true -multiSubscribe false -dispatch/callback/retries -1 -autoSubscribe true"
#export SERV=" -dispatch/connection/plugin/socket/hostname localhost -dispatch/connection/plugin/socket/port 7607"

for ((i=1;i<=$COUNT;i+=1)); do
	echo "subscriber/$i ..."
	xterm -geom 180x26 -e $PROG $ARGS $SERV -session.name subscriber$i/1 &
#	xterm -geom 180x26 -e java -Dcom.sun.management.jmxremote javaclients.HelloWorldSubscribe -oid Hello -session.name subscriber$i/1 -persistentSubscribe true -multiSubscribe false -dispatch/callback/retries -1 -autoSubscribe true
	sleep 1
done
