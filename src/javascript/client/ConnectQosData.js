import { MapObject } from "../util/MapObject.js";
import { SecurityService } from "../client/SecurityService.js";
import { SessionQos } from "../client/SessionQos.js";
import { SessionName } from "../util/SessionName.js";
import * as orgXmlBlasterUtil from "../util.js";
import { ClientProperty } from "../util/ClientProperty.js";
import { log } from "../util/Logger.js";

const ME1 = "ConnectQosData.js";

/**
 * Holds a ConnectQos or ConnectReturnQos instance of a xmlBlaster message
 * @todo queue and address info
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html
 */
export class ConnectQosData {
   /**
    * @param {Document} domDocument The complete XML W3C DOM tree:
    * interface Document : Node
    * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
    * @param {Node} currentNode The W3C Node
    */
   constructor(domDocument = null, currentNode = null) {
      this.securityService = null;
      this.sessionQos = null;
      this.reconnected = false;
      this.priority = 5;
      this.persistent = false;
      this.instanceId = "";
      this.clientProperties = new MapObject();
      if (currentNode == null) {
         return;
      }
      for (var i = 0; i < currentNode.childNodes.length; i++) {
         var node = currentNode.childNodes[i];
         if (node.nodeType != Node.ELEMENT_NODE) {
            var val = orgXmlBlasterUtil.isDefined(node.nodeValue) ? node.nodeValue : "";
            var tmp = orgXmlBlasterUtil.trim(val);
            if (tmp.length == 0) continue;
            val = ": '" + val + "'";
            var txt = "ConnectQosData(): Ignoring xmlBlaster response node " + orgXmlBlasterUtil.getXPathLocation(node, true) + val;
            (node.nodeType == Node.COMMENT_NODE || node.nodeType == Node.TEXT_NODE) ? log.debug(ME1 + txt) : log.warn(ME1 + txt);
            continue;
         }
         switch (node.nodeName) {
            case "securityService":
               this.securityService = new SecurityService();
               if (node.getAttribute('type') != null)
                  this.securityService.type = node.getAttribute('type');
               if (node.getAttribute('version') != null)
                  this.securityService.version = node.getAttribute('version');
               for (var jj = 0; jj < node.childNodes.length; jj++) {
                  var tmpNode = node.childNodes[jj];
                  if (tmpNode.nodeType == Node.CDATA_SECTION_NODE) {
                     var valCdata = tmpNode.nodeValue;
                     log.debug(ME1 + "Parsing valCdata=" + valCdata);
                     this.securityService.user = orgXmlBlasterUtil.extractFromTag(valCdata, "user");
                     this.securityService.passwd = orgXmlBlasterUtil.extractFromTag(valCdata, "passwd");
                     break;
                  }
                  let val = (tmpNode.firstChild) ? tmpNode.firstChild.nodeValue : null;
                  if (val != null) {
                     if (tmpNode.nodeName == "user")
                        this.securityService.user = val;
                     else if (tmpNode.nodeName == "passwd")
                        this.securityService.passwd = val;
                  }
               }
               break;
            case "session":
               this.sessionQos = new SessionQos();
               this.sessionQos.sessionName = new SessionName(node.getAttribute('name'));
               if (node.getAttribute('timeout') != null)
                  this.sessionQos.sessionTimeout = new Number(node.getAttribute('timeout')).valueOf();
               if (node.getAttribute('maxSessions') != null)
                  this.sessionQos.maxSessions = new Number(node.getAttribute('maxSessions')).valueOf();
               if (node.getAttribute('clearSessions') != null)
                  this.sessionQos.clearSessions = orgXmlBlasterUtil.parseBoolean(node.getAttribute('clearSessions'));
               if (node.getAttribute('reconnectSameClientOnly') != null)
                  this.sessionQos.reconnectSameClientOnly = orgXmlBlasterUtil.parseBoolean(node.getAttribute('reconnectSameClientOnly'));
               if (node.getAttribute('sessionId') != null)
                  this.sessionQos.sessionId = node.getAttribute('sessionId');
               break;
            case "instanceId":
               this.instanceId = orgXmlBlasterUtil.collectTextFromNodeChilds(node);
               break;
            case "queue":
               log.debug(ME1 + "ConnectQosData(" + orgXmlBlasterUtil.getXPathLocation(node, true) + "): Parsing not implemented");
               break;
            case "serverRef":
               log.debug(ME1 + "ConnectQosData(" + orgXmlBlasterUtil.getXPathLocation(node, true) + "): Parsing not implemented");
               break;
            case "reconnected":
               this.reconnected = orgXmlBlasterUtil.collectBooleanFromNodeChilds(node, this.reconnected);
               break;
            case "priority":
               var val = orgXmlBlasterUtil.collectTextFromNodeChilds(node);
               this.priority = orgXmlBlasterUtil.toNumber(val, this.priority);
               break;
            case "persistent":
               this.persistent = orgXmlBlasterUtil.collectBooleanFromNodeChilds(node, this.persistent);
               break;
            case "clientProperty":
               var cp = new ClientProperty(node.getAttribute('name'));
               if (node.getAttribute('type') != null) cp.type = node.getAttribute('type');
               if (node.getAttribute('encoding') != null) cp.encoding = node.getAttribute('encoding');
               if (node.getAttribute('charset') != null) cp.charset = node.getAttribute('charset');
               cp.value = orgXmlBlasterUtil.collectTextFromNodeChilds(node);
               this.clientProperties.put(cp.name, cp);
               break;
            default:
               log.warn(ME1 + "ConnectQosData(): Ignoring xmlBlaster response tag " + orgXmlBlasterUtil.getXPathLocation(node, true));
               break;
         }
      }
   }

   /**
      * @param {string} xmlStr
      */
   static parseString(xmlStr) {
      const document = org.xmlBlaster.util.getDOMDocument(xmlStr);
      return new ConnectQosData(document, document.documentElement);
   }
   /** @return {SecurityService} A SecurityService instance */
   getSecurityService() { return this.securityService; }
   /** @return {SessionQos} A SessionQos instance */
   getSessionQos() { return this.sessionQos; }
   getInstanceId() { return this.instanceId; }
   isReconnected() { return this.reconnected; }
   getPriority() { return this.priority; }
   isPersistent() { return this.persistent; }
   hasClientProperty(key) {
      var cp = this.clientProperties.get(key);
      if (!orgXmlBlasterUtil.isDefined(cp)) return false;
      return true;
   }
   /**
    * If defaultValue is not given the ClientProperty instance is returned or null if not found
    * If defaultValue is given: If key is found the value is returned (typically a String instance) else the given defaultValue
    */
   getClientProperty(key, defaultValue) {
      if (defaultValue == undefined)
         return this.clientProperties.get(key);
      var cp = this.clientProperties.get(key);
      if (cp == undefined || cp == null) return defaultValue;
      return cp.getValue();
   }
   dump() {
      var str = "<qos>";
      if (this.securityService != null) str += this.securityService.dump();
      if (this.sessionQos != null) str += this.sessionQos.dump();
      str += "<persistent>" + this.persistent + "</persistent>";
      str += "<priority>" + this.priority + "</priority>";
      this.clientProperties.moveFirst();
      while (this.clientProperties.next()) {
         //str += this.clientProperties.getKey() + " = " + this.clientProperties.getValue());
         //"<clientProperty name='myTransactionId'>TODO</clientProperty>" +
         str += this.clientProperties.getValue().dump();
      }
      str += "</qos>";
      return str;
   }
}