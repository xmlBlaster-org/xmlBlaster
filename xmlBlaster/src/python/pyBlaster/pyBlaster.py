# Copyright (c) 2003 Peter Arwanitis
# mailto:spex66 @ gmx . net
# (=PA=)
#
# This program is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free Software
# Foundation; either version 2 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with
# this program; if not, write to the Free Software Foundation, Inc.,
# 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

__version__ = '120403'


"""

 8888888b.           888888b.   888                   888
 888   Y88b          888  "88b  888                   888
 888    888          888  .88P  888                   888
 888   d88P 888  888 8888888K.  888  8888b.  .d8888b  888888 .d88b.  888d888
 8888888P"  888  888 888  "Y88b 888     "88b 88K      888   d8P  Y8b 888P"
 888        888  888 888    888 888 .d888888 "Y8888b. 888   88888888 888
 888        Y88b 888 888   d88P 888 888  888      X88 Y88b. Y8b.     888
 888         "Y88888 8888888P"  888 "Y888888  88888P'  "Y888 "Y8888  888
                 888
            Y8b d88P
             "Y88P"

=======================================================
THE ABSTRACT
             
pyBlaster
    The Python way ("The first steps" :-)) to use www.XMLBLASTER.org
    
    A Python module that provides the complete XMLBLASTER interface for XML-RPC 
    This means for asynchronous updates (callbacks), too!
    
    Fredrik Lundh has provided the excellent XML-RPC library for Python.
       http://www.pythonware.com/products/xmlrpc/



Have fun and thanks to the XMLBLASTER-team!
        http://www.xmlblaster.org

        
Peter Arwanitis
spex66 @ gmx . net
(=PA=)

=======================================================
THE DETAILS

Core file
    pyBlaster.py

        My 1st Step:
            class XmlBlasterClient
                Implementation of the complete(?) XML-RPC client interface
                With just a little beautifying of the method-signatures

        My 2nd Step:        
            class XmlBlasterCallbackClient
                Specialisation of XmlBlasterClient with additional 
                threaded XML-RPC server implementation
        

Based on (if you have an uptodate installation, delete the provided files)
    xmlrpclib.py / SimpleXMLRPCServer.py ( Version 1.0.1 )
    
Additional core files
    BaseService.py             class to comfortable handle threads
                               found in the Narval project from LOGILAB
                               http://www.logilab.org
    ThreadedXMLRPCServer.py    mixin class SimpleXMLRPCServer & BaseService
                               to build an threaded XMLRPCServer
                            
Optional files
    ShellService.py            mixin class BaseService & InteractiveConsole
                               to serve an interactive Python prompt (shell) 
                               for debugging and testing 


=======================================================
THE INSTALLATION

Put the pyBlaster directory (its an python package) into your 
python/Lib/site-packages (or use it direct from the directory)


=======================================================
THE USAGE 

Read the XMLBLASTER documentation / requirements, especially for the
"quality of service QoS" options.

In your python project:

# import
from pyBlaster import pyBlaster

# build an instance
xb = pyBlaster.XmlBlasterCallbackClient()

# start server / use client
# its up to you, thats all!

=======================================================
THE TEST

Developed under Python 2.2.2 with the XML-RPC update from pythonware

Success stories from Jython and other CPython version are appreciated!

Test (batteries included):
    start pyBlaster.py in a shell and have a look at the help text
    start pyBlaster.py in more than one shell and experiment interactive
          with publish / subcribe / get <-- this is the python way :)
    


(=PA=)        
"""


from ThreadedXMLRPCServer import ThreadedXMLRPCServer
import xmlrpclib
import sys
from ShellService import ShellService
from socket import gethostname

true, false = 1, 0 # Bool init

class XmlBlasterClient:
    """Implementation of a client interface for XMLBLASTER 
       (docstrings copied from the java version)
    """
    def __init__(self, xmlblaster_url=None):
        "Optional xmlblaster_url for direct connection"
        self.xmlblaster_url = xmlblaster_url
        self.proxy          = None
        self.sessionId      = None
                
        if xmlblaster_url:
            self.connect(xmlblaster_url)
            
    # CLIENT Interface #################################################        
            
    def connect(self, xmlblaster_url):
        self.proxy = xmlrpclib.ServerProxy(xmlblaster_url)
        print "\n==> ::CONNECT to XmlBlaster:: <=="
        print '    Sucessful Server connect on ', xmlblaster_url
        #self.proxy = xmlrpclib.Server(xmlblaster_url) # for old xmlrpclib versions
        
    # XMLBLASTER

    def login(self, username='guest', password='guest', callback_url=None):
        """
        Do login to xmlBlaster.
        @see org.xmlBlaster.authentication.Authenticate#connect(ConnectQosServer,String)
        @deprecated Use connect() instead
        @return The secret sessionId as a raw string
        """
        
        if callback_url:
            _cb = "<callback type='XML-RPC'>%s</callback>" % callback_url
        else:
            _cb = ""
        
        qos = "<qos>%s</qos>" % _cb
        
        # remember the return secret value for further usage
        self.sessionId = self.proxy.authenticate.login(username, password, qos, "")
        
        print "==> ::LOGIN:: <=="
        print '      Success with sessionId= ', self.sessionId
        
    def logout(self):
    
        print "==> ::LOGOUT:: <=="
        self.proxy.authenticate.logout(self.sessionId)
    
    def publish(self, xmlKey, content, qos):
        """
        Publish messages.

        This variant allows to pass an array of MsgUnitRaw object, for performance reasons and
        probably in future as an entity for transactions.

        @see org.xmlBlaster.engine.RequestBroker
        @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
        """
        
        print "==> ::PUBLISH:: <=="
        self.proxy.xmlBlaster.publish(self.sessionId, xmlKey, content, qos)
    
    def publishOneway(self, xmlKey, content, qos):
        """
        Publish messages.

        The oneway variant may be used for better performance,
        it is not returning a value (no application level ACK)
        and there are no exceptions supported over the connection to the client.

        @see org.xmlBlaster.engine.RequestBroker
        @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
        """
        print "==> ::PUBLISHONEWAY:: <=="
        self.proxy.xmlBlaster.publishOneway(self.sessionId, xmlKey, content, qos)
    
    def subscribe(self, xmlKey, qos):
        """
        Subscribe to messages.

        @param xmlKey_literal Depending on the security plugin this key is encrypted
        @param subscribeQoS_literal Depending on the security plugin this qos is encrypted
        @see org.xmlBlaster.engine.RequestBroker
        @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
        """

        print "==> ::SUBSCRIBE:: <=="
        self.proxy.xmlBlaster.subscribe(self.sessionId, xmlKey, qos)
        
    def unsubscribe(self, xmlKey, qos):
        """
        Unsubscribe from messages.

        To pass the raw xml ASCII strings, use this method.

        @param xmlKey_literal Depending on the security plugin this key is encrypted
        @param unSubscribeQoS_literal Depending on the security plugin this qos is encrypted
        @see org.xmlBlaster.engine.RequestBroker
        """
        print "==> ::UNSUBSCRIBE:: <=="
        self.proxy.xmlBlaster.unsubscribe(self.sessionId, xmlKey, qos)
            
    def get(self, xmlKey, qos):
        """
        Synchronous access a message.

        @see org.xmlBlaster.engine.RequestBroker
        @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
        """
        print "==> ::GET:: <=="
        return self.proxy.xmlBlaster.get(self.sessionId, xmlKey, qos)

    def erase(self, xmlKey, qos):
        """   
        Delete messages.
        @see org.xmlBlaster.engine.RequestBroker
        @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
        """    
        
        print "==> ::ERASE:: <=="
        self.proxy.xmlBlaster.erase(self.sessionId, xmlKey, qos)
        
    def printMessages(self, messages):
        print "   Received ", len(messages), " messages:"
        for msg in messages:
           key = msg[0]
           content = msg[1]     # content is of type xmlrpclib.Binary
           qos = msg[2]
           print "      key=", key
           print "      content=", content.data, " bytes"
           print "      qos=", qos
        

class XmlBlasterCallbackClient(XmlBlasterClient):
    """Specialication of the client class with the additional 
       implementation of the server interface
       
       To use asynchronous update() or updateOneway() you can
       subtype this class and override the methods with your own
       project specific dispatchers. 
       
       Hint:
         Remember that the calls occur in a seperate thread, 
         so usage of a threadsafe queue is always a good idea
         
         Look for python cookbook hints on threaded programming
       
       The XML-RPC callback server runs on an own port as a seperate thread
       (docstrings copied from the java version)
    """

    def __init__(self, xmlblaster_url=None):
    
        # INIT client part
        XmlBlasterClient.__init__(self, xmlblaster_url)
        
        self.callback_url    = None
        self.callback_server = None
        self.shell           = None
        
    # Dispatcher Class (XMLRPC Server Interface) ###############################

    class XB_CallbackDispatcher:
        def __init__(self, xb_CallbackInstance):
            self.xb_CallbackInstance=xb_CallbackInstance
        def update(self, *attrs):
            return self.xb_CallbackInstance.update(*attrs)
        def updateOneway(self, *attrs):
            return self.xb_CallbackInstance.updateOneway(*attrs)
        def ping(self, *attrs):
            return self.xb_CallbackInstance.ping(*attrs)

    # Start / Stop Server ######################################################

    def startCallbackServer(self, port) :

        self.callback_server = ThreadedXMLRPCServer(port, 
                                    dispatcherClass=self.XB_CallbackDispatcher, 
                                    callbackInstance=self
                                    )
        
        self.callback_url = 'http://%s:%i/RPC2' % (gethostname(), port)   
        
        print "\n==> ::STARTCALLBACKSERVER:: <=="
        print '      Success with callback_url= ', self.callback_url
        
        self.callback_server.start()

    def stopCallbackServer(self) :
        print "\n==> ::STOPCALLBACKSERVER:: <=="
        print "      I'm dying... "
        self.callback_server.stop()
        print "      ...good bye!"
        

    # Start / Stop SHELL Service ##############################################

    def startShellService(self):
        if not self.shell:
            print "\n==> ::STARTSHELLSERVICE:: <=="
            self.shell = ShellService(engine=self, name='ShellService')
            self.shell.start()

    def stopShellService(self):
        if self.shell:
            self.shell.stop()
            
    # Total Shutdown ##############################################
    def shutdown(self):
        "Closes all servers (joining all threads) and connections"
        
        # XXX 080403 PA ? thread stopping isn't workink smooth yet... help appreciated!
        
        print "\n==> ::SHUTDOWN Initiated:: <=="
        if self.sessionId: self.logout()
        if self.callback_server: self.stopCallbackServer()
        if self.shell: self.stopShellService()
        print "\n==> ::SHUTDOWN Completed:: <=="
        
            


    # SERVER Interface ##########################################################    

    def update(self, sessionId, key, content, qos):
        """ This is the callback method invoked from the server
            informing the client in an asynchronous mode about new messages
            
            You have to override this method in a specialication, to establish
            your own logic.
        """
    
        print "==> ::UPDATE:: <=="
        print "   SessionId::    ", sessionId
        print "         Key::    ", key
        print "     Content::    ", content.data
        print "         QoS::    ", qos
        
        return ""
        
    
    def updateOneway(self, sessionId, key, content, qos):
        """ This oneway method does not return something, it is high performing but
            you loose the application level hand shake.
            @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
            
            You have to override this method in a specialication, to establish
            your own logic.
        """
        print "==> ::UPDATEONEWAY:: <=="
        print "   SessionId::    ", sessionId
        print "         Key::    ", key
        print "     Content::    ", content.data
        print "         QoS::    ", qos

    def ping(self, qos):
        """ Ping to check if the callback server is alive.
            @param qos ""
            @return ""
        """
        #print "==>::PING:: ", qos
        
        return ""

# TEST with interaction #####################################################

__usage__ = """Usage from the shell:
        python pyBlaster.py [xmlblaster_url, 
                             callbackport, 
                             username, password, 
                             XPath subscription testphrase
                             ]
        
    Example:
        java -jar lib/xmlBlaster.jar
            <-- starts XmlBlaster
        
        pyBlaster.py http://<the-xb-machine>:8080 8081 me too first
            <-- starts your first client with callbacks

        pyBlaster.py http://<the-xb-machine>:8080 8082 you too second
            <-- starts another one in another shell (on another computer)

        XPath subscription testphrase: 'first' , 'second' or 'third'
        look at the result of the five publish() calls, copy them for 
        further testing
        
        try to publish something on your own :-) 
        the python interactive shell is your friend
        
    Pythonshell:
            _ is the instance of your XmlBlasterCallbackClient, 
            >>> dir(_) 
            shows the interface
            
            >>> print _.publish.__doc__ 
            gives a interactive look at the docstring 
            (it's just an example :-))
            
            cause callbacks are mixed on the same output, 
            it's a bit messy sometimes :-)
            
            <ctrl><pause> is killing the program, without ending 
            each thread and connection by hand
            
    Hint:
            Situation:
                A client got a message with an oid (i.e. '3') 
                cause it's fitting an appropriate subsribe/XPath
            Effect:    
                If the message with exactly _this_ oid is changed/altered, 
                the client recieves an update() on this, ignoring the 
                subscribe/XPath!
                
                If you have an OID once, you have an subscription to all 
                changes, nice!
        
        """

if __name__ == '__main__':
    # ok, it is a raw usage of sys.argv, but there is no confusion 
    # through all the perfect modules to parse the parameters :-)
    #
    # for the beauty of option-parsing look elsewhere, thanks
    try:
        xmlblaster_url = sys.argv[1]
        callbackport = int(sys.argv[2])
        user = sys.argv[3]
        passwd = sys.argv[4]
        phrase = sys.argv[5]
    except:
        print __usage__
        sys.exit()
    
    xb = XmlBlasterCallbackClient(xmlblaster_url)
    xb.startCallbackServer(callbackport)
    xb.startShellService()
    
    
    print """    _.login('%s', '%s', '%s')""" % (user, passwd, xb.callback_url)
    xb.login(user, passwd, xb.callback_url)
    
    print """    _.subscribe("<key oid='' queryType='XPATH'>//%s</key>", "<qos/>")"""  % phrase
    xb.subscribe("<key oid='' queryType='XPATH'>//%s</key>" % phrase, "<qos/>")
    

    print """    _.publish("<key oid='1'><first/></key>", 'First Type Message', "<qos></qos>")"""
    xb.publish("<key oid='1'><first/></key>", 'First Type Message', "<qos></qos>")

    print """    _.publish("<key oid='2'><second/></key>", 'Second Type Message', "<qos></qos>")"""
    xb.publish("<key oid='2'><second/></key>", 'Second Type Message', "<qos></qos>")

    print """    _.publish("<key oid='3'><first/></key>", 'First Type Message', "<qos></qos>")"""
    xb.publish("<key oid='3'><first/></key>", 'First Type Message', "<qos></qos>")

    print """    _.publish("<key oid='4'><third/></key>", 'Third Type Message', "<qos></qos>")"""
    xb.publish("<key oid='4'><third/></key>", 'Third Type Message', "<qos></qos>")

    print """    _.publish("<key oid='5'><third/></key>", 'Third Type Message', "<qos></qos>")"""
    xb.publish("<key oid='5'><third/></key>", 'Third Type Message', "<qos></qos>")
    
    print """\n\nNow it's on you, the python-prompt is yours! Yes it's an python prompt !-)
    
    _    <-- is the running instance
    
    maybe a first attempt is to copy the printed calls and alter them...
    """ 

    