
import win32serviceutil, win32service
from optparse import OptionParser
from blaster import Blaster
from filesender import BlasterCallback_i
import logging, sys

class XMLBlasterFileSenderService(win32serviceutil.ServiceFramework):
    _svc_name_ = "XMLBlasterFileSenderService"
    _svc_display_name_ = "XMLBlaster File Sender Service"

    def __init__(self, args):
        win32serviceutil.ServiceFramework.__init__(self, args)
        parser = OptionParser("usage: \%prog [-d <directory>] [-s <server>] [-l <login>] [-p <password>]")
        parser.add_option("-d", "--directory", dest="directory", help="upload directory", default=".\\")
        parser.add_option("-s", "--server", dest="server", help="auth server host", default="localhost")
        parser.add_option("-l", "--login", dest="login", help="login", default="")
        parser.add_option("-p", "--password", dest="password", help="password", default="")
        parser.add_option("-c", "--config", dest="config", help="config file", default="")
        parser.add_option("-f", "--filelog", dest="filelog", help="log file name", default="service.log")
        argv = eval(win32serviceutil.GetServiceCustomOption(XMLBlasterFileSenderService, "argv", "[]"))
        (self.options, args) = parser.parse_args(argv)
        self.setupLog()
        logging.info("argv=%s", argv)

    def setupLog(self):
        from warnings import filterwarnings
        filterwarnings("ignore", category = DeprecationWarning, module = "logging")
        if self.options.config:
            logging.fileConfig(self.options.config)
        else:
            hdlr = logging.FileHandler(self.options.filelog)
            fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
            hdlr.setFormatter(fmt)
            logging.getLogger().addHandler(hdlr)
            logging.getLogger().setLevel(logging.INFO)
        
    def SvcDoRun(self):
        import servicemanager
        self.sender = Blaster(self.options.server, 3412, [])
        try:
            logging.info("login as %s", self.options.login)
            callback = BlasterCallback_i(self.options)
            self.sender.login(self.options.login, self.options.password, callback)
            logging.info("sessionId: %s", self.sender.sessionId)
            logging.info("serverIOR: %s", self.sender.serverIOR)
            logging.info("callbackIOR: %s", self.sender.callbackior)
            logging.info("start uploading")
            self.sender.orb.run()
            logging.info("done")
            self.ReportServiceStatus(win32service.SERVICE_STOPPED)
        except:
            logging.exception("fatal error")

    def SvcStop(self):
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        logging.info("logout")
        self.sender.logout()
        logging.info("stop uploading")
        self.sender.orb.shutdown(True)

    def SvcPause(self):
        logging.info("pause")
        self.sender.poa._get_the_POAManager().hold_requests(True)
        self.ReportServiceStatus(win32service.SERVICE_PAUSED)

    def SvcContinue(self):
        logging.info("continue")
        self.sender.poa._get_the_POAManager().activate()
        self.ReportServiceStatus(win32service.SERVICE_RUNNING)

if __name__=='__main__':
    win32serviceutil.HandleCommandLine(XMLBlasterFileSenderService)
