# -----------------------------------------------------
# Python hello world demo xmlrpc-client
# Invoke:  python  hello.py  http://myhost:8080
# -----------------------------------------------------

import xmlrpclib
import sys

# The url to xmlBlaster (see the output of xmlBlaster server):
server_url = sys.argv[1]


# -----------------------------------------------------
print "\nTrying to connect to xmlBlaster server on ", server_url, " ...\n"
# Create an object to represent our server.
server = xmlrpclib.Server(server_url)


# -----------------------------------------------------
# Login to xmlBlaster using a password file:
#qos = "<qos><securityService type='htpasswd' version='1.0'> \
#           <![CDATA[ \
#             <user>michele</user> \
#             <passwd>secret</passwd> \
#           ]]> \
#        </securityService></qos>";

# Login to xmlBlaster using default authentication:
qos = "<qos></qos>"

sessionId = server.authenticate.login( "ben", "secret", qos, "")
print "\nLogin success with on ", server_url, " sessionId=", sessionId


# -----------------------------------------------------
# Call the server and get its current memory consumption.
print "\n*  Trying to access the consumed memory of the xmlBlaster server ..."
messages = server.xmlBlaster.get( sessionId, "<key oid='__cmd:?totalMem'></key>", "<qos></qos>")
print "   Received ", len(messages), " messages:"
for msg in messages:
   key = msg[0]
   content = msg[1]     # content is of type xmlrpclib.Binary
   qos = msg[2]
   print "      key=", key
   print "      content=", content.data, " bytes"
   print "      qos=", qos


# -----------------------------------------------------
print "\n*  Trying to publish a 'Hello world' message ..."
# How do i generate a binary content with python??
#content = xmlrpclib.Binary(R"Hello world")
#server.xmlBlaster.publish( sessionId, "<key oid='MyMessage'></key>", content.data, "<qos></qos>")

content = "Hello world"
server.xmlBlaster.publish( sessionId, "<key oid='MyMessage'></key>", content, "<qos></qos>")
print "   Publish success for message 'MyMessage' content=", content


# -----------------------------------------------------
# Call the server and get the hello world message again
print "\n*  Trying to access our previous published 'Hello world' message ..."
messages = server.xmlBlaster.get( sessionId, "<key oid='MyMessage'></key>", "")
print "   Received ", len(messages), " messages:"
for msg in messages:
   key = msg[0]
   content = msg[1]     # content is of type xmlrpclib.Binary
   qos = msg[2]
   print "      key=", key
   print "      content=", content.data
   print "      qos=", qos


# -----------------------------------------------------
# Asynchronous access - not yet implemented
# We need to instantiate a Python xmlRpc callback server first
#server.xmlBlaster.subscribe( sessionId, "<key oid='__cmd:?totalMem'></key>", "<qos></qos>")


# -----------------------------------------------------
# Logout from xmlBlaster
server.authenticate.logout( sessionId ) #server.authenticate.logout( sessionId, "<qos></qos>" )
print "\nLogout done, bye.\n"
