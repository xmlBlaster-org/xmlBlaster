__version__ = '030415'
__author__ = 'spex66@gmx.net'


import threading
import time

# from Python Cookbook (O'Reilley)
#      Chapter 6.2 Terminating a thread
#
#      isNotStopped and stop instead of join are slightly differences
#

class BaseService(threading.Thread):

    def __init__(self, name=None):
        """ constructor, setting initial variables """
        self._stopevent = threading.Event()

        threading.Thread.__init__(self, name=(name or 'BaseService'))

    def isNotStopped(self):
        "Test if thread has got an ending event or not"
        return not self._stopevent.isSet()


    def run(self):
        """ example main control loop 
        
            has to be specialized in subtyping
        """
        print "%s starts" % (self.getName(),)

        count = 0
        _sleepperiod = 1.0

        while self.isNotStopped():

            count += 1
            print "loop %d" % (count,)
            
            # seems to wait on a set _stopevent
            #self._stopevent.wait(_sleepperiod)

        print "%s ends" % (self.getName(),)

    def stop(self, timeout=None):
        self.join(timeout)

    def join(self, timeout=None):
        """ Stop the thread. """
        self._stopevent.set()
        # Calling baseclass join()
        threading.Thread.join(self, timeout)

if __name__ == "__main__":
    testthread = BaseService()
    testthread.start()

    time.sleep(10.0)

    testthread.stop()
