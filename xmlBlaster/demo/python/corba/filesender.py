
import sys, os, glob, shutil, logging, blaster
from xml.dom import minidom
from clientIdl__POA import BlasterCallback

class BlasterCallback_i(BlasterCallback):

  def __init__(self, options):
    self.options = options
  
  def update(self, sessionId, msgUnitArr):
    logging.info("update. sessionId=%s", sessionId)
    result = []
    for msgUnit in msgUnitArr:
      try:
        logging.info("%s%s", msgUnit.xmlKey, msgUnit.qos)
        dom = minidom.parseString(msgUnit.xmlKey)
        oid = dom.getElementsByTagName("key")[0].getAttribute("oid").encode()
        addresses = dom.getElementsByTagName("address")
        if addresses.length > 0:
          address = addresses[0].firstChild.nodeValue
        else:
          address = self.options.directory
        file = os.path.join(address, oid)
        logging.info("save content to %s", file)
        open(file, "wb").write(msgUnit.content)
        result.append("<qos><state id='OK'/></qos>")
      except:
        logging.exception("failed to upload message")
        result.append("<qos><state id='ERROR'/></qos>")
    return result

  def updateOneway(self, sessionId, msgUnitArr):
    logging.info("updateOneway. sessionId=%s", sessionId)
    for msgUnit in msgUnitArr:
      logging.info("%s%s", msgUnit.xmlKey, msgUnit.qos)

  def ping(self, qos):
    logging.info("ping. qos=%s", qos)
    return ""

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
  parser = OptionParser("usage: \%prog [-d <directory>] [-s <server>] [-l <login>] [-p <password>]")
  parser.add_option("-d", "--directory", dest="directory", help="upload directory", default=".\\")
  parser.add_option("-s", "--server", dest="server", help="auth server host", default="localhost")
  parser.add_option("-l", "--login", dest="login", help="login", default="")
  parser.add_option("-p", "--password", dest="password", help="password", default="")
  (options, args) = parser.parse_args()
  setupLog(options)
  uploader = blaster.Blaster(options.server, 3412, [])
  try:
    logging.info("login as %s", options.login)
    callback = BlasterCallback_i(options)
    uploader.login(options.login, options.password, callback)
    try:
      logging.info("sessionId: %s", uploader.sessionId)
      logging.info("serverIOR: %s", uploader.serverIOR)
      logging.info("callbackIOR: %s", uploader.callbackior)
      logging.info("start uploading")
      uploader.orb.run()
    finally:
      logging.info("logout")
      uploader.logout()
  except KeyboardInterrupt, ex:
    logging.info("stop uploading")
    uploader.orb.shutdown(True)
    logging.info("done")

if __name__ == '__main__':
  main(sys.argv)
