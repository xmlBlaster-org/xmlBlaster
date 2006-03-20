__version__ = '030415'
__author__ = 'spex66@gmx.net'



from BaseService import BaseService
import threading, sys

from code import InteractiveConsole
try:
    import readline
except:
    pass

class ShellService(BaseService, InteractiveConsole):
    """
    A subclass of Thread to emulate a shell.
    """

    def __init__(self, engine, name=None):
        BaseService.__init__(self, name or 'ShellService')
        InteractiveConsole.__init__(self, locals={'_': engine})
        

    # target that is executed by thread ########################################

    def run(self):
        """Closely emulate the interactive Python console."""
        
        # INIT
        
        try:
            sys.ps1
        except AttributeError:
            sys.ps1 = ">>> "
        try:
            sys.ps2
        except AttributeError:
            sys.ps2 = "... "

        cprt = 'Type "copyright", "credits" or "license" for more information.'

        self.write("Python %s on %s\n%s\n(%s)\n" %
                   (sys.version, sys.platform, cprt,
                    self.__class__.__name__))
        
        more = 0
        
        # SERVE Python prompt
        
        print "%s starts" % (self.getName(),)
        while self.isNotStopped():
            if more:
                prompt = sys.ps2
            else:
                prompt = sys.ps1

            try:
                line = raw_input(prompt)
            except EOFError:
                print '...ShSrv EOF...'
                continue
            
            if line.lower() in ('bye', 'quit', 'exit'):
                # get exit commands
                self.loop = 0
                self.locals['_'].post_event( ('quit', ))
            else:
                more = self.push(line)
                

        print "%s ends" % (self.getName(),)


    def stop(self):
        print 'Press any key to leave the shell...'
        BaseService.stop(self)