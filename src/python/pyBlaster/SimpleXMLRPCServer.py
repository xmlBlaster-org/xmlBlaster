"""Simple XMLRPC Server.

This module can be used to create simple XMLRPC servers
by creating a server and either installing functions, a
class instance, or by extending the SimpleXMLRPCRequestHandler
class.

A list of possible usage patterns follows:

1. Install functions:

server = SimpleXMLRPCServer(("localhost", 8000))
server.register_function(pow)
server.register_function(lambda x,y: x+y, 'add')
server.serve_forever()

2. Install an instance:

class MyFuncs:
    def __init__(self):
        # make all of the string functions available through
        # string.func_name
        import string
        self.string = string
    def pow(self, x, y): return pow(x, y)
    def add(self, x, y) : return x + y
server = SimpleXMLRPCServer(("localhost", 8000))
server.register_instance(MyFuncs())
server.serve_forever()

3. Install an instance with custom dispatch method:

class Math:
    def _dispatch(self, method, params):
        if method == 'pow':
            return apply(pow, params)
        elif method == 'add':
            return params[0] + params[1]
        else:
            raise 'bad method'
server = SimpleXMLRPCServer(("localhost", 8000))
server.register_instance(Math())
server.serve_forever()

4. Subclass SimpleXMLRPCRequestHandler:

class MathHandler(SimpleXMLRPCRequestHandler):
    def _dispatch(self, method, params):
        try:
            # We are forcing the 'export_' prefix on methods that are
            # callable through XMLRPC to prevent potential security
            # problems
            func = getattr(self, 'export_' + method)
        except AttributeError:
            raise Exception('method "%s" is not supported' % method)
        else:
            return apply(func, params)

    def log_message(self, format, *args):
        pass # maybe do something fancy like write the messages to a file

    def export_add(self, x, y):
        return x + y

server = SimpleXMLRPCServer(("localhost", 8000), MathHandler)
server.serve_forever()
"""

# Written by Brian Quinlan (brian@sweetapp.com).
# Based on code written by Fredrik Lundh.

import xmlrpclib
import SocketServer
import BaseHTTPServer
import sys

class SimpleXMLRPCRequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):
    """Simple XMLRPC request handler class.

    Handles all HTTP POST requests and attempts to decode them as
    XMLRPC requests.

    XMLRPC requests are dispatched to the _dispatch method, which
    may be overriden by subclasses. The default implementation attempts
    to dispatch XMLRPC calls to the functions or instance installed
    in the server.
    """

    def do_POST(self):
        """Handles the HTTP POST request.

        Attempts to interpret all HTTP POST requests as XMLRPC calls,
        which are forwarded to the _dispatch method for handling.
        """

        try:
            # get arguments
            data = self.rfile.read(int(self.headers["content-length"]))
            params, method = xmlrpclib.loads(data)

            # generate response
            try:
                response = self._dispatch(method, params)
                # wrap response in a singleton tuple
                response = (response,)
            except:
                # report exception back to server
                response = xmlrpclib.dumps(
                    xmlrpclib.Fault(1, "%s:%s" % (sys.exc_type, sys.exc_value))
                    )
            else:
                response = xmlrpclib.dumps(response, methodresponse=1)
        except:
            # internal error, report as HTTP server error
            self.send_response(500)
            self.end_headers()
        else:
            # got a valid XML RPC response
            self.send_response(200)
            self.send_header("Content-type", "text/xml")
            self.send_header("Content-length", str(len(response)))
            self.end_headers()
            self.wfile.write(response)

            # shut down the connection
            self.wfile.flush()
            self.connection.shutdown(1)

    def _dispatch(self, method, params):
        """Dispatches the XMLRPC method.

        XMLRPC calls are forwarded to a registered function that
        matches the called XMLRPC method name. If no such function
        exists then the call is forwarded to the registered instance,
        if available.

        If the registered instance has a _dispatch method then that
        method will be called with the name of the XMLRPC method and
        it's parameters as a tuple
        e.g. instance._dispatch('add',(2,3))

        If the registered instance does not have a _dispatch method
        then the instance will be searched to find a matching method
        and, if found, will be called.

        Methods beginning with an '_' are considered private and will
        not be called by SimpleXMLRPCServer.
        """

        def resolve_dotted_attribute(obj, attr):
            """resolve_dotted_attribute(math, 'cos.__doc__') => math.cos.__doc__

            Resolves a dotted attribute name to an object. Raises
            an AttributeError if any attribute in the chain starts
            with a '_'.
            """
            for i in attr.split('.'):
                if i.startswith('_'):
                    raise AttributeError(
                        'attempt to access private attribute "%s"' % i
                        )
                else:
                    obj = getattr(obj,i)
            return obj

        func = None
        try:
            # check to see if a matching function has been registered
            func = self.server.funcs[method]
        except KeyError:
            if self.server.instance is not None:
                # check for a _dispatch method
                if hasattr(self.server.instance, '_dispatch'):
                    return apply(
                        getattr(self.server.instance,'_dispatch'),
                        (method, params)
                        )
                else:
                    # call instance method directly
                    try:
                        func = resolve_dotted_attribute(
                            self.server.instance,
                            method
                            )
                    except AttributeError:
                        pass

        if func is not None:
            return apply(func, params)
        else:
            raise Exception('method "%s" is not supported' % method)

    def log_request(self, code='-', size='-'):
        """Selectively log an accepted request."""

        if self.server.logRequests:
            BaseHTTPServer.BaseHTTPRequestHandler.log_request(self, code, size)

class SimpleXMLRPCServer(SocketServer.TCPServer):
    """Simple XMLRPC server.

    Simple XMLRPC server that allows functions and a single instance
    to be installed to handle requests.
    """

    def __init__(self, addr, requestHandler=SimpleXMLRPCRequestHandler,
                 logRequests=1):
        self.funcs = {}
        self.logRequests = logRequests
        self.instance = None
        SocketServer.TCPServer.__init__(self, addr, requestHandler)

    def register_instance(self, instance):
        """Registers an instance to respond to XMLRPC requests.

        Only one instance can be installed at a time.

        If the registered instance has a _dispatch method then that
        method will be called with the name of the XMLRPC method and
        it's parameters as a tuple
        e.g. instance._dispatch('add',(2,3))

        If the registered instance does not have a _dispatch method
        then the instance will be searched to find a matching method
        and, if found, will be called.

        Methods beginning with an '_' are considered private and will
        not be called by SimpleXMLRPCServer.

        If a registered function matches a XMLRPC request, then it
        will be called instead of the registered instance.
        """

        self.instance = instance

    def register_function(self, function, name = None):
        """Registers a function to respond to XMLRPC requests.

        The optional name argument can be used to set a Unicode name
        for the function.

        If an instance is also registered then it will only be called
        if a matching function is not found.
        """

        if name is None:
            name = function.__name__
        self.funcs[name] = function

if __name__ == '__main__':
    server = SimpleXMLRPCServer(("localhost", 8000))
    server.register_function(pow)
    server.register_function(lambda x,y: x+y, 'add')
    server.serve_forever()
