
import blaster
server = blaster.Blaster('localhost', 3412, [])
server.login('myname', '', None)
for unit in server.get("<key oid='__cmd:?totalMem'/>", "<qos/>"):
  print unit.xmlKey, unit.content, unit.qos
server.logout()
