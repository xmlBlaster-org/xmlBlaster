"""
__version__ = '$Revision: 1.1 $'
__date__    = '$Date$'
__author__  = 'Robin Munn, Peter Arwanitis'
__license__ = 'pyBlaster is under LGPL, see http://www.xmlBlaster.org/license.html'

last change by $Author$ 

"""

"""
Found in thread:
http://groups.google.com/groups?hl=de&lr=&ie=UTF-8&threadm=slrna3pabh.1rq.rmunn%40rmunn.dyndns.org&rnum=1&prev=/groups%3Fhl%3Dde%26lr%3D%26ie%3DUTF-8%26selm%3Dslrna3pabh.1rq.rmunn%2540rmunn.dyndns.org%26rnum%3D1

I've been playing around with the SocketServer module recently. It
provides some useful classes for building various kinds of servers. But
there's one feature I wanted that didn't seem available. I wanted to be
able to kill the server externally at any time, without using kill -9 or
anything that relied on Unix signals, since I also needed to be able to
run this under Windows. After a little bit of pondering, I came up with
the following solution:

1) Subclass the ThreadingTCPServer class so that instead of calling
   socket.accept() and blocking until a connection comes in, it will
   call select.select() with a timeout; at the end of the timeout, it
   will check a "Quit now?" flag and if the flag is set, will close the
   connection and exit. If the quit flag is not set, go back into the
   select.select() for another N seconds.

2) A "master control thread" launches an instance of the
   ThreadingTCPServer subclass, then goes and blocks on some event
   (which could be anything from just pressing Return on the console the
   server was run from, as in the simplified example below, to receiving
   a signal from the Windows NT service manager, to something else).
   Once that event takes place, it sets the quit flag on the server
   thread and then join()s the thread, which will exit as soon as the
   next select.select() call returns.

3) If the Master Control Thread ever gets out of hand, a "Tron" thread
   will activate and kill it. (Just kidding). :)

A fully-functional example is below. Any comments? I'd be especially
interested if you know a better way of doing what I was trying to do
(stay responsive to "quit now" commands from an external source without
completely re-inventing the wheel).

-- 
Robin Munn
rmunn@pobox.com


----- begin code -----
"""
# Witty socket server

# Note: In subclassing ThreadingTCPServer, try overriding get_request()
#       to select() on the single socket for a certain timeout period,
#       then poll the quit flag at the end of that timeout before
#       returning
#       to the select(). That should provide a good balance between
#       blocking
#       to keep CPU usage down and speed of response to a shutdown
#       request.

import os, sys

import socket
import SocketServer
import time
import threading
import select


class TimeToQuit(Exception):
    pass


class ResponsiveThreadingTCPServer(SocketServer.ThreadingTCPServer):
    def __init__(self, server_address, RequestHandlerClass, lock, timeout = 5.0):
        SocketServer.ThreadingTCPServer.__init__(self, server_address, RequestHandlerClass)
        self.timeout = timeout  # Default timeout: 5.0 seconds
        self.lock = lock        # Should be a preexisting threading.RLock() object
        self.lock.acquire()
        self.QuitFlag = 0
        self.lock.release()

    def get_request(self):
        socklist = [self.socket]
        while 1:
            # Select with a timeout, then poll a quit flag. An alternate
            # approach would be to let the master thread "wake us up"
            # with
            # a socket connection.
            ready = select.select(socklist, [], [], self.timeout)
            self.lock.acquire()
            time_to_quit = self.QuitFlag
            self.lock.release()
            if time_to_quit:
                raise TimeToQuit        # Get out now
            if ready[0]:        # A socket was ready to read
                return SocketServer.ThreadingTCPServer.get_request(self)
            else:               # We timed out, no connection yet
                pass            # Just go back to the select()

    def serve_forever(self):
        try:
            SocketServer.ThreadingTCPServer.serve_forever(self)
        except TimeToQuit:
            self.server_close() # Clean up before we leave



#
# Test the ResponsiveThreadingTCPServer
#

class CommandHandler(SocketServer.StreamRequestHandler):
    def handle(self):
        self.requestline = self.rfile.readline()
        while self.requestline[-1] in ['\r', '\n']:
            self.requestline = self.requestline[:-1]   # Strip trailing CR/LF if any
        command = 'do_' + self.requestline.upper()
        if not hasattr(self, command):
            self.send("Unknown command:")
            self.send(self.requestline)
            return
        method = getattr(self, command)
        method()

    def send(self, text):
        self.wfile.write(text)
        self.wfile.flush()

    def do_SONG(self):
        self.send('Old McDonald had a farm, E-I-E-I-O!\r\n')

    def do_QUOTE(self):
        self.send('And now for something completely different...\r\n')

    def do_POEM(self):
        self.do_HAIKU()

    def do_HAIKU(self):
        self.send('A haiku on "Error 404"\r\n')
        self.send('\r\n')
        self.send('  You step in the stream,\r\n')
        self.send('  But the water has moved on.\r\n')
        self.send('  This file is not here.\r\n')

#
# Pattern of a master thread
#

class MasterControlThread(threading.Thread):
    def __init__(self, port=31310):
        threading.Thread.__init__(self)
        self.port = port
        self.lock = threading.RLock()

    def run(self):
        print "Serving on port", self.port
        self.server = ResponsiveThreadingTCPServer(('', self.port), CommandHandler, self.lock)
        # Note: Five seconds timeout instead of a minute, for testing.
        self.thread = threading.Thread(target = self.server.serve_forever)
        self.thread.start()
        print "Press Enter to quit."
        raw_input()
        # Tell the server it's time to shut down
        self.server.lock.acquire()
        self.server.QuitFlag = 1
        self.server.lock.release()
        print "Waiting for server to shut down (could take several seconds)..."
        self.thread.join()
        print "Exiting now."


if __name__ == '__main__':
    mct = MasterControlThread()
    mct.start()
    mct.join()


