use Frontier::Client;
    
# Make an object to represent the XML-RPC server.
$server_url = 'http://MyHost:8080/';
print "\nTrying to connect to xmlBlaster server on $server_url ...\n";

$server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Call the remote server and get our result.
$sessionId = $server->call('authenticate.login', "ben", "secret", "<qos></qos>", "");
print "\nLogin success with sessionId=$sessionId \n";

# Call the server and get its current memory consumption.
$message = $server->call('xmlBlaster.get', $sessionId, "<key oid='__sys__TotalMem'></key>", "<qos></qos>");
print "\nResult for a get():\n----------------\n $message \n----------------\n";

#$message = $server->call('xmlBlaster.publish', $sessionId, "<key oid='MyMessage'></key>", "Hello world", "<qos><forceUpdate /></qos>");

# Logout from xmlBlaster
#$server->call('authenticate.logout', $sessionId);
#print "\nLogout done, bye.\n";

