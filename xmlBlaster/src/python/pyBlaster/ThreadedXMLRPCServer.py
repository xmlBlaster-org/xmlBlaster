__version__ = '080403'
__author__ = 'spex66@gmx.net'

# mixin classes
from BaseService import BaseService
from SimpleXMLRPCServer import SimpleXMLRPCServer

# helper function
from socket import gethostname


# XMLRPC Server Mixin ###########################################################

class ThreadedXMLRPCServer(SimpleXMLRPCServer, BaseService):
    def __init__(self, port, dispatcherClass, callbackInstance):
    
        # INIT Superclasses without logging
        SimpleXMLRPCServer.__init__(self, (gethostname(), port), logRequests=0)
        BaseService.__init__(self, 'XMLRPCService')
        
        # Connect the dispatcher
        dispatcher =  dispatcherClass(callbackInstance)
        
        # Register the dispatcher
        self.register_instance(dispatcher)
        #print "[XMLRPCServer.init]", self
        
    def _run(self) :
        
        while self.loop :
            self.handle_request() 
        

