import xmlrpclib

# Create an object to represent our server.
server_url = 'http://swand.lake.de:8080/';
server = xmlrpclib.Server(server_url);
print "SUCCESS: Connected to", server_url;

# Login to xmlBlaster
sessionId = server.authenticate.login( "ben", "secret", "<qos></qos>", "mySessionId");
print "login success with sessionId=", sessionId;

# Call the server and get our result.
message = server.xmlBlaster.get( "mySessionId", "<key oid=\"__sys__Login\"></key>", "<qos></qos>");
print "get result=", message;

# Asynchronous access
#server.xmlBlaster.subscribe( "mySessionId", "<key oid=\"__sys__Login\"></key>", "<qos></qos>");

