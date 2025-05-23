import { toNumber } from "../util.js";

/**
 * Parse the sessionName.
 */
export class SessionName {
  /**
   * 
   * @param {string=} name e.g. "/node/heron/client/joe/session/1" OR name == null
   * @param {string=} subjectId_ e.g. "joe"
   * @param {number=} sessionId_ e.g. 1
   * @param {string=} nodeId_ e.g. null
   */
  constructor(name, subjectId_, sessionId_, nodeId_) {
    if (name == null && org.xmlBlaster.util.isDefined(subjectId_)) {
      this.origName = subjectId_;
      this.nodeId = nodeId_ || "undefined"; // not the JS undefined but the word "undefined"
      this.subjectId = subjectId_;
      /** @type {number} */
      this.pubSessionId = toNumber(sessionId_, 1);
    }
    else {
      this.origName = name || "";
      var index = name.indexOf("/");
      if (index == -1) {
        this.nodeId = "undefined";
        if (name.length < 1)
          this.subjectId = "undefined";
        else
          this.subjectId = name;
        this.pubSessionId = 0;
      }
      else if (index == 0) { //"/node/heron/client/joe/session/1"
        var arr = name.substring(1).split("/");  // trim leading "/"
        this.nodeId = arr[1];
        this.subjectId = arr[3];
        if (arr.length > 5)
          this.pubSessionId = new Number(arr[5]).valueOf();
        else
          this.pubSessionId = new Number(arr[4]).valueOf(); // support old style "/node/heron/client/joe/1"
      }
      else {  //"client/joe/session/1"
        var arr = name.split("/");  // trim leading "/"
        this.nodeId = "undefined";
        this.subjectId = arr[1];
        if (arr.length > 2)
          this.pubSessionId = new Number(arr[3]).valueOf();
        else
          this.pubSessionId = new Number(arr[1]).valueOf(); // support old style "client/joe/1"
      }
    }
  }
  getAbsoluteName() { return "/node/" + this.nodeId + "/" + this.getRelativeName(); }
  getNodeId() { return this.nodeId; }
  getSubjectId() { return this.subjectId; }
  getPubSessionId() { return this.pubSessionId; }
  getRelativeName() { return "client/" + this.subjectId + "/session/" + this.pubSessionId; }
  /**
   * @param {SessionName} sessionNameOther
   * @return {boolean}
   */
  isSame(sessionNameOther) {
    if (!sessionNameOther) {
      return false;
    }
    return this.getSubjectId() == sessionNameOther.getSubjectId() && this.getPubSessionId() == sessionNameOther.getPubSessionId();
  }
  toString() { return this.getAbsoluteName(); }

  /** @return {boolean} true it publicSessionId is given by xmlBlaster server (if < 0) */
  isPubSessionIdInternal() {
    return this.pubSessionId < 0;
  }

  /** @return true it publicSessionId is given by user/client (if > 0) */
  isPubSessionIdUser() {
    return this.pubSessionId > 0;
  }

}