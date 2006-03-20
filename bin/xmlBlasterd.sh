#! /bin/sh

#
# Skeleton sh script suitable for starting and stopping 
# wrapped Java apps on the Solaris platform. 
#
# This script expects to find the 'realpath' executable
# in the same directory. 
#
# Make sure that PIDFILE points to the correct location,
# if you have changed the default location set in the 
# wrapper configuration file.
#

#-----------------------------------------------------------------------------
# These settings can be modified to fit the needs of your application

# Application
APP_NAME="xmlBlaster"
APP_LONG_NAME="xmlBlaster"

# Wrapper
WRAPPER_CMD="$XMLBLASTER_HOME/bin/wrapper"
WRAPPER_CONF="$XMLBLASTER_HOME/config/wrapper.conf"

# Priority (see the start() method if you want to use this) 
PRIORITY=

# Do not modify anything beyond this point
#-----------------------------------------------------------------------------

# Source function library.
if [ -x /etc/rc.d/init.d/functions ]; then
. /etc/rc.d/init.d/functions
fi

# Get to the actual location of this script
#SCRIPT_DIR=`dirname $0`
#SCRIPT=`$SCRIPT_DIR/realpath $0`
#cd `dirname $SCRIPT`

# Process ID
PIDDIR="/var/run"
PIDFILE="$PIDDIR/$APP_NAME.pid"
pid=""

getpid() {
    if [ -f $PIDFILE ]
    then
    if [ -r $PIDFILE ]
    then
        pid=`cat $PIDFILE`
        if [ "X$pid" != "X" ]
        then
        # Verify that a process with this pid is still running.
        pid=`/usr/bin/ps -p $pid | grep $pid | grep -v grep | awk '{print $1}' | tail -1`
        if [ "X$pid" = "X" ]
        then
            # This is a stale pid file.
            rm -f $PIDFILE
            echo "Removed stale pid file: $PIDFILE"
        fi
        fi
    else
        echo "Cannot read $PIDFILE."
        rm -f $PIDFILE
        exit 1
    fi
    fi
}
 
start() {
    echo "Starting $APP_LONG_NAME..."
    getpid
    if [ "X$pid" = "X" ]
    then
        # If you wanted to specify the priority with which
        # your app runs, you could use nice here:
        # exec nice -$PRIORITY $WRAPPER_CMD $WRAPPER_CONF &
        # See "man nice" for more details.
        if [ -x /etc/rc.d/init.d/functions ]; then
            daemon $WRAPPER_CMD $WRAPPER_CONF 
        else
            su - -c "$WRAPPER_CMD $WRAPPER_CONF"
        fi
        #exec $WRAPPER_CMD $WRAPPER_CONF &
        echo
    else
        echo "$APP_LONG_NAME is already running."
        exit 1
    fi
}
 
stop() {
    echo "Stopping $APP_LONG_NAME..."
    getpid
    if [ "X$pid" = "X" ]
    then
        echo "$APP_LONG_NAME was not running."

    else
        kill $pid
        sleep 6

        pid=`/usr/bin/ps -p $pid | grep $pid | grep -v grep | awk '{print $1}' | tail -1`

        if [ "X$pid" != "X" ]
        then
        # SIGTERM didn't work, send SIGKILL.
            kill -9 $pid
        rm -f $PIDFILE

            pid=`/usr/bin/ps -p $pid | grep $pid | grep -v grep | awk '{print $1}' | tail -1`
        fi

        if [ "X$pid" != "X" ]
        then
            echo "Failed to stop $APP_LONG_NAME."
            exit 1
        else
            echo "Stopped $APP_LONG_NAME."
        fi
    fi
}

dump() {
    echo "Dumping $APP_LONG_NAME..."
    getpid
    if [ "X$pid" = "X" ]
    then
        echo "$APP_LONG_NAME was not running."

    else
        kill -3 $pid

        if [ $? -ne 0 ]
        then
            echo "Failed to dump $APP_LONG_NAME."
            exit 1
        else
            echo "Dumped $APP_LONG_NAME."
        fi
    fi
}

case "$1" in

    'start')
        start
        ;;

    'stop')
        stop
        ;;

    'restart')
        stop
        start
        ;;

    'dump')
        dump
        ;;

    *)
        echo "Usage: $0 { start | stop | restart | dump }"
        exit 1
        ;;
esac

exit 0

