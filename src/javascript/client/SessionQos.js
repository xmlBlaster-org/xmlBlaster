import { SessionName } from "../util/SessionName.js";


/**
 * Holds sessionQos informations inside a ConnectQos
 */
export class SessionQos {
  constructor() {
    this.sessionName = null;
    this.sessionTimeout = 1000 * 60 * 60 * 24; //DAY_IN_MILLIS
    this.maxSessions = 10;
    this.clearSessions = false;
    this.reconnectSameClientOnly = false;
    this.sessionId = ""; // secret sessionId
  }

  /**
   * @return {SessionName} The login name and session number
   */
  getSessionName() { return this.sessionName; }
  /**
   * @return {number} The number of milli seconds the session lives without interaction
   */
  getSessionTimeout() { return this.sessionTimeout; }
  /**
   * @return {number} Number of accepted login sessions of this user
   */
  getMaxSessions() { return this.maxSessions; }
  /**
   * @return {boolean} If true other login sessions with same name are destroyed
   */
  getClearSessions() { return this.clearSessions; }
  /**
   * @return {boolean} Singleton hint
   */
  getReconnectSameClientOnly() { return this.reconnectSameClientOnly; }
  /**
   * @return {string} The secret session id
   */
  getSessionId() { return this.sessionId; }

  dump() {
    var str = "<session";
    if (this.sessionName != null) str += " name='" + this.sessionName + "'";
    if (this.sessionTimeout != null) str += " timeout='" + this.sessionTimeout + "'";
    if (this.maxSessions != null) str += " maxSessions='" + this.maxSessions + "'";
    if (this.clearSessions != null) str += " clearSessions='" + this.clearSessions + "'";
    if (this.sessionId != null) str += " sessionId='" + this.sessionId + "'";
    str += "/>";
    return str;
  }
}