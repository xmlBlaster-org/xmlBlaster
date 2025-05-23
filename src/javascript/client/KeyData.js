
/**
 * Holds key of a xmlBlaster message.
 * @see {@link http://www.xmlBlaster.org} <a href="http://www.xmlBlaster.org" target="others">xmlBlaster.org</a>
 */
export class KeyData {

  /**
   * @param {string=} oid
   * @param {string=} contentMime
   * @param {string=} contentMimeExtended
   * @param {string=} domain
   */
  constructor(oid, contentMime, contentMimeExtended, domain) {
    this.oid = org.xmlBlaster.util.isDefined(oid) ? oid : null;
    this.contentMime = org.xmlBlaster.util.isDefined(contentMime) ? contentMime : null;
    this.contentMimeExtended = org.xmlBlaster.util.isDefined(contentMimeExtended) ? contentMimeExtended : null;
    this.queryType = null;
    this.domain = org.xmlBlaster.util.isDefined(domain) ? domain : null;
    this.queryString = "";
  }
  /**
   * @return {string} topicId
   */
  getOid() { return this.oid }
  /**
   * @return {string} the contentMime type like "text/xml"
   */
  getContentMime() { return this.contentMime; }
  hasContentMime() { return this.contentMime != null && this.contentMime.length > 0; }
  /**
   * @return {string} contentMimeExtended additional information
   */
  getContentMimeExtended() { return this.contentMimeExtended; }
  hasContentMimeExtended() { return this.contentMimeExtended != null && this.contentMimeExtended.length > 0; }
  /**
   * @return {string} domain
   */
  getDomain() { return this.domain; }
  hasDomain() { return this.domain != null && this.domain.length > 0; }
  /**
   * @param {string} domain for example "RUGBY"
   */
  setDomain(domain) {
    this.domain = domain;
  }
  /**
   * @return {string} queryType "EXACT" | "XPATH" | "REGEX" | "DOMAIN"
   */
  getQueryType() {
    if (this.queryType == null) this.queryType = "EXACT";
    return this.queryType;
  }
  /**
   * @param {string} queryType "EXACT" | "XPATH" | "REGEX" | "DOMAIN"
   */
  setQueryType(queryType) {
    this.queryType = queryType;
  }
  getQueryString() { return this.queryString; }
  hasQueryString() { return this.queryString != null && this.queryString.length > 0; }
  /**
   * @param {string} queryString for example "//key"
   */
  setQueryString(queryString) {
    this.queryString = queryString;
  }

  isExact() { return "EXACT" == this.queryType; }
  isQuery() { return "XPATH" == this.queryType || "REGEX" == this.queryType; }

  isXPath() { return "XPATH" == this.queryType; }
  isDomain() { return "DOMAIN" == this.queryType; }

  getUrl() {
    if (this.isExact())
      return "exact:" + this.oid;    // Constants.java:EXACT_URL_PREFIX
    else if (this.isXPath())
      return "xpath:" + this.queryString;
    else if (this.isDomain())
      return "domain:" + this.domain;
    else
      return this.oid;
  }
  toXml() {
    var str = "<key oid='" + org.xmlBlaster.util.escapeXmlAttr(this.oid) + "'";
    if (this.hasContentMime()) { str += " contentMime='" + this.contentMime + "'" };
    if (this.hasContentMimeExtended()) { str += " contentMimeExtended='" + org.xmlBlaster.util.escapeXml(this.contentMimeExtended) + "'" };
    if (this.hasDomain()) { str += " domain='" + this.domain + "'"; }
    if (this.queryType != null && this.queryType != "EXACT") { str += " queryType='" + this.queryType + "'"; }
    if (this.queryString != null && this.queryString != "") {
      str += ">" + org.xmlBlaster.util.escapeXml(this.queryString) + "</key>";
    }
    else {
      str += "/>";
    }
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
    return KeyData.parse(document, document.documentElement);
  }

  /**
   * Parse xml key of a xmlBlaster message.
   * @param {Document} domDocument The complete XML W3C DOM tree:
   * interface Document : Node
   * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
   * @param {ChildNode} currentNode The W3C Node
   * @return {KeyData} keyData
   * @see {@link http://www.xmlBlaster.org} <a href="http://www.xmlBlaster.org" target="others">xmlBlaster.org</a>
   */
  static parse(domDocument, currentNode) {
    if (typeof domDocument == "string") {
      // is a xmlString
      domDocument = org.xmlBlaster.util.getDOMDocument(domDocument);
      currentNode = domDocument.documentElement; // is rootNode
    }
    var keyData = new KeyData();
    //keyData.rootNode = currentNode; // remember for user meta data access
    keyData.oid = currentNode.getAttribute('oid');
    keyData.contentMime = currentNode.getAttribute('contentMime');
    keyData.contentMimeExtended = currentNode.getAttribute('contentMimeExtended');
    keyData.queryType = currentNode.getAttribute('queryType');
    keyData.domain = currentNode.getAttribute('domain');
    keyData.queryString = (currentNode.firstChild == null) ? "" : currentNode.firstChild.nodeValue;
    return keyData;

  }

}