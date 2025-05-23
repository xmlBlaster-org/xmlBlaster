import { toBooleanRelaxed, toNumber } from "../xmlBlaster/util.js";
import { orgXmlBlasterEscapeReservedArrayKeyWord, orgXmlBlasterUNEscapeReservedArrayKeyWord, splitCSVQuoted } from "../xmlBlaster/util/MapObject.js";

/**
 * Convenience access for typed data from native Map and json dumper/parser. 
 * Map containing Map is supported, type is {Map<string,object}
 * 
 * @author Marcel
 */
export class MapObject extends Map {
  /**
   * @param {Map=} map
   */
  constructor(map = null) {
    super(map == null ? new Map() : map);
  }

  /**
   * @param {string} key 
   * @param {string} defaultValue 
   * @returns {number|boolean|string|object} eg QosData
   */
  getTyped(key, defaultValue = null) {
    if (defaultValue != null) {
      if (defaultValue.constructor == String)
        return this.getStr(key, defaultValue);
      else if (defaultValue.constructor == Number)
        return this.getNumber(key, defaultValue);
      else if (defaultValue.constructor == Boolean)
        return this.getBoolean(key, defaultValue);
    }
    const val = this.get(key);
    if (val === "undefined") {
      return defaultValue;
    }
    return val;
  }

  /**
   * @param {string} key 
   * @param {string} defaultValue 
   * @returns {string}
   */
  getStr(key = null, defaultValue = "") {
    const value = super.get(key);
    if (value === undefined || value == null) {
      return defaultValue;
    }
    if (typeof (value) == "string") {
      return value;
    }
    else if (value instanceof String) {
      return value.toString(); // typeof() -> "string"
    }
    return "" + value;
  }

  /**
   * Relaxed lookup, see
   * @param {string} key
   * @param {boolean} defaultValue
   * @return {boolean}
   */
  getBoolean(key = null, defaultValue = false) {
    const value = super.get(key);
    if (value === undefined || value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean) {
      return value.valueOf();
    } else if (value instanceof String) {
      return org.xmlBlaster.util.toBooleanRelaxed(value, defaultValue);
    }
    return org.xmlBlaster.util.toBooleanRelaxed(String(value), defaultValue);
  }

  /**
   * @param {string} key
   * @param {number} defaultValue which defaults to 0 (not NaN)
   * @return {number}
   */
  getNumber(key = null, defaultValue = 0) {
    const value = super.get(key);
    if (value === undefined || value == null) {
      return defaultValue;
    }
    if (value instanceof Number) {
      return value.valueOf();
    }
    else if (value instanceof String) {
      return org.xmlBlaster.util.toNumber(value, defaultValue);
    }
    else if (value === true) {
      return 1;
    }
    else if (value === false) {
      return 0;
    }
    return org.xmlBlaster.util.toNumber(String(value), defaultValue);
  }

  /**
   * @param {string} key
   * @param {MapObject} defaultValue
   * @return {MapObject}
   */
  getMap(key, defaultValue = new MapObject()) {
    const value = super.get(key);
    if (value === undefined || value == null) {
      return defaultValue;
    }
    if (value instanceof Map) {
      return new MapObject(value);
    }
    return defaultValue;
  }

  /**
   * @param {string} key
   * @param {Array} defaultValue
   * @return {Array}
   */
  getArray(key, defaultValue = new Array()) {
    const value = super.get(key);
    if (value === undefined || value == null) {
      return defaultValue;
    }
    if (value instanceof Array) {
      return value;
    }
    return defaultValue;
  }

  /**
   * To JSON Object notation. 
   * <pre>
   {
     "aKeyNum": 1,
     "aKeyStr": "1",
     "aKeyBool": true
   }
   * </pre>
   * 
   * @param {number} indentFactor eg 2 for nice formatting
   * @return {string} never null
   */
  toJsonStr(indentFactor) {
    const obj = this.mapToObj(this);
    return JSON.stringify(obj, null, indentFactor);
    // map = new Map(JSON.parse(jsonText));
  }

  /**
   * Handles Map in Map nicely. 
   * @param {Map?} map 
   * @returns {object}
   * @see org.xmlBlaster.util.mapToJson(strMap); org.xmlBlaster.util.mapToObj(strMap);
   */
  mapToObj(map = null) {
    if (!map) {
      map = this;
    }
    const useEcma6 = false; // Else Map in Map fails
    if (useEcma6) {
      const result = Object.fromEntries(map);
      return result;
    }
    return Array.from(map).reduce((obj, [key, value]) => {
      if (value && value instanceof Map) {
        value = this.mapToObj(value);
      } else if (value && value instanceof Array) {
        var resultArr = [];
        for (let elem of value) {
          if (elem && elem instanceof Map) {
            elem = this.mapToObj(elem);
          }
          resultArr.push(elem);
        }
        value = resultArr;
      }
      obj[key] = value;
      return obj;
    }, {});
  }

  /**
   * @param {number} indentFactor
   * @return '[["foo","bar"],["baz","blob"]]'
   */
  toJsonArr(indentFactor) {
    return JSON.stringify([...this], null, indentFactor);
    // return JSON.stringify(Array.from(super.entries()), null, indentFactor);
  }

  /**
   * @param {string} jsonStr
   * <pre>
   {
     "aKeyNum": 1,
     "aKeyStr": "1",
     "aKeyBool": true
   }
   * </pre>
   * @return {MapObject}
   */
  static parseJSON(jsonStr) {
    const map = new MapObject();
    const obj = JSON.parse(jsonStr);
    MapObject.parseObject(obj, map);
    return map;
  }

  /**
   * @param {object} obj 
   * @param {MapObject|null} map 
   * @return {MapObject} never null extends Map<string, object>
   * @see org.xmlBlaster.util.objToMap(obj)
   */
  static parseObject(obj, map = new MapObject()) {
    Object.keys(obj).forEach(k => {
      let value = obj[k];
      if (value instanceof Array) {
      }
      else if (value instanceof Object) {
        value = MapObject.parseObject(value);
      }
      map.set(k, value);
    });
    return map;
  }

  /**
   * Caution: Looses native data type when parsed again. Use toJson to preserve data type. 
   * @param {boolean} escapeSeparator
   * @param {string} separator
   * @param {string} assign
   * @return {string} csv "a=1,b=2,c=5"
   */
  toCsv(escapeSeparator = true, separator = ",", assign = "=") {
    try {
      const size = this.size;
      var s = new Array(size);
      let i = -1;

      for (const [keyEsc, v] of this) {
        i++;
        let key = orgXmlBlasterUNEscapeReservedArrayKeyWord(keyEsc);
        s[s.length] = key; // "map"
        if (v !== undefined && v != null) {
          s[s.length] = assign;
          if (escapeSeparator) { // && v.indexOf(seperator) != -1) {
            var str = v.toString();
            //var str = org.xmlBlaster.util.replaceAllTokens(v.toString(), ",", "&comma;");
            //if (separator == ";") {
            //  str = org.xmlBlaster.util.replaceAllTokens(str, ";", "%3B");
            //}
            str = org.xmlBlaster.util.escapeCsvValue(str, true, separator); // also escape &#034; -> "
            s[s.length] = str;
          }
          else {
            s[s.length] = v.toString();
          }
        }
        if (i != size - 1)
          s[s.length] = separator;
      }
    } catch (e) {
      //do nothing here
    }

    return s.join("");
  }

  /**
   * Static method to parse comma separated value. 
   * An empty key (with no =) is assumed to be boolean true
   * An empty key (with =) is assumed to be an empty string
   * Unescapes "&comma;" -> "," if separator is ","
   * <p>
   * Caution: Does currently NOT support protected value by apostrophe like "addr=Hauptstr, Konstanz",lat=47.0
   * @param {string} csv "platform=Nokia,version=1.0,aTrueBool,aTrueBool2=true,aFalseBool=false,anEmptyString=" 
   * @param {string} separator defaults to ","
   * @param {string} assign defaults to "="
   * @return {MapObject} never null extends Map<string, object>
   */
  static parseCSV(csv = null, separator = ",", assign = "=") {
    const hash = new MapObject();
    if (csv == null) return hash;
    var arr = csv.split(separator);
    for (var i = 0, l = arr.length; i < l; i++) {
      var tuple = arr[i];
      var index = tuple.indexOf(assign);
      if (index >= 0) {
        let key = tuple.substring(0, index);
        const value = tuple.substring(index + 1);
        key = orgXmlBlasterEscapeReservedArrayKeyWord(key);
        /*
        if (separator == ",") {
          hash.put(key, org.xmlBlaster.util.unescapeCsvValue(value, false, separator));//org.xmlBlaster.util.replaceAllTokens(value, "&comma;", ","));
        }
        else {
          hash.put(key, value);
        }
        */
        hash.set(key, org.xmlBlaster.util.unescapeCsvValue(value, true, separator));
      }
      else {
        let key = tuple;
        const value = true;
        key = orgXmlBlasterEscapeReservedArrayKeyWord(key);
        hash.set(key, value);
      }
    }
    return hash;
  }

  /**
   * Same as #parseCSV() but supports quotes. 
   * @param {string} csv 'reportDelivery=xxx,footerText=yyy'
   * @param {string|null} separator ","
   * @param {string|null} assign "="
   * @return {MapObject} never null
   */
  static parseCSVQuoted(csv = null, separator = ",", assign = "=", quotechar = '"',
    trimEmpty = true, preserveInsideQuoteChar = false, trimKey = false, trimValue = false) {
    var hash = new MapObject();
    if (csv == null) return hash;

    var arr = splitCSVQuoted(csv, separator, quotechar, trimEmpty, preserveInsideQuoteChar);
    for (let i = 0, l = arr.length; i < l; i++) {
      const tuple = arr[i];
      let index = tuple.indexOf(assign);
      if (index >= 0) {
        let key = tuple.substring(0, index);
        key = orgXmlBlasterEscapeReservedArrayKeyWord(key);
        if (trimKey) {
          key = org.xmlBlaster.util.trim(key);
        }
        let value = tuple.substring(index + 1);
        if (trimValue) {
          value = org.xmlBlaster.util.trim(value);
        }
        hash.set(key, org.xmlBlaster.util.unescapeCsvValue(value, true, separator));
        /*      
              if (separator == ",") {
                hash.put(key, org.xmlBlaster.util.unescapeCsvValue(value, true, separator));//org.xmlBlaster.util.replaceAllTokens(value, "&comma;", ","));
              }
              else if (separator == ";") {
      //          flag = org.xmlBlaster.util.replaceAllTokens(flag, ";", "%3B");
      //          text = org.xmlBlaster.util.replaceAllTokens(text, "%3B", ";");
                hash.put(key, org.xmlBlaster.util.unescapeCsvValue(value, true, separator));//org.xmlBlaster.util.replaceAllTokens(value, "&comma;", ","));
              }
              else {
                hash.put(key, value);
              }
        */
      }
      else {
        let key = tuple;
        key = orgXmlBlasterEscapeReservedArrayKeyWord(key);
        const value = true;
        hash.set(key, value);
      }
    }
    return hash;
  }

  /**
   * @param {string} str 
   * @param {string} mimeType 
   * @return {MapObject} never null extends Map<string, object>
   */
  static parse(str = "", mimeType = "") {
    const mimeTypeUpper = (mimeType == null) ? "" : mimeType.toUpperCase();
    if (mimeTypeUpper.includes("JSON")) {
      return MapObject.parseJSON(str);
    }
    if (mimeTypeUpper.includes("CSV")) {
      return MapObject.parseCSV(str);
    }
    if (str.length == 0) {
      return MapObject.parseCSV(str);
    }
    // guess
    if (org.xmlBlaster.isJsonStr(str)) {
      return MapObject.parseJSON(str);
    }
    // guess
    return MapObject.parseCSV(str);
  }

  /*
   * @return {Properties} Never null
   getProperties() {
    Properties p = new Properties();
    for (Map.Entry<String, Object> entry : this.entrySet()) {
      p.put(entry.getKey(), entry.getValue());
    }
    return p;
  }
   */
}
