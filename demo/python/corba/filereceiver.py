
import sys, os, glob, shutil, blaster, logging

class Downloader:

  def __init__(self, options, argv):
    self.options = options
    self.blaster = blaster.Blaster(options.server, 3412, argv)

  def run(self):
    logging.info("login as %s", self.options.login)
    self.blaster.login(self.options.login, self.options.password, None)
    logging.info("sessionId: %s", self.blaster.sessionId)
    logging.info("serverIOR: %s", self.blaster.serverIOR)
    try:
      for file in glob.glob(os.path.abspath(self.options.files)):
        try:
          logging.info("Downloading %s", file)
          content = open(file, "rb").read()
          address, oid = os.path.split(file)
          if self.options.move:
            logging.info("backup %s to %s", oid, self.options.move)
            shutil.copy(file, self.options.move)
          logging.info("removing %s", file)
          os.remove(file)
          rqos = self.blaster.send(self.options.receiver, address, oid, content)
          logging.info("%s", rqos)
        except:
          logging.exception("fail to download file %s", file)
    finally:
      logging.info("logout")
      self.blaster.logout()
    
def setupLog(options):
  from warnings import filterwarnings
  filterwarnings("ignore", category = DeprecationWarning, module = "logging")
  hdlr = logging.StreamHandler()
  fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
  hdlr.setFormatter(fmt)
  logging.getLogger().addHandler(hdlr)
  logging.getLogger().setLevel(logging.INFO)
  
def main(argv):
  from optparse import OptionParser
  parser = OptionParser("usage: \%prog [-f <filemask>] [-s <server>] [-l <login>] [-r <receiver>]")
  parser.add_option("-f", "--files", dest="files", help="files mask", default="*.*")
  parser.add_option("-s", "--server", dest="server", help="auth server host", default="localhost")
  parser.add_option("-l", "--login", dest="login", help="login", default="")
  parser.add_option("-p", "--password", dest="password", help="password", default="")
  parser.add_option("-r", "--receiver", dest="receiver", help="message receiver", default="Diasoft5NT")
  parser.add_option("-m", "--move", dest="move", help="move to directory", default="")
  (options, args) = parser.parse_args(argv)
  setupLog(options)
  downloader = Downloader(options, [])
  downloader.run()

if __name__ == '__main__':
  main(sys.argv)
