
/**-----------------------------------------------------------------------------
@fileoverview
Access method to xmlBlaster MOM, seamless communication to xmlBlaster AjaxServlet with Javascript 1.5
@file      XmlBlasterAccess.js
@project   org.xmlBlaster
@comment   Receives messages from xmlBlaster over WebSocket formatted with client script
@author    Marcel Ruff mr@marcelruff.info 2007-12-06
@copyright Marcel Ruff mr@marcelruff.info (if not otherwise noted in function docu)
@see       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html
@javadoc   Comments style is Javadoc/JsDoc
          http://code.google.com/p/jsdoc-toolkit/wiki/TagReference
@code      Indent 2 spaces (like prototype or qooxdoo)
------------------------------------------------------------------------------*/

import { Base64 } from "./Base64.js";
import { MsgUnit } from "./MsgUnit.js";
import { MsgUnitRaw } from "./MsgUnitRaw.js";
import { MsgInfo } from "./MsgInfo.js";
import { QosData } from "../client/QosData.js";
import { KeyData } from "../client/KeyData.js";
import { XmlBlasterException } from "./XmlBlasterException.js";
import { SessionName } from "./SessionName.js";
import { ConnectQosData } from "../client/ConnectQosData.js";
import * as orgXmlBlasterUtil from "../util.js";
import { log } from "../../util/Logger.js";

var defaultXmlBlasterSecurityPlugin = "htpasswd";

/**
 * Access xmlBlaster AjaxServlet.java. 
 * @class Main object to access xmlBlaster with XmlBlaster Scripting
 * @constructor
 * @param {string} urlBase "wss://localhost"
 * @param {string} loginName The loginName
 */
export class XmlBlasterAccess {

  /**
   * @param {string} urlBase
   * @param {string=} loginName
   */
  constructor(urlBase, loginName, opt_options={}) {
    if (!orgXmlBlasterUtil.isDefined(urlBase))
      throw "IllegalArgument: Can't create org.xmlBlaster.client.XmlBlasterAccess without urlBase";

    this.urlBase = urlBase;
    this.loginName = (loginName) ? loginName : "anonymous";
    this.connectReturnQosData = null;
    this.isConnected = false;
    this.isIntentionalSocketClose = false;

    this.webSocket = null;
    this.responseHandlers = new Map(); // map request ID to waiting response handler
    this.updateCb = null;
    this.closeCb = null;
  }

  /** Callback type for XB update callback
   * @callback xbUpdateCallback
   * @param {MsgUnit[]} msgUnits
   */
  /**
   * Set XB update callback
   * @param {xbUpdateCallback} cbFunc
   */
  setUpdateCb(cbFunc) {
    this.updateCb = cbFunc;
  }

  /** Callback type for connection close callback
   * @callback connectionCloseCb
   * @param {boolean} closedIntentionally true=closed intentionally by calling shutdown(), false=caused by browser or server
   * @param {object} eventData event.data of websocket close event
   */
  /**
   * @param {connectionCloseCb} callback function cb function
   */
  setConnectionCloseCb(cbFunc) {
    this.closeCb = cbFunc;
  }

  /**
   * @returns {string}
   */
  getLoginName() {
    return this.loginName;
  }

  /**
   * @return {SessionName} The login name and session number
   */
  getSessionName() {
    if (this.connectReturnQosData && this.connectReturnQosData instanceof ConnectQosData) {
      const sessionName = this.connectReturnQosData.getSessionQos()?.getSessionName();
      if (sessionName) {
        return sessionName;
      }
    }
    const sessionName = new SessionName(this.getLoginName());
    return sessionName;
  }

  registerResponseHandler(requestId, responseHandlerFp) {
    this.responseHandlers.set(requestId, responseHandlerFp);
  }


  /**
  * @param {MsgInfo} msgInfo
  * @param {function} responseFp
  * @throws {XmlBlasterException}
  */
  sendMessageForResponse(msgInfo, responseFp) {
    if (!this.webSocket) {
      throw new XmlBlasterException("Not connected");
    }
    this.registerResponseHandler(msgInfo.getRequestId(), responseFp);
    this.webSocket.send(msgInfo.encodeXbf());
  }

  /**
   * @param {string} data
   */
  onWebSocketMessage(data) {
    const msg = MsgInfo.parseXbf(event.data);
    const requestId = msg.getRequestId();
    // If this is a response, return to the appropriate response hanlder, otherwise handle further
    const responseHandler = this.responseHandlers.get(requestId);
    if (responseHandler) {
      responseHandler(msg);
      this.responseHandlers.delete(requestId);
      return;
    }
    if (msg.getMethodName() == "update") {
      try {
        if (this.updateCb)
          this.updateCb(msg.getMsgUnits());
        const response = new MsgInfo("R", "update", this.getSessionId(), [new MsgUnitRaw("<qos><state id='OK'/></qos>", "", "")]);
        response.setRequestId(requestId);
        this.webSocket.send(response.encodeXbf());
      } catch (ex) {
        // send error to server?
        const response = new MsgInfo("E", "update", this.getSessionId(), new MsgUnitRaw("", "", "" + ex));
        response.setRequestId(requestId);
        this.webSocket.send(response.encodeXbf());
      }
    } else if (msg.getMethodName() == "updateOneway") {
      if (this.updateCb)
        this.updateCb(msg.getMsgUnits());
    } else if (msg.getMethodName() == "ping") {
      //console.log("PING")
      const response = new MsgInfo("R", "ping", this.getSessionId(), [new MsgUnitRaw("<qos><state id='OK'/></qos>", "", "OK")])
      response.setRequestId(requestId);
      this.webSocket.send(response.encodeXbf());
    }

  }

  getSessionId() {
    return this?.connectReturnQosData?.getSessionQos()?.getSessionId();
  }

  /**
   * Blocks until successfull connect.
   * Logs in with negative session Id.
   * @param {boolean} forceLoad if true tells servlet to force reload
   * @param {string} qos
   * @param {object} returnObj Bounced back with response or exception
   * @param {string|?} additionalParams Append to URL, e.g."admin=true";
   * @param {function} responseFp
   * @param {object} responseThisArg
   * @returns {void}
   * @throws {XmlBlasterException} From server, e.g. "user.security.authentication.accessDenied"
   */
  connectRaw(forceLoad, qos, returnObj, additionalParams, responseFp, responseThisArg) {
    if (responseFp === undefined || responseFp == null) {
      throw new XmlBlasterException("internal.illegalArgument", "XmlBlasterAccess.js#connectRaw", "Missing responseFp", false);
    }
    this.isIntentionalSocketClose = false;

    const that = this;

    log.info("XmlBlasterAccess.js-connect(): " + qos);

    this.webSocket = new WebSocket(this.urlBase);
    this.webSocket.binaryType = "arraybuffer";

    const msgUnit = new MsgUnitRaw(qos, "", "");
    const msg = new MsgInfo("I", "connect", "", msgUnit);

    this.webSocket.addEventListener("open", (event) => {
      this.sendMessageForResponse(msg, (response) => {
        const msgUnit = response.getMsgUnits()[0];
        if (response.isException()) {
          responseFp.call(responseThisArg, msgUnit.getQosData(), returnObj);
          return;
        }

        this.isConnected = true;
        log.info("XbAcces.js-connect() returned");//: " + msgUnit.getQosData().toXml());
        this.connectReturnQosData = msgUnit.getQosData();
        responseFp.call(responseThisArg, this.connectReturnQosData, returnObj);
      });
    });

    this.webSocket.addEventListener("message", (event) => {
      if (!(event.data instanceof ArrayBuffer)) {
        throw new XmlBlasterException("Unexpected type " + typeof (event.data) + " from websocket. Expected ArrayBuffer");
      }
      this.onWebSocketMessage(event.data);
    });

    this.webSocket.addEventListener("close", (event) => {
      if (this.closeCb)
        this.closeCb(this.isIntentionalSocketClose, event.data);
    });
  }

  /**
   * Blocks until successfull connect.
   * Logs in with negative session Id.
   * @param {boolean} forceLoad if true tells servlet to force reload
   * @param {string} securityLoginName The authentication credentials, eg "labk-w01:admin" for sudo access
   * @param {string} passwd The authentication credentials
   * @param {string} securityPlugin_ defaults to "htpasswd"
   * @param {number} maxSessions
   * @param {object} returnObj_ Bounced back with response or exception
   * @param {string | ?} additionalParams Append to URL, e.g."admin=true";
   * @return {void}
   * @throws {XmlBlasterException} From server, e.g. "user.security.authentication.accessDenied"
   */
  connect(forceLoad, securityLoginName, passwd, securityPlugin_, maxSessions, returnObj_, additionalParams, responseFp, responseThisArg) {
    var securityPlugin = (securityPlugin_ == undefined || securityPlugin_ == null) ? defaultXmlBlasterSecurityPlugin : securityPlugin_;
    var maxSessions = maxSessions || 10;
    // We HAVE TO SET TO zero, as tomcat (and not xmlBlaster) determines the lifetime of the session
    // Receiving only updates would not refresh the xmlBlaster timeout
    // Note: The servlet timeout is set in web.xml to 30 minutes
    var timeout = 0; // 0 is unlimited; 60000 one minute; 3600000 is one hour
    // Note that retries is set to one, if tomcat disappears xmlBlaster kills the session immediately
    var qos = "";
    if (orgXmlBlasterUtil.isFilled(securityLoginName)) {
      if (passwd && (passwd.indexOf("&") != -1 || passwd.indexOf("<") != -1)) {
        // This will fail -> XmlScriptParser &amp; is OK -> ConnectQosSaxFactory ok -> SecurityQosParserPlugin fails as <CDATA has orignial &
        // Workaround:
        passwd = "__base64:" + Base64.encode(passwd); // The server side security plugin must decode this!!!!
      }
      qos =
        "<qos>" +
        " <securityService type='" + securityPlugin + "' version='1.0'>" +
        "   <user>" + orgXmlBlasterUtil.escapeXml(securityLoginName) + "</user>" +
        "   <passwd>" + orgXmlBlasterUtil.escapeXml(passwd) + "</passwd>" +
        " </securityService>" +
        " <session name='" + orgXmlBlasterUtil.escapeXmlAttr(this.getLoginName()) + "' timeout='" + timeout + "' maxSessions='" + maxSessions + "'/>" +
        " <queue relating='connection'>" +
        "   <address type='SOCKET' retries='0'/>" +
        " </queue>" +
        " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000' maxBytes='500000000'>" +
        "   <callback type='SOCKET' sessionId='browser' " +
        "      pingInterval='30000' retries='1' delay='60000' dispatcherActive='true'>" +
        "      <burstMode collectTime='30' maxEntries='100' maxBytes='1000000' />" +
        "   </callback>" +
        " </queue>" +
        " <clientProperty name='locale'>" + orgXmlBlasterUtil.getLocaleIdJava() + "</clientProperty>" +
        " <clientProperty name='timezoneOffsetMinutes'>" + orgXmlBlasterUtil.getTimezoneOffsetMinutes() + "</clientProperty>" +
        " <clientProperty name='osEnv'>web</clientProperty>" + // see Server checkPassword()
        "</qos>";
    }
    else {
      qos =
        "<qos>" +
        " <session name='" + this.getLoginName() + "' timeout='" + timeout + "' maxSessions='" + maxSessions + "'/>" +
        " <clientProperty name='locale'>" + orgXmlBlasterUtil.getLocaleIdJava() + "</clientProperty>" +
        " <clientProperty name='timezoneOffsetMinutes'>" + orgXmlBlasterUtil.getTimezoneOffsetMinutes() + "</clientProperty>" +
        " <clientProperty name='osEnv'>web</clientProperty>" +
        "</qos>";
    }
    this.connectRaw(forceLoad, qos, returnObj_, additionalParams, responseFp, responseThisArg);
  }



  /**
   * Blocks until successfull disconnect.
   * Logs in with negative session Id.
   * returns {ConnectQosData}
   */
  disconnect() {
    this.isIntentionalSocketClose = true;
    const msgInfo = new MsgInfo("I", "disconnect", this.getSessionId());
    this.sendMessageForResponse(msgInfo, (resp) => {
      this.shutdown();

      log.info("Returned: " + resp.getQosStr(), { file: "XmlBlasterAccess", method: "disconnect" });
      if (resp.isException()) {
        const msgUnit = resp.getMsgUnits()[0];
        log.error("XmlBlasterAccess.js-Disconnect returned exception " + msgUnit.getXmlBlasterException()?.toString(), { file: "XmlBlasterAccess", method: "disconnect", exception: msgUnit.getXmlBlasterException() });
      }
    });
  }

  shutdown() {
    this.isIntentionalSocketClose = true;
    this.isConnected = false;
    if (this.webSocket != null)
      this.webSocket.close();
    this.webSocket = null;
  }

  /**
   * Supports key_="exact:hello" or "xpath://key"
   * Asynchronous call
   * @param {string} key_ The xmlBlaster key XML markup, e.g. "<key oid='hello'/>"
   * @param {string} qos_ The xmlBlaster QoS XML markup, e.g. "<qos/>"
   * @param {object|null} responseFp_ -> if given we call responseFp_(dataReceived, myXmlHttp, returnObj)
   * @param {object|null} responseThisArg -> the 'this' pointer (scope) used when callback is called
   * @param {object|null} returnObj_ Bounced back with response or exception
   * @return {MsgUnit[]|false} ArrayList containing {@link MsgUnit} instances
   * @deprecated sync unsupported since chrome 118 (2023)
   */
  get(key_, qos_, responseFp_, responseThisArg, returnObj_) {
    var key = key_ || null;
    var qos = (qos_ == undefined || qos_ == null) ? "<qos></qos>" : qos_;
    if (key == null) return false;
    var keyXml = "";
    if (key.indexOf("exact:") == 0) {
      keyXml = "  <key oid='" + key.substr("exact:".length) + "'/>";
    }
    else if (key.indexOf("xpath:") == 0) {
      keyXml = "  <key oid='' queryType='XPATH'>" + key.substr("xpath:".length) + "</key>";
    }
    else if (key.indexOf("<") == 0) {
      keyXml = key;
    }
    else {
      keyXml = "<key oid='" + key + "'/>";
    }

    const msgInfo = new MsgInfo("I", "get", this.getSessionId(), new MsgUnitRaw(qos, keyXml));
    this.sendMessageForResponse(msgInfo, (resp) => {
      const msgUnit = resp.getMsgUnits()[0];
      responseFp_.call(responseThisArg, msgUnit, returnObj_);
    });
  }

  /**
   * Supports key_="exact:hello" or "xpath://key"
   * Asynchronous call
   * @param {string|KeyData} key_ The xmlBlaster key XML markup, e.g. "<key oid='hello'/>"
   * @param {string|QosData} qos_ The xmlBlaster QoS XML markup, e.g. "<qos/>"
   * @param {object|null} responseFp_ -> if given we call responseFp_(dataReceived, myXmlHttp, returnObj)
   * @param {object|null} responseThisArg -> the 'this' pointer (scope) used when callback is called
   * @param {object|null} returnObj_ Bounced back with response or exception
   */
  subscribe(key_ = null, qos_ = null, responseFp_, responseThisArg, returnObj_) {
    if (key_ == null || (typeof (key_) == "string" && key_.length == 0)) {
      return false;
    }
    var qosXml;
    if (qos_ == null) {
      qosXml = "<qos><multiSubscribe>false</multiSubscribe><updateOneway>true</updateOneway><local>false</local></qos>";
    }
    else if (qos_.constructor == QosData) {
      qosXml = qos_.toXml();
    }
    else {
      qosXml = qos_;
    }

    var keyXml = "";
    if (key_.constructor == KeyData) {
      keyXml = key_.toXml();
    } else if (typeof key_ === 'string' || key_ instanceof String) {
      if (key_.indexOf("exact:") == 0) {
        keyXml = "  <key oid='" + key_.substr("exact:".length) + "'/>";
      }
      else if (key_.indexOf("xpath:") == 0) {
        keyXml = "  <key oid='' queryType='XPATH'>" + key_.substr("xpath:".length) + "</key>";
      }
      else if (key_.indexOf("<") == 0) {
        keyXml = /** @type {string} */(key_);
      }
      else {
        keyXml = "<key oid='" + key_ + "'/>";
      }
    }

    const msgInfo = new MsgInfo("I", "subscribe", this.getSessionId(), new MsgUnitRaw(qosXml, keyXml));
    this.sendMessageForResponse(msgInfo, (resp) => {
      const msgUnit = resp.getMsgUnits()[0];
      responseFp_.call(responseThisArg, msgUnit, returnObj_);
    });
  }

  /**
   * Supports key_="exact:hello" or "xpath://key"
   * Asynchronous call
   * @param {string} key The xmlBlaster key XML markup, e.g. "<key oid='hello'/>"
   * @param {string} qos The xmlBlaster QoS XML markup, e.g. "<qos/>"
   * @param {object} responseFp_ -> if given we call responseFp_(dataReceived, myXmlHttp, returnObj)
   * @param {object} returnObj_ Bounced back with response or exception
   * @param {any} responseThisArg
   */
  unSubscribe(key, qos, responseFp_, responseThisArg, returnObj_) {
    if (!orgXmlBlasterUtil.isDefined(key))
      throw "IllegalArgument: Can't unSubscribe without key";
    var qos = (orgXmlBlasterUtil.isDefined(qos)) ? qos : "<qos/>";
    var keyXml = "";
    if (key.indexOf("exact:") == 0) {
      keyXml = "  <key oid='" + key.substr("exact:".length) + "'/>";
    }
    else if (key.indexOf("xpath:") == 0) {
      keyXml = "  <key oid='' queryType='XPATH'>" + key.substr("xpath:".length) + "</key>";
    }
    else if (key.indexOf("<") == 0) {
      keyXml = key;
    }
    else {
      keyXml = "<key oid='" + key + "'/>";
    }

    const msgInfo = new MsgInfo("I", "unSubscribe", this.getSessionId(), new MsgUnitRaw(qos, keyXml));

    this.sendMessageForResponse(msgInfo, (resp) => {
      const msgUnit = resp.getMsgUnits()[0];
      responseFp_.call(responseThisArg, msgUnit, returnObj_);
    });
  }

  /**   
   * Send a messag to xmlBlaster.  
   * Ther response will arrive asynchronously via the ajax poller. 
   * @param {MsgUnit} msgUnit
   * @param {function | null} responseFp_ An optional callback function
   *        for the servlet response - publishReturnQos (Not the service response!)
   * @param {object | null} responseThisArg An optional callback function this pointer
   * @param {object | null} returnObj_
   * @return {void}
   */
  publish(msgUnit, responseFp_, responseThisArg, returnObj_) {
    const msgInfo = new MsgInfo("I", "publish", this.getSessionId(), msgUnit.toMsgUnitRaw());
    this.sendMessageForResponse(msgInfo, (resp) => {
      const msgUnit = resp.getMsgUnits()[0];
      responseFp_.call(responseThisArg, msgUnit, returnObj_);
    });
  }

  /**
     * @param {string} info
     */
  setStartTimestamp(info) {
    this.startTimestamp = (new Date).valueOf();
    this.startTimestampInfo = info || "";
  }

  resetStartTimestamp() {
    this.startTimestamp = null;
    this.startTimestampInfo = "";
  }

  /**
     * @param {string} info
     * @param {boolean} [reset]
     */
  logTimeElapsed(info, reset) {
    if (!orgXmlBlasterUtil.isDefined(this.startTimestamp))
      return;
    var reset = reset || false;
    var info = info || "";
    var elapsed = (+new Date - this.startTimestamp);
    log.info(elapsed + "ms " + this.startTimestampInfo + "-" + info);
    if (reset)
      this.resetStartTimestamp();
  }

} // end class XmlBlasterAccess

