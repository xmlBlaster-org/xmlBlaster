# Invoke: perl testpub.pl http://myHost:8080
use Frontier::Client;
    
#$server_url = 'http://MyHost:8080/';
$server_url =  @ARGV[0];
print "\nTrying to connect to xmlBlaster server on $server_url ...\n";

# Make an object to represent the XMLRPC server.
$server = Frontier::Client->new(url => $server_url);

$connectQos = "<qos>".
              "  <securityService type='htpasswd' version='1.0'>".
              "    <user>testpubUser</user>".
              "    <passwd>secret</passwd>".
              "  </securityService>".
              "  <ptp>true</ptp>".
              "  <session timeout='86400000' maxSessions='10'/>".
              "</qos>";

# Call the remote server and get our result (we need to port to connect(), see XmlBlaster.pm).
$sessionId = $server->call('authenticate.login', "testpubUser", "secret", $connectQos, "");
print "\nLogin success on $server_url, got secret sessionId=$sessionId \n";

my $publishKey="<key oid='' contentMime='text/xml'>".
               "  <service>post</service>".
               "  <type>request</type>".
               "  <id>123</id>".
               "</key>",
my $cdata="<event>testing</event>";
my $publishQos="<qos>".
               "  <expiration lifeTime='0'/>".
               "  <persistent>false</persistent>".
               "  <topic destroyDelay='0' createDomEntry='true'/>".
               "</qos>";

$message = $server->call('xmlBlaster.publish', $sessionId,
        $publishKey,
        $cdata,
        $publishQos);

print "publish return is : $message\n";

# Logout from xmlBlaster
$server->call('authenticate.disconnect', $sessionId, "<qos/>");
print "\nLogout done, bye.\n";

