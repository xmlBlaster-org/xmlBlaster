import { AccessFilterQos } from "./AccessFilterQos.js";
import { KeyData } from "./KeyData.js";
import { MapObject } from "../util/MapObject.js";
import { SessionName } from "../util/SessionName.js";
import { ClientProperty } from "../util/ClientProperty.js";
import { log } from "../util/Logger.js";

const ME1 = "QosData.js";

/**
 * Holds a QoS instance of a xmlBlaster message
 * All mixed in: received UpdateQos, SubscribeQos etc
 * @constructor
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
 * @see http://xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 */
export class QosData {
  constructor() {
    this.stateId = null; //"OK";
    this.stateInfo = "";
    this.sender = "";
    this.priority = 5;
    this.subscriptionId = "";
    this.rcvTimestampNanos = "";
    this.lifeTime = -1; // Number.MAX_VALUE; // millis
    this.remainingLife = -1; // Number.MAX_VALUE; // millis
    this.persistent = null;
    this.multiSubscribe = null; // null to not send, CAUTION: xmlBlaster default is true;
    this.wantInitialUpdate = null;
    this.wantUpdateOneway = null;
    this.wantLocal = null;
    this.wantContent = null;
    this.wantNotify = null;
    this.wantMeta = null;
    this.wantUpdateOneway = null;
    this.forceDestroy = null;
    this.queueIndex = 0;
    this.queueSize = 1;
    this.redeliver = 0;
    this.eraseKeyOid = "";
    this.destinationQueryType = null; // Should be in a class similar Destination.java
    this.destinationStr = null; // TO-DO: Should be a class similar Destination.java
    this.destinationForceQueuing = false; // Should be in a class similar Destination.java
    /**
     * value=org.xmlBlaster.util.ClientProperty instance
     */
    this.clientProperties = new MapObject();
    /** @type AccessFilterQos[] */
    this.accessFilterArr = []; // <filter type='GnuRegexFilter' version='1.0'>^H.*$</filter>
    // TODO QuerySpecQos.java <querySpec type='QueueQuery' version='1.0'><![CDATA[maxEntries=3;maxSize=-1;consumable=false;waitingDelay=0]]></querySpec>
    // TODO HistoryQos.java <history numEntries='20' newestFirst='true'/>
  }

  /** @param {AccessFilterQos} accessFilterQos */
  addAccessFilter(accessFilterQos) {
    if (accessFilterQos)
      this.accessFilterArr.push(accessFilterQos);
  }
  getAccessFilterArr() {
    return this.accessFilterArr;
  }

  hasStateId() { return this.stateId != null && this.stateId.length > 0; }
  getStateId() { return this.stateId == null ? "OK" : this.stateId; }
  setStateId(stateId) { this.stateId = stateId; }
  getStateInfo() { return this.stateInfo == null ? "" : this.stateInfo; }
  setStateInfo(stateInfo) { this.stateInfo = stateInfo; }
  /**
   * Access the sender as a {@link SessionName} instance
   * @return {SessionName} A SessionName instance, is never null
   */
  getSender() { return new SessionName(this.sender); }
  setSenderStr(senderStr) { this.sender = senderStr; }
  hasSender() { return this.sender != null && this.sender.length > 0; }

  /**
   * Access the sender as a {@link SessionName} instance
   * @return {SessionName} A SessionName instance, can be null
   */
  getSessionNameChanger() {
    const senderStr = this.getClientProperty("changerSessionName", null);
    if (senderStr) {
      return new SessionName(senderStr); // the real changer '/node/dev/client/demo/session/-90'
    }
    if (this.hasSender()) {
      return this.getSender();
    }
    return null;
  }

  /**
   * PtP or Pub/Sub mode?
   * @return {boolean} true if it is a PtP messages
   */
  isPtp() { return (org.xmlBlaster.util.isDefined(this.destinationStr)) };
  /**
   * Access the destination as a {@link SessionName} instance
   * @return {SessionName} A SessionName instance or null
   */
  hasDestination() { return org.xmlBlaster.util.isDefined(this.destinationStr); }
  getDestination() { return (org.xmlBlaster.util.isDefined(this.destinationStr)) ? new SessionName(this.destinationStr) : null; }
  /**
   * Forcing a PtP publish (or a Publish/Sub message if destination is null). 
   * @param {string} destination "client/joe/session/1" or "/node/heron/client/joe/session/1"
   */
  setDestinationStr(destination) {
    this.destinationStr = destination;
  }
  getDestinationStr() {
    return this.destinationStr == null ? "" : this.destinationStr;
  }
  /**
   * Forcing a PtP publish (or a Publish/Sub message if sessionName is null). 
   * @param {SessionName} sessionName A SessionName instance or null
   */
  setDestination(sessionName) {
    if (org.xmlBlaster.util.isDefined(sessionName))
      this.destinationStr = sessionName.getAbsoluteName();
    else
      this.destinationStr = null;
  }
  /**
   * 0 Has highest priority, 5 is default
   * @return {number} 0...9
   */
  getPriority() { return this.priority; }
  setPriority(priority) { this.priority = priority; }
  hasSubscriptionId() { return this.subscriptionId != null && this.subscriptionId.length > 0; }
  getSubscriptionId() { return this.subscriptionId; }
  setSubscriptionId(subscriptionId) { this.subscriptionId = subscriptionId; }
  /** @return {string} */
  getRcvTimestampNanos() { return this.rcvTimestampNanos; }
  getLifeTime() { return this.lifeTime; }
  setLifeTime(lifeTimeMillis) { this.lifeTime = lifeTimeMillis; }
  getRemainingLife() { return this.remainingLife; }
  isPersistent() { return this.persistent == null ? false : this.persistent; }
  setPersistent(persistent) { this.persistent = persistent; }
  isMultiSubscribe() { return this.multiSubscribe == null ? true : this.multiSubscribe; }
  setMultiSubscribe(multiSubscribe) { this.multiSubscribe = multiSubscribe; }
  isWantInitialUpdate() { return this.wantInitialUpdate == null ? true : this.wantInitialUpdate; }
  setWantInitialUpdate(wantInitialUpdate) { this.wantInitialUpdate = wantInitialUpdate; }
  isWantLocal() { return this.wantLocal == null ? true : this.wantLocal; }
  setWantLocal(local) { this.wantLocal = local; }
  isWantContent() { return this.wantContent == null ? true : this.wantContent; }
  setWantContent(content) { this.wantContent = content; }
  isWantNotify() { return this.wantNotify == null ? true : this.wantNotify; }
  setWantNotify(notify) { this.wantNotify = notify; }
  isWantMeta() { return this.wantMeta == null ? true : this.wantMeta; }
  setWantMeta(meta) { this.wantMeta = meta; }
  isWantUpdateOneway() { return this.wantUpdateOneway == null ? true : this.wantUpdateOneway; }
  setWantUpdateOneway(oneway) { this.wantUpdateOneway = oneway; }
  isNewestOnly() { return this.getClientProperty("__newestOnly", false); }
  setNewestOnly(newestOnly) { this.addClientProperty("__newestOnly", newestOnly); }
  isForceDestroy() { return this.forceDestroy == null ? false : this.forceDestroy; }
  setForceDestroy(forceDestroy) { this.forceDestroy = forceDestroy; }
  getQueueIndex() { return this.queueIndex; }
  getQueueSize() { return this.queueSize; }
  getRedeliver() { return this.redeliver; }
  getEraseKeyOid() { return this.eraseKeyOid; }
  /**
   * Are client properties attached?
   * @return {boolean} true if client properties are available
   */
  hasClientProperty(key) {
    var cp = this.clientProperties.get(key);
    if (!org.xmlBlaster.util.isDefined(cp)) return false;
    return true;
  }
  /**
   * @param {ClientProperty} clientProperty
   */
  setClientProperty(clientProperty) {
    if (!org.xmlBlaster.util.isDefined(clientProperty)) return null;
    return this.clientProperties.put(clientProperty.getName(), clientProperty);
  }
  addClientProperty(key, value) {
    if (!org.xmlBlaster.util.isDefined(key)) return null;
    var clientProperty = new ClientProperty(key, value)
    return this.clientProperties.put(clientProperty.getName(), clientProperty);
  }
  /**
   * @return {MapObject} never null
   */
  getClientProperties() {
    return this.clientProperties;
  }
  /**
    * If defaultValue is not given the ClientProperty instance is returned or null if not found
    * If defaultValue is given: If key is found the value is returned (typically a String instance) else the given defaultValue
    * @param {string} key The key to lookup
    * @param {object} defaultValue The default to use if not found
    * @return {object} the value of the {@link org.xmlBlaster.util.ClientProperty} object, never 'undefined' but null if not found and defaultValue was undefined
    */
  getClientProperty(key, defaultValue) {
    if (!org.xmlBlaster.util.isDefined(defaultValue))
      defaultValue = null;
    var cp = this.clientProperties.get(key);
    if (!org.xmlBlaster.util.isDefined(cp)) return defaultValue;
    return cp.getValue();
  }

  /**
   * A client side subscriptionId must start with "__subId:" followed by the relative session name.
   * <p>This us only useful for positive session Ids in fail save environments: if the
   * subscription is queued the faked subscriptionId will be used later by the server</p>
   * @param {SessionName} sessionName
   * @param {KeyData} subscribeKey
   * @return {string} e.g. "__subId:client/joe/session/1-XPATH://key" for pubSessionId>0 and multiSubscribe=false
   * or e.g. "__subId:client/joe-135692304540000" in other cases
   */
  generateSubscriptionId(sessionName, subscribeKey, isMultiSubscribe) {
    if (!sessionName) {
      var da = new Date();
      this.subscriptionId = org.xmlBlaster.SUBSCRIPTIONID_PREFIX + "UnknownUser-" + da.getMilliseconds();
      return this.subscriptionId;
    }
    if (sessionName.isPubSessionIdUser() || !isMultiSubscribe) {
      // This key is assured to be the same on client restart
      // a previous subscription in the server will have the same subscriptionId
      // Benefit: If on client restart we are queueing the returned faked subscriptionId will
      // match the later used one of the xmlBlaster server. We can easily use the subscriptionId
      // as a key in client code hashtable to dispatch update() messages
      // Note: multiSubscribe==false allows max one subscription on a topic, even it has
      // different mime query plugins (the latest wins)
      var url = subscribeKey.getUrl();
      // url = ReplaceVariable.replaceAll(url, "'", "&apos;"); // to have valid xml (<subscribe id='bla'/>
      this.subscriptionId = org.xmlBlaster.SUBSCRIPTIONID_PREFIX + sessionName.getRelativeName() + "-" + url;
    }
    else {
      var da = new Date();
      this.subscriptionId = org.xmlBlaster.SUBSCRIPTIONID_PREFIX + sessionName.getRelativeName() + "-" + da.getMilliseconds();
    }
    return this.subscriptionId;
  }


  toXml() {
    var str = "<qos>";
    if (this.hasStateId())
      str += "<state id='" + this.stateId + "'" + " info='" + org.xmlBlaster.util.escapeXmlAttr(this.stateInfo) + "'/>";
    if (org.xmlBlaster.util.isFilled(this.sender))
      str += "<sender>" + org.xmlBlaster.util.escapeXml(this.sender) + "</sender>";
    if (this.priority != 5)
      str += "<priority>" + this.priority + "</priority>";
    if (org.xmlBlaster.util.isFilled(this.subscriptionId))
      str += "<subscribe id='" + org.xmlBlaster.util.escapeXmlAttr(this.subscriptionId) + "'/>";
    if (this.persistent != null)
      str += "<persistent>" + this.persistent + "</persistent>";
    if (this.multiSubscribe != null)
      str += "<multiSubscribe>" + this.multiSubscribe + "</multiSubscribe>";
    if (this.wantInitialUpdate != null)
      str += "<initialUpdate>" + this.wantInitialUpdate + "</initialUpdate>";
    if (this.wantLocal != null)
      str += "<local>" + this.wantLocal + "</local>";
    if (this.wantContent != null)
      str += "<content>" + this.wantContent + "</content>";
    if (this.wantNotify != null)
      str += "<notify>" + this.wantNotify + "</notify>"; // correct???
    if (this.wantMeta != null)
      str += "<meta>" + this.wantMeta + "</meta>";
    if (this.wantUpdateOneway != null)
      str += "<updateOneway>" + this.wantUpdateOneway + "</updateOneway>";
    if (this.forceDestroy != null)
      str += "<forceDestroy>" + this.forceDestroy + "</forceDestroy>";
    if (org.xmlBlaster.util.isFilled(this.rcvTimestampNanos))
      str += "<rcvTimestamp nanos='" + this.rcvTimestampNanos + "'/>";
    if (this.lifeTime != -1)
      str += "<expiration lifeTime='" + this.lifeTime + "' remainingLife='" + this.remainingLife + "'/> ";
    if (org.xmlBlaster.util.isFilled(this.destinationStr))
      str += "<destination forceQueuing='" + this.destinationForceQueuing + "'>" + org.xmlBlaster.util.escapeXml(this.destinationStr) + "</destination>";
    if (this.queueIndex > 0)
      str += "<queue index='" + this.queueIndex + "' size='" + this.queueSize + "'/>";
    //  "<redeliver>4</redeliver>";

    // TODO: AccessFilterQos.java
    // &lt;filter type='myPlugin' version='1.0'>a!=100&lt;/filter>

    // TODO: QuerySpecQos.java
    // <querySpec type='QueueQuery' version='1.0'>800</querySpec>

    this.clientProperties.moveFirst();
    while (this.clientProperties.next()) {
      //str += this.clientProperties.getKey() + " = " + this.clientProperties.getValue());
      //"<clientProperty name='myTransactionId'>TODO</clientProperty>" +
      str += this.clientProperties.getValue().dump();
    }
    for (var ii = 0; ii < this.accessFilterArr.length; ii++) {
      /** @type {AccessFilterQos} */
      if (this.accessFilterArr[ii]) {
        str += "\n";
        str += this.accessFilterArr[ii].toXml();
      }
    }
    /*
    "<route> <!-- Routing information in cluster environment -->" +
       "<node id='avalon' stratum='1' timestamp='1068026303739000001' dirtyRead='false'/>" +
       "<node id='heron' stratum='0' timestamp='1068026303773000001' dirtyRead='false'/>" +
    "</route>" +
    */
    str += "</qos>";
    return str;
  }

  dump() {
    return this.toXml();
  }

  /**
   * @param {string} xmlStr
   */
  static parseString(xmlStr) {
    const document = org.xmlBlaster.util.getDOMDocument(xmlStr);
    return QosData.parse(document, document.documentElement);
  }

  /**
   * Parse xml to QosData (xmlBlaster message Qos).
   * @param {Document} domDocument The complete XML W3C DOM tree:
   * interface Document : Node
   * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
   * @param {Node} currentNode The W3C Node
   * @return {QosData} qosData
   * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
   * @see http://xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
   */
  static parse(domDocument, currentNode) {
    var qosData = new QosData();
    for (var i = 0; i < currentNode.childNodes.length; i++) {
      /** @type {Node|Element} */
      const node1 = currentNode.childNodes[i];
      if (node1.nodeType != Node.ELEMENT_NODE) {
        const node = node1;
        let val = org.xmlBlaster.util.isDefined(node.nodeValue) ? node.nodeValue : "";
        var tmp = org.xmlBlaster.util.trim(val);
        if (tmp.length == 0) continue;
        val = ": '" + val + "'";
        var txt = "QosData(): Ignoring xmlBlaster response node " + org.xmlBlaster.util.getXPathLocation(node, true) + val;
        (node.nodeType == Node.COMMENT_NODE || node.nodeType == Node.TEXT_NODE) ? log.debug(ME1 + txt) : log.warn(ME1 + txt);
        continue;
      }
      const node = /** @type {Element} */ (currentNode.childNodes[i]);
      switch (node.nodeName) {
        case "state":
          qosData.stateId = node.getAttribute('id');
          if (qosData.stateId == null) qosData.stateId = "";
          qosData.stateInfo = node.getAttribute('info');
          if (qosData.stateInfo == null) qosData.stateInfo = "";
          break;
        case "sender":
          qosData.sender = org.xmlBlaster.util.collectTextFromNodeChilds(node);
          //qosData.sender = (node.firstChild) ? node.firstChild.nodeValue : "";
          break;
        case "priority":
          {
            const val = org.xmlBlaster.util.collectTextFromNodeChilds(node);
            qosData.priority = org.xmlBlaster.util.toNumber(val, qosData.priority);
          }
          break;
        case "subscribe": // <subscribe id='__subId:client/subscriber/session/1-exact:MyHelloTopic'/>
          qosData.subscriptionId = node.getAttribute('id');
          break;
        case "rcvTimestamp":
          qosData.rcvTimestampNanos = node.getAttribute('nanos');
          break;
        case "expiration":
          qosData.lifeTime = new Number(node.getAttribute('lifeTime')).valueOf();
          qosData.remainingLife = new Number(node.getAttribute('remainingLife')).valueOf();
          break;
        case "persistent":
          qosData.persistent = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.persistent);
          break;
        case "multiSubscribe":
          qosData.multiSubscribe = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.multiSubscribe);
          break;
        case "initialUpdate":
          qosData.wantInitialUpdate = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.wantInitialUpdate);
          break;
        case "local":
          qosData.wantLocal = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.wantLocal);
          break;
        case "content":
          qosData.wantContent = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.wantContent);
          break;
        case "meta":
          qosData.wantMeta = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.wantMeta);
          break;
        case "updateOneway":
          qosData.wantUpdateOneway = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.wantUpdateOneway);
          break;
        case "forceDestroy":
          qosData.forceDestroy = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.forceDestroy);
          break;
        case "notify":
          qosData.wantNotify = org.xmlBlaster.util.collectBooleanFromNodeChilds(node, qosData.wantNotify);
          break;
        case "queue":
          qosData.queueIndex = new Number(node.getAttribute('index')).valueOf();
          qosData.queueSize = new Number(node.getAttribute('size')).valueOf();
          break;
        case "redeliver":
          {
            const val = org.xmlBlaster.util.collectTextFromNodeChilds(node);
            qosData.redeliver = org.xmlBlaster.util.toNumber(val, qosData.redeliver);
          }
          break;
        case "route":
          log.debug(ME1 + "QoS <route> tag parsing is not implemented");
          break;
        case "subscribable":
          // <key oid="device.lrazak-as-w01.status" contentMime="application/service" contentMimeExtended="1.0"/>
          // content: base64
          // "<qos>\n  <subscribable>false</subscribable>\n
          //  <destination>/node/dev/client/admin/-7</destination>\n
          //  <sender>/node/dev/client/as-w01/30</sender>\n
          //  <subscribe id=\"__subId:PtP\"/>\n  <expiration lifeTime=\"20000\"
          //       remainingLife=\"19998\" forceDestroy=\"true\"/>\n  <rcvTimestamp nanos=\"1738237201911000000\"/>\n
          //  <queue index=\"0\" size=\"1\"/>\n
          //  <isUpdate/>\n </qos>"
          log.warn(ME1 + "QoS <subscribable> tag parsing is not implemented");
          break;
        case "forceUpdate":
          log.warn(ME1 + "QoS <forceUpdate> tag parsing is not implemented");
          break;
        case "destination":
          qosData.destinationQueryType = node.getAttribute('queryType');
          qosData.destinationForceQueuing = org.xmlBlaster.util.parseBoolean(node.getAttribute('forceQueuing'));
          qosData.destinationStr = org.xmlBlaster.util.collectTextFromNodeChilds(node); //node.firstChild.nodeValue;
          break;
        case "clientProperty":
          var cp = new ClientProperty(node.getAttribute('name'),
            org.xmlBlaster.util.collectTextFromNodeChilds(node),
            node.getAttribute('encoding'),
            node.getAttribute('type'),
            node.getAttribute('charset'));
          qosData.clientProperties.put(cp.getName(), cp);
          break;
        case "filter":
          // <filter type='GnuRegexFilter' version='1.0'>^H.*$</filter>
          var type = (node.getAttribute('type') != null) ? node.getAttribute('type') : "";
          var version = (node.getAttribute('version') != null) ? node.getAttribute('version') : "1.0";
          var query = org.xmlBlaster.util.collectTextFromNodeChilds(node);
          var filter = new AccessFilterQos(type, version, query);
          qosData.addAccessFilter(filter);
          break;
        case "key": // erase markup contains a <key oid='__sys__Login'/>"
          var keyData = KeyData.parse(domDocument, node);
          qosData.eraseKeyOid = (keyData == null) ? "" : keyData.getOid();
          break;
        case "isSubscribe":
        case "isPublish":
        case "isUpdate":
        case "isUnSubscribe":
        case "isErase":
        case "isConnect":
          log.debug(ME1 + "QosData(): Ignoring xmlBlaster response tag " + org.xmlBlaster.util.getXPathLocation(node, true));
          break;
        default:
          log.warn(ME1 + "QosData(): Ignoring xmlBlaster response tag " + org.xmlBlaster.util.getXPathLocation(node, true));
          break;
      }
    }
    return qosData;
  }

}