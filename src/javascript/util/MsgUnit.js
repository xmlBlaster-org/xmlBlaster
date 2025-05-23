import { KeyData } from "../client/KeyData.js";
import { QosData } from "../client/QosData.js";
import { MsgUnitRaw } from "./MsgUnitRaw.js";
import { ConnectQosData } from "../client/ConnectQosData.js";

/**
 * @class Holds a xmlBlaster MsgUnit (for publish, get return or update).
 */
export class MsgUnit {
/**
 * @param {string} methodName The message type like "publish" or "subscribe", see MethodName.java
 * @param {QosData | ConnectQosData} qosData Can be of type ConnectQosData
 * @param {KeyData} keyData The message key
 * @param {string} content The message payload
 */
constructor(methodName, qosData=null, keyData=null, content="") {
  this.sessionId = "";
  this.methodName = methodName || "";
  /**
   * @type {import("./XmlBlasterException").XmlBlasterException}
   */
  this.xmlBlasterException = null;
  /**
   * @type {QosData | ConnectQosData}
   */
  this.qosData = qosData; // of type QosData BUT in case of "connect": ConnectQosData
  this.keyData = keyData; // new org.xmlBlaster.client.KeyData();
  this.content = content || "";
}
  isConnect() { return ("connect" == this.methodName); }
  isException() { return ("exception" == this.methodName); }
  isUpdate() { return ("update" == this.methodName); }
  isGet() { return ("get" == this.methodName); }
  isSubscribe() { return ("subscribe" == this.methodName); }
  isUnSubscribe() { return ("unSubscribe" == this.methodName); }
  isPublish() { return ("publish" == this.methodName); }
  isErase() { return ("erase" == this.methodName); }
  
  /**
   * @param {import("./XmlBlasterException").XmlBlasterException} xmlBlasterException
   */
  setXmlBlasterException(xmlBlasterException=null) { 
    this.xmlBlasterException = xmlBlasterException;
  }

  /**
   * @return {import("./XmlBlasterException").XmlBlasterException} or null
   */
  getXmlBlasterException() {
    return this.xmlBlasterException;
  }

  /**
   * @param {string}
   */
  setSessionId(sessId) {
    this.sessionId = sessId;
  }
  /**
   * @returns {string}
   */
  getSessionId() { return this.sessionId; };

  /**
   * @return {QosData | ConnectQosData}
   */
  getQosData() {
    return this.qosData;
  };

  /**
   * Convenience typed accessor for code editor
   * @return {ConnectQosData}
   */
  getConnectQos() {
    // @ts-ignore
    return this.qosData;
  }
  
  /**
   * Convenience typed accessor for code editor
   * @return {QosData}
   */
  getUpdateQos() {
    // @ts-ignore
    return this.qosData;
  }

  /**
   * @return {KeyData}
   */
  getKeyData() {
    return this.keyData;
  }

  getContentStr() {
    return this.content;
  }

  toMsgUnitRaw() {
    return new MsgUnitRaw(this.getQos().toXml(), this.getKey().toXml(), this.getContentStr());
  }
  
  dump() {
    var str = "<"+this.methodName+">\n";
    if (this.qosData != null) str += this.qosData.dump();
    if (this.keyData != null) str += "\n" + this.keyData.dump();
    if (this.content.length > 0) str += "\n<content>" + this.content + "</content>";
    str += "\n</"+this.methodName+">";
    return str;
  }

  /**
   * @return {string} Usable for xmlBlaster scripting. 
   */
  toXml() {
    return this.dump();
  }
}