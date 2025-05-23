import { toBoolean } from "../util.js";

/**
 * See org.xmlBlaster.util.parseXmlBlasterResponse() which creates us for server side exceptions 
 * @class Holds an XmlBlasterException instance, thrown locally or from server. 
 */
export class XmlBlasterException {

  /**
   * @constructor Create an exception instance
   * @param {string} errorCodeStr See org.xmlBlaster.util.ErrorCode.java 
   * @param {string} location_ E.g. "XbAccess.js"
   * @param {string} rawMessage The English error text
   * @param {boolean} isServerSide true if coming from server
  */
  constructor(errorCodeStr, location_, rawMessage, isServerSide) {
    this.errorCodeStr = errorCodeStr || "internal.unknown";
    this.errorCodeStrOrig = "";
    this.location = location_ || "";
    this.rawMessage = rawMessage || "-";
    //this.isServerSide = isServerSide || false;
    this.embeddedMessage = "";
    this.xmlBlasterServerSide = isServerSide || false;
    this.tomcatServerSide = false;
    this.httpStatus = 0; // e.g. when Ajax call fails when tomcat has stopped: = 503  (Service Unavailable)
    this.isLocalizedAlready = false;
  }

  getErrorCodeStr() {
    return this.errorCodeStr;
  }
  setErrorCodeStr(str) {
    var str = str || "user.internal";
    this.errorCodeStrOrig = this.errorCodeStr;
    this.errorCodeStr = str;
  }

  getHttpStatus() {
    return this.httpStatus;
  };

  isHttpStatusOk() {
    return this.httpStatus == 0 || this.httpStatus == 200;
  }

  isJavascriptSide() {
    return (this.tomcatServerSide == false && this.xmlBlasterServerSide == false);
  }

  isServerSide() {
    return (this.tomcatServerSide == true || this.xmlBlasterServerSide == true);
  }

  isXmlBlasterServerSide() {
    return this.xmlBlasterServerSide;
  }

  isTomcatServerSide() {
    return this.tomcatServerSide;
  }

  setTomcatServerSide(serverSide) {
    this.tomcatServerSide = toBoolean(serverSide, false);
    if (this.tomcatServerSide)
      this.xmlBlasterServerSide = false;
  }

  setXmlBlasterServerSide(serverSide) {
    this.xmlBlasterServerSide = toBoolean(serverSide, true);
    if (this.xmlBlasterServerSide)
      this.tomcatServerSide = false;
  }

  setLocation(location) {
    this.location = location || "";
  }
  getLocation() {
    return this.location;
  }


  getRawMessage() {
    return this.rawMessage;
  }
  getMessage() {
    return this.getRawMessage();
  }
  setEmbeddedMessage(embeddedMessage) {
    this.embeddedMessage = embeddedMessage || "";
    //<embeddedMessage><![CDATA[Original erroCode=user.security.authentication.accessDenied]]></embeddedMessage>
    var start = this.embeddedMessage.indexOf("errorCode=");
    if (start >= 0 && this.embeddedMessage.length > 11) {
      this.errorCodeStr = this.embeddedMessage.substring(start + 10);
      var end = this.errorCodeStr.indexOf(" ");
      if (end > 0)
        this.errorCodeStr = this.errorCodeStr.substring(0, end);
    }
  }
  setMessage(rawMessage) {
    this.rawMessage = rawMessage || "";
    //Authorize throws such ugly mix:
    //<message><![CDATA[#16983M errorCode=user.security.authentication.accessDenied: Login to dev failed due to missing privileges. Access on account with loginName 'joe' is denied]]></message>
    var start = this.rawMessage.indexOf("errorCode=");
    if (start >= 0) {
      // "Original errorCode=" see XmlBlasterException.java#changeErrorCode()
      var tmp = this.rawMessage.substring(start + 9);
      var end = tmp.indexOf(": ");
      if (end > 0 && (end + 2) < tmp.length)
        this.rawMessage = tmp.substring(end + 2);
    }
  }
  toString() {
    var text = "errorCode=" + this.getErrorCodeStr() + " message=" + this.getRawMessage();
    //if (this.embeddedMessage != null && this.embeddedMessage.length() > 0) {
    //   text += " : " + embeddedMessage;
    //}
    return text;
  }
  /**
   * Can be used by locally thrown exceptions where the translation is already done
   * to controll what is displayed in error popup.  
   */
  isLocalized() {
    return this.isLocalizedAlready;
  }
  setLocalized(isLocalized) {
    this.isLocalizedAlready = isLocalized;
  }

  isInternal() {
    return this.errorCodeStr.startsWith("internal");
  }

  isResource() {
    return this.errorCodeStr.startsWith("resource");
  }

  isCommunication() {
    return this.errorCodeStr.startsWith("communication");
  }

  isUser() {
    return this.errorCodeStr.startsWith("user");
  }

  isTransaction() {
    return this.errorCodeStr.startsWith("transaction");
  }

  isAuthentication() {
    return this.errorCodeStr.startsWith("user.security.authentication");
  }

  /** Only for that AjaxServlet.java kills the browser session, see boolean killConnection = ... */
  tomcatWillKillConnection() {
    if (this.isTomcatServerSide()) {
      var killsIt = this.isInternal() || this.isCommunication() || this.isResource() || this.isAuthentication();
      return killsIt;
    }
    return false;
  }

}