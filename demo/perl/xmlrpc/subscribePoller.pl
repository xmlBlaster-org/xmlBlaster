#!/usr/bin/perl
# Invoke
#   perl subscribePoller.pl http://myHost:8080/
# if xmlBlaster runs on 'myHost'
#
# Work around as we don't have a callback server
# to not loose asynchronously arriving messages for us.
#
# Connects with a persistent session 'joe/1',
# subscribes on topic 'myTopic'
# and synchronously polls the callback queue
# with get() for arrived messages.
# The session/subscription is persistent and
# we never loose any message even if the script
# terminates for a while.
#
# Test setup:
#
# Start server
#   java -Dcom.sun.management.jmxremote org.xmlBlaster.Main
# (you can use JDK's 'jconsole' to observe the server status)
#
# Start a publisher to send test messages
#  java javaclients.HelloWorldPublish -oid myTopic -numPublish 20
#
# @author Marcel Ruff

use Frontier::Client;
use MIME::Base64;
    
$server_url = @ARGV[0];
if ($#ARGV == -1) {
   $host = `uname -n`;
   $host =~ s/^\s*(.*?)\s*$/$1/;
   $server_url = "http://" . $host . ":8080/";  # guess where xmlBlaster is
}
print "\nTrying to connect to xmlBlaster server on $server_url ...\n";

$server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Login and set dispatcherActive='false' as we have no callback server
# Use a fake EMAIL callback protocol to satisfy xmlBlaster
# We are only interested in the callback queue to hold the messages
$sessionId = $server->call('authenticate.login', "ben", "secret",
    "<qos>
      <session name='joe/3' timeout='-1'/>
      <persistent/>
      <queue relating='callback' maxEntries='1000'>
        <callback type='EMAIL' retries='-1' pingInterval='0' dispatcherActive='false'>
          a@b
        </callback>
      </queue>
    </qos>", "");

print "Login success, got secret sessionId=$sessionId \n";

# Subscribe with persistence flag to survive server restart
# Subscribe once is enough as we have a persistent session 'joe/3'.
# To avoid duplicate subscriptions on restart of this script
# we set multiSubscribe to false
$topicId = 'myTopic';
$returnQos = $server->call('xmlBlaster.subscribe', $sessionId,
       "<key oid='" . $topicId . "'/>",
       "<qos>
          <multiSubscribe>false</multiSubscribe>
          <persistent>true</persistent>
        </qos>");
print "\nResult for a subscribe(" . $topicId . "):\n------------", $returnQos, "\n------------\n";

# Poll for messages
while (true) {
   $queryKey = "<key oid='__cmd:client/joe/3/?cbQueueEntries'/>";
   # Access the callback queue and consume all messages from there
   @msgUnits = $server->call('xmlBlaster.get', $sessionId, $queryKey, 
      "<qos>
        <querySpec type='QueueQuery'>
          <![CDATA[maxEntries=-1&maxSize=-1&consumable=true&waitingDelay=0]]>
        </querySpec>
      </qos>");

   print "\nResults for a get($queryKey):";
   for $i (0 .. $#msgUnits) {
      for $j (0 .. $#{$msgUnits[$i]}) {
         print "\n-------------#$j-------------------";
         $key = $msgUnits[$i][$j][0];
         $contentBase64AndEncoded = $msgUnits[$i][$j][1];
         $content = decode_base64($contentBase64AndEncoded->value());
         $qos = $msgUnits[$i][$j][2];
         print $key;
         print "\n<content>" . $content . "</content>\n";
         print $qos;  # TODO: re-subscribe if an ERASE arrives
         print "\n-------------#$j-------------------\n";
      }
   }
   sleep(2);  # Poll invterval set to 2 seconds
}

# No logout from xmlBlaster to keep the session 'joe/3'
#$server->call('authenticate.logout', $sessionId);

print "\nKeeping session, bye.\n";
