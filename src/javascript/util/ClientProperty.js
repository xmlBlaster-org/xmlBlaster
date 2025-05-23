import { Base64 } from "./Base64.js";

const ME1 = "ClientProperty.js";
/**
 * @class Holds one client property.
 * <p/>
 * For example:<br/>
 * <pre>
 * &lt;clientProperty name='myDescription' encoding='base64' charset='windows-1252'>QUUgaXMgJ8QnDQpPRSBpcyAn1icNCnNzIGlzICffJw==&lt;/clientProperty>
 * </pre>
 */
export class ClientProperty {

  /**
   * @param {string} name The unique key of the client property
   * @param {object=} value The value encoded as specified with encoding
   * @param {string=} encoding Mark the charset for a base64 encoded String
   * @param {string=} type
   * @param {string=} charset
   */
  constructor(name, value, encoding, type, charset) {
    /** private */
    this.name = name;
    /** private */
    this.type = type || "String"; // see Constants.java: TYPE_BLOB etc.
    /**
     * The value encoded as specified with encoding
     * private
     */
    this.value = org.xmlBlaster.util.isFilled(value) ? value : "";
    /** private */
    this.encoding = org.xmlBlaster.util.isFilled(encoding) ? encoding : null;
    /** private */
    this.charset = charset || null;    // Needed for Base64 encoding
  }

  getName() { return this.name; }
  /**
   * For example Constants.TYPE_INTEGER="int" or Constants.TYPE_BLOB="byte[]"
   * @return {string}
   */
  getEncoding() { return this.encoding; }
  /** Returns the charset, for example "cp1252" or "UTF-8", helpful if base64 encoded
   * @return {string}
   */
  getCharset() { return this.charset; }
  /**
   * The value encoded as specified with encoding
   * @return {string}
   */
  getValueRaw() { return this.value; }
  /**
    * The string representation of the value.
    * <p />
    * If the string is base64 encoded with a given charset, it is decoded
    * and transformed to the default charset, typically "UTF-8"
    * @return {string} The value which is decoded (readable) in case it was base64 encoded, can be null
    */
  getValueStr() {
    if (this.value == null || this.value.length == 0) return "";
    if ("base64" == this.encoding) { // Constants.ENCODING_BASE64
      var trimmedValue = org.xmlBlaster.util.trim(this.value);
      //var content = org.xmlBlaster.util.Base64._utf8_decode(trimmedValue);
      var content = Base64.decode(trimmedValue);
      log.debug(ME1 + "Decoded base64");
      //log.debug(ME1+"Decoding '" + trimmedValue + "' to '" + content + "'");
      /*
      if (getCharset() != null) {
         try {
            return new String(content, getCharset()).valueOf();
         } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
         }
      }
      */
      return content;
    }
    return this.value;
  }
  /**
   * The value in the correct type.
   * @return Number, Boolean or String
   */
  getValue() {
    var val = this.getValueStr();
    switch (this.type) {
      case "boolean":
        return org.xmlBlaster.util.parseBoolean(val);
      case "double":
      case "float":
      case "int":
      case "short":
      case "long":
        return new Number(val).valueOf(); // parseInt(), parseFloat()
      case "byte":
      case "String":
      case "byte[]":
        return val;
      default:
        return val;
    }
  }
  /**
   * Access the XML formatted clientProperty
   * @return {string}
   */
  dump() {
    var str = "<clientProperty";
    if (this.name != null) str += " name='" + org.xmlBlaster.util.escapeXmlAttr(this.name) + "'";
    if (this.encoding != null) str += " encoding='" + this.encoding + "'";
    if (this.charset != null) str += " charset='" + this.charset + "'";
    str += ">";
    if (this.value != null) str += org.xmlBlaster.util.escapeXml(this.value);
    str += "</clientProperty>";
    return str;
  }

  static CLIENTPROPERTY_ISINITIALUPDATE = "__isInitialUpdate"; // msgs from history queue directly after subscribe, see Constants.java

}