import xmlrpclib
import sys

# Invoke:  python  hello.py  http://myhost:8080

# The url to xmlBlaster (see the output of xmlBlaster server):
server_url = sys.argv[1];
#server_url = 'http://myHost:8080/';


print "\nTrying to connect to xmlBlaster server on ", server_url, " ...\n"

# Create an object to represent our server.
server = xmlrpclib.Server(server_url);
print "Connected to xmlBlaster server on ", server_url;

# Login to xmlBlaster
sessionId = server.authenticate.login( "ben", "secret", "<qos></qos>", "");
print "\nLogin success with sessionId=", sessionId;

# Call the server and get our result.
message = server.xmlBlaster.get( sessionId, "<key oid=\"__sys__Login\"></key>", "<qos></qos>");
print "\nResult for a get():\n\n", message;

# Asynchronous access - not yet implemented
#server.xmlBlaster.subscribe( sessionId, "<key oid=\"__sys__Login\"></key>", "<qos></qos>");


# Logout from xmlBlaster
server.authenticate.logout( sessionId );
#server.authenticate.logout( sessionId, "<qos></qos>" );
print "\nLogout done, bye.\n";

