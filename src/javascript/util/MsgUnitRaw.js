/**
 * @class Holds a xmlBlaster MsgUnit (for publish, get return or update).
 */
export class MsgUnitRaw {
  /**
   * @param {string} methodName The message type like "publish" or "subscribe", see MethodName.java
   * @param {QosData | ConnectQosData} qosData Can be of type ConnectQosData
   * @param {KeyData} keyData The message key
   * @param {string} content The message payload
   */
  constructor(qosDataStr=null, keyDataStr=null, contentStr="") {
    this.qosDataStr = qosDataStr;
    this.keyDataStr = keyDataStr;
    this.contentStr = contentStr;
  }

  getQosDataStr() {
    return this.qosDataStr;
  }

  getKeyDataStr() {
    return this.keyDataStr;
  }

  getContentStr() {
    return this.contentStr;
  }
}