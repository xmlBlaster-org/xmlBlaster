
import CORBA, PortableServer
import authenticateIdl, authenticateIdl__POA
import clientIdl, clientIdl__POA
import serverIdl, serverIdl__POA
from xml.dom import minidom

def get_ior(server, service, port = 3412):
  import httplib
  conn = httplib.HTTPConnection(server, port)
  conn.request("GET", "/%s.ior"%service)
  resp = conn.getresponse()
  if resp.status <> 200:
    raise Exception("Can not find ior")
  ior = resp.read()
  conn.close()
  return ior

class Blaster:

  def __init__(self, server, port, argv):
    self.server = server
    self.port = port
    self.orb = CORBA.ORB_init(argv, CORBA.ORB_ID)

  def login(self, login, password, callback):
    if callback:
      self.poa = self.orb.resolve_initial_references("RootPOA")
      self.poa._get_the_POAManager().activate()
      id = self.poa.activate_object(callback)
      obj = self.poa.id_to_reference(id)
      self.callbackior = self.orb.object_to_string(obj)
    else:
      self.callbackior = ''
    authior = get_ior(self.server, "AuthenticationService", self.port)
    self.auth = self.orb.string_to_object(authior)
    self.auth = self.auth._narrow(authenticateIdl.AuthServer)
    if self.callbackior:
      callbackpart="<callback type='IOR'>%s</callback>"%self.callbackior
    else:
      callbackpart=""
    connectqos="""
    <qos>
      <securityService type="htpasswd" version="1.0">
        <![CDATA[
          <user>%s</user>
          <passwd>%s</passwd>
        ]]>
      </securityService>
      %s
    </qos>
    """%(login, password, callbackpart)
    connectionxml = self.auth.connect(connectqos)
    dom = minidom.parseString(connectionxml)
    self.sessionId = dom.getElementsByTagName("session")[0].getAttribute("sessionId").encode()
    self.serverIOR = dom.getElementsByTagName("serverRef")[0].firstChild.data.encode().strip()
    obj = self.orb.string_to_object(self.serverIOR)
    self.server = obj._narrow(serverIdl.Server)

  def get(self, key, qos):
    return self.server.get(key, qos)  
    
  def logout(self):        
    self.auth.disconnect(self.sessionId, "<qos/>")

  def send(self, receiver, address, oid, content):
    publishqos =  """
    <qos>
    <destination queryType='EXACT' forceQueuing='true'>%s</destination>
    <persistent/>
    </qos>
    """%receiver
    key = '<key oid="%s"><address>%s</address></key>'%(oid, address)
    msgUnit = serverIdl.MessageUnit(key, content, publishqos)
    return self.server.publish(msgUnit)
