use Frontier::Client;
use MIME::Base64;
    
#$server_url = 'http://MyHost:8080/';
$server_url =  @ARGV[0];
print "\nTrying to connect to xmlBlaster server on $server_url ...\n";

# Make an object to represent the XMLRPC server.
$server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Call the remote server and get our result.
$sessionId = $server->call('authenticate.login', "ben", "secret", "<qos></qos>", "");
print "\nLogin success with sessionId=$sessionId \n";

# Call the server and get its current memory consumption.
@msgUnits = $server->call('xmlBlaster.get', $sessionId, "<key oid='__cmd:?totalMem'/>", "<qos/>");
# Call the server and query all messages with XPath:
#@msgUnits = $server->call('xmlBlaster.get', $sessionId, "<key queryType='XPATH'>/xmlBlaster</key>", "<qos/>");
print "\nResults for a get():\n--------------------------------";
for $i (0 .. $#msgUnits) {
   for $j (0 .. $#{$msgUnits[$i]}) {
      print "\n\n-------------#$j-------------------";
      $key = $msgUnits[$i][j][0];
      $contentBase64AndEncoded = $msgUnits[$i][j][1];
      $content = decode_base64($contentBase64AndEncoded->value());
      $qos = $msgUnits[$i][j][2];
      print $key;
      print "\n<content>" . $content . "</content>\n";
      print $qos;
      print "\n-------------#$j-------------------\n";
   }
}

$returnQos = $server->call('xmlBlaster.publish', $sessionId, "<key oid='MyMessage'></key>", "Hello world", "<qos><forceUpdate /></qos>");
print "\nResult for a publish():\n----------------", $returnQos, "\n----------------\n";


# Logout from xmlBlaster
$server->call('authenticate.logout', $sessionId);
print "\nLogout done, bye.\n";
