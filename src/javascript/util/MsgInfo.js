import { KeyData } from "../client/KeyData.js";
import { QosData } from "../client/QosData.js";
import { ConnectQosData } from "../client/ConnectQosData.js";
import { MsgUnit } from "./MsgUnit.js";

/**
 * @class Holds a xmlBlaster socket message, encode/decode Xbf
 */
export class MsgInfo {
  static currRequestId = 1;
  /**
   * @param {string} msgType I=Invoke, R=Return value, E=Exception
   * @param {string} methodName connect/disconnect/publish/subscribe/get/ping/erase/unsubscribe/...
   * @param {string} secretSessionId
   * @param {Array.<MsgUnitRaw>|MsgUnitRaw} msgUnits
   */
  constructor(msgType, methodName, secretSessionId="", msgUnitsRaw=[]) {
    this.msgType = msgType;
    this.methodName = methodName;
    this.secretSessionId = secretSessionId;
    this.msgUnits = msgUnitsRaw;
    this.requestId = MsgInfo.currRequestId++;

    if (!Array.isArray(this.msgUnits))
      this.msgUnits = [this.msgUnits];
  }

  /**
   * @param {ArrayBuffer} xbfData
   */
  static parseXbf(xbfData) {
    // TODO: Expects no checksum
    xbfData = new Uint8Array(xbfData);
    const decoder = new TextDecoder();

    const msgLenStr = decoder.decode(xbfData.slice(0, 10)).trim();
    const flagBytes = xbfData.slice(10, 16);
    const msgType = String.fromCharCode(flagBytes[2]);

   
    let i = 16;
    let end = xbfData.indexOf(0, i);
    const requestId = decoder.decode(xbfData.slice(i, end)); i = end + 1; end = xbfData.indexOf(0, i);
    const methodName = decoder.decode(xbfData.slice(i, end)); i = end + 1; end = xbfData.indexOf(0, i);
    const sessId = decoder.decode(xbfData.slice(i, end)); i = end + 1; end = xbfData.indexOf(0, i);
    const lenUnzipped = decoder.decode(xbfData.slice(i, end)); i = end + 1; // end = remainder.indexOf("\0", i);

    const msgUnits = [];

    while (i < xbfData.length) {
      let end = xbfData.indexOf(0, i);
      const qosStr = decoder.decode(xbfData.slice(i, end)) || "<qos/>";
      i = end + 1;
      end = xbfData.indexOf(0, i);
      const keyStr = decoder.decode(xbfData.slice(i, end)) || "<key/>";
      i = end + 1;
      end = xbfData.indexOf(0, i);
      const lenStr = decoder.decode(xbfData.slice(i, end));
      i = end + 1;
      end = i + parseInt(lenStr);
      const contentStr = decoder.decode(xbfData.slice(i, end));
      i = end; // no \0 to skip

      let qosData = null;
      if (methodName == "connect") {
        qosData = ConnectQosData.parseString(qosStr);
      } else {
        qosData = QosData.parseString(qosStr);
      }
      const keyData = KeyData.parseString(keyStr);

      const msgUnit = new MsgUnit(methodName, qosData, keyData, contentStr);
      msgUnit.setSessionId(sessId);
      msgUnits.push(msgUnit);
    }

    const result = new MsgInfo(msgType, methodName, sessId, msgUnits);
    result.requestId = requestId;
    return result;
  }

  isException() {
    return this.msgType == "E";
  }
  /**
   * @return {string}
   */
  getRequestId() {
    return this.requestId.toString();
  }
  /**
   * @return {string}
   */
  getMsgType() {
    return this.msgType;
  }

  /**
   * @return {string}
   */
  getMethodName() {
    return this.methodName;
  }

  /**
   * @return {string}
   */
  getSecretSessionId() {
    return this.secretSessionId;
  }

  /**
   * @return {Array.<MsgUnit>}
   */
  getMsgUnits() {
    return this.msgUnits;
  }

  setRequestId(requestId) {
    this.requestId = requestId;
  }

  /**
   * @param {string} msgType I=Invoke, R=Return value, E=Exception
   * @param {string} methodName connect/disconnect/publish/subscribe/get/ping/erase/unsubscribe/...
   * @param {string} secretSessionId
   * @param {string} key
   * @param {string} qos
   * @param {string} content
   * @returns {Uint8Array}
   */
  encodeXbf() {
    // msgLen[10] flag[6] requestId methodName secretSessionId lenUnzipped userData checkSum[10]
    // +---------+-------+---------*----------*----------------*-----------*--------+-----------+
    // * parts are 0-terminated (need 1 byte more)
    const encoder = new TextEncoder();
    const zeroByte = new Uint8Array([0]);

    const userData = [];
    for (const msgUnit of this.msgUnits) {
      const qosData = encoder.encode(msgUnit.getQosDataStr() || "");
      const keyData = encoder.encode(msgUnit.getKeyDataStr() || "");
      const content = encoder.encode(msgUnit.getContentStr());
      userData.push(qosData);
      userData.push(zeroByte);
      userData.push(keyData);
      userData.push(zeroByte);
      userData.push(encoder.encode(content.length.toString()));
      userData.push(zeroByte);
      userData.push(content);
    }


    let parts = [];
    parts.push(new Uint8Array([0, 0, this.msgType.charCodeAt(0), 0, 0, "1".charCodeAt(0)])); // FLAGS
    parts.push(encoder.encode(this.requestId.toString()));
    parts.push(zeroByte);
    parts.push(encoder.encode(this.methodName));
    parts.push(zeroByte);
    parts.push(encoder.encode(this.secretSessionId));
    parts.push(zeroByte);
    const userDataLen = userData.reduce((sum, d) => sum + d.length, 0);
    parts.push(encoder.encode(userDataLen.toString()))
    parts.push(zeroByte);
    parts = parts.concat(userData);

    const totalLen = 10 + parts.reduce((sum, d) => sum + d.length, 0);
    const lenStr = totalLen.toString().padStart(10, " ");
    parts.splice(0, 0, encoder.encode(lenStr));

    const result = new Uint8Array(totalLen);
    let writeIndex = 0;
    for (const part of parts) {
      result.set(part, writeIndex);
      writeIndex += part.length;
    }
    return result;
  }
}