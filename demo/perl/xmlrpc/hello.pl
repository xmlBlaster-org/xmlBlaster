#!/usr/bin/perl
# Name: xmlBlaster/demo/perl/xmlrpc/hello.pl (xmlBlaster.org)
# Invoke
#   perl hello.pl http://myHost:8080/
# if xmlBlaster runs on 'myHost'

use Frontier::Client;
use MIME::Base64;
#use String::Trim;
    
#$server_url = 'http://MyHost:8080/';
$server_url = @ARGV[0];
if ($#ARGV == -1) {
   $host = `uname -n`;
   $host =~ s/^\s*(.*?)\s*$/$1/; # trim whitespace
   $server_url = "http://" . $host . ":8080/";  # guess where xmlBlaster is
}
print "\nTrying to connect to xmlBlaster server on $server_url ...\n";

# Make an object to represent the XMLRPC server.
$server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Call the remote server and get our result.
$sessionId = $server->call('authenticate.login', "ben", "secret",
                                                 "<qos></qos>", "");
print "Login success, got secret sessionId=$sessionId \n";

# Call the server and get its current memory consumption.
$queryKey = "<key oid='__cmd:?totalMem'/>";
# Call the server and query all messages with XPath:
#$queryKey = "<key queryType='XPATH'>/xmlBlaster</key>";

@msgUnits = $server->call('xmlBlaster.get', $sessionId, $queryKey, "<qos/>");
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
      print $qos;
      print "\n-------------#$j-------------------\n";
   }
}

# Try publishing a message:
$returnQos = $server->call('xmlBlaster.publish', $sessionId,
                           "<key oid='MyMessage'></key>",
                           "Hello world", "<qos><forceUpdate /></qos>");
print "\nResult for a publish():\n------------", $returnQos, "\n------------\n";


# Logout from xmlBlaster
$server->call('authenticate.logout', $sessionId);
print "\nLogout done, bye.\n";
