"""
__version__ = '$Revision: 1.3 $'
__date__    = '$Date$'
__author__  = 'Peter Arwanitis'
__license__ = 'pyBlaster is under LGPL, see http://www.xmlBlaster.org/license.html'

last change by $Author$ 

"""

# mixin classes
from BaseService import BaseService
from SimpleXMLRPCServer import SimpleXMLRPCServer, SimpleXMLRPCRequestHandler
from ResponsiveThreadingTCPServer import ResponsiveThreadingTCPServer

# helper function
from socket import gethostname

import threading 

import sys

# XMLRPC Server Mixin ###########################################################

class ThreadedXMLRPCServer(SimpleXMLRPCServer, BaseService):
    """First and very simple try, but thread cannot be regulary joined, 
       cause of blocking get_request()
    """

    def __init__(self, port, dispatcherClass, callbackInstance):
    
        # INIT Superclasses without logging
        SimpleXMLRPCServer.__init__(self, (gethostname(), port), logRequests=0)
        BaseService.__init__(self, 'XMLRPCService')
        
        # Connect the dispatcher
        dispatcher =  dispatcherClass(callbackInstance)
        
        # Register the dispatcher
        self.register_instance(dispatcher)
        #print "[XMLRPCServer.init]", self
        
    def run(self) :
        
        print "%s starts" % (self.getName(),)
        count = 0
        while self.isNotStopped():
            sys.stdout.flush()    
            if count % 100 == 0: print '.'
            count += 1
            self.handle_request() 
        

        print "%s ends" % (self.getName(),)



class ResponsiveThreadedXMLRPCServer(threading.Thread):
    """Reimplement (mixin) MasterControlThread with SimpleXMLRPCRequestHandler
    
       To bypass blocking get_request implementation, that cannot IMHO be joined.
       Please read the doc in ResponsiveThreadingTCPServer
       
       Thanks to Robin Munn
       
       The dispatcherClass is a way to restrict registering a complete instance,
       and the possibility to facade the real implementation, to get a stable
       interface.
       
       Example for an dispatcherClass to communicate with XmlBlaster
       It is simple :-) and you have a single place of connecting your code with
       the interface to XMLRPC.
       
       This code is living part of the pyBlaster project 
       (http://www.xmlblaster.org/index.html)
       Look at the news dating [Apr 13, 2003]
       
       have a nice day
       Peter
       (=PA=)
       
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

    """    

    def __init__(self, port, dispatcherClass, callbackInstance, logRequests=0, timeout=5.0):
    
        # from MasterControlThread
        threading.Thread.__init__(self)
        self.port = port
        self.timeout = timeout
        self.lock = threading.RLock()
        
        # from SimpleXMLRPCServer
        self.funcs = {}
        self.logRequests = logRequests
        self.instance = None
        
        # Connect the dispatcher
        dispatcher =  dispatcherClass(callbackInstance)
        
        # Register the dispatcher
        self.register_instance(dispatcher)
        #print "[XMLRPCServer.init]", self
        
        
        print "%s starts" % (self.getName(),)
        # encapsulate this server to get discrete control over this thread, in the thread
        self.server = ResponsiveThreadingTCPServer((gethostname(), self.port), SimpleXMLRPCRequestHandler, self.lock, self.timeout)
        
        
    def getConnectedPort(self):
        # thanks to Doug Palmer
        return self.server.socket.getsockname()[1]


    def register_instance(self, instance):
        # from SimpleXMLRPCServer
        self.instance = instance

    def register_function(self, function, name = None):
        # from SimpleXMLRPCServer
        if name is None:
            name = function.__name__
        self.funcs[name] = function
    
    def run(self):
        # from ResponsiveThreadingTCPServer.MasterControlThread
        #print "%s starts" % (self.getName(),)
        #self.server = ResponsiveThreadingTCPServer((gethostname(), self.port), SimpleXMLRPCRequestHandler, self.lock, self.timeout)
        
        # Routing
        self.server.logRequests = self.logRequests # to control RequestHandler behaviour
        self.server.instance = self.instance
        self.server.funcs = self.funcs
        
        # Note: Five seconds timeout instead of a minute, for testing.
        self.thread = threading.Thread(target=self.server.serve_forever)
        self.thread.start()
        print "%s ends" % (self.getName(),)
        
    def stop(self):    
        # from ResponsiveThreadingTCPServer.MasterControlThread
        # Tell the server it's time to shut down
        self.server.lock.acquire()
        self.server.QuitFlag = 1
        self.server.lock.release()
        print "Waiting for server to shut down (could take several seconds, till %0.2f seconds)..." % self.timeout
        self.thread.join()



        
