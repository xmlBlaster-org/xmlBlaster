use Frontier::Client;
    
#$server_url = 'http://MyHost:8080/';
$server_url =  @ARGV[0];
print "\nTrying to connect to xmlBlaster server on $server_url ...\n";

# Make an object to represent the XML-RPC server.
$server = Frontier::Client->new(url => $server_url);
print "Connected to xmlBlaster server on $server_url \n";

# Call the remote server and get our result.
$sessionId = $server->call('authenticate.login', "dk", "dk", "<qos><local>false</local></qos>", "");
print "\nLogin success with sessionId=$sessionId \n";

my $c=0;
while ($c++ < 100) {
	$message = $server->call('xmlBlaster.publish', $sessionId,
"<key oid='' contentMime='text/xml'><service>post</service><type>request</type><id>123</id></key>",
"<tag>Hello World</tag>", "<qos><isVolatile>true</isVolatile><isDurable>false</isDurable></qos>");
	print "published... $c\n";
	sleep(2);
}


# Logout from xmlBlaster
#$server->call('authenticate.logout', $sessionId);
print "\nLogout done, bye.\n";

