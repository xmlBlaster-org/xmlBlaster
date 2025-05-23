import { ConnectQosData } from "./client/ConnectQosData.js";
import { KeyData } from "./client/KeyData.js";
import { QosData } from "./client/QosData.js";
import { ClientProperty } from "./util/ClientProperty.js";
import { MsgUnit } from "./util/MsgUnit.js";
import { XmlBlasterException } from "./util/XmlBlasterException.js";
import { log } from "../util/Logger.js";

export function isString(str) {
  return (typeof (str) === 'string' || str instanceof String);
}

/**
 * @param {string} str "50b1a302-92d7-4d1b-bfd4-055d784cf747" 
 * or "50b1a302-92d7-4d1b-bfd4-055d784cf747|e88f6a0f-330c-460d-a1be-d74b7b54934b|f5ce06b3-52e0-472d-9933-f83df61c9e94|3154cb70-127e-4bf4-94ad-e96b612e534d"
 * @return true if given string fits our guid scheme
 */
export function isGUID(str) {
  return /[\w]{8}(-[\w]{4}){3}-[\w]{12}\b/.test(str);
}

/**
 * compareMap isSame(map)
 * @param {Map} map1
 * @param {Map} map2
 * @return {boolean}
 */
export function isSameMap(map1, map2) {
  if (!map1 || !map2) {
    return false;
  }
  if (map1 instanceof Map && map2 instanceof Map) {
    if (map1.size != map2.size) {
      return false;
    }
    for (const [key, value] of map1) {
      if (map2.get(key) != value) {
        return false;
      }
    }
    return true;
  }
  return false;
}

/**
 * @param {Map<string, Object>} map
 * @return {object}
 */
export function mapToObj(map) {
  const useEcma6 = true;
  if (useEcma6) {
    const result = Object.fromEntries(map);
    return result;
  }
  let obj = Object.create(null);
  for (let [k, v] of map) {
    // We don’t escape the key '__proto__'
    // which can cause problems on older engines
    obj[k] = v;
  }
  return obj;
}

/**
 * Please use MapObject.js !
 * 
 * @param {Map<string, Object>} strMap
 * @param {(this: any, key: string, value: any) => any} replacer
 * @param {string|number|null} space
 * @return {string} '{"key1":"value1","key2":"value2","keyB":true,"keyN":12}'
 */
export function mapToJson(strMap, replacer, space) {
  const obj = mapToObj(strMap);
  return JSON.stringify(obj, replacer, space);
}

/**
 * @param {object} obj
 * @return {Map<string, Object>}
 */
export function objToMap(obj) {
  const useEcma6 = true;
  if (useEcma6) {
    const result = new Map(Object.entries(obj));
    return result;
  }
  let strMap = new Map();
  for (let k of Object.keys(obj)) {
    strMap.set(k, obj[k]);
  }
  return strMap;
}

/**
 * Please use MapObject.js !
 * 
 * @param {string} jsonStr '{"key1":"value1","key2":"value2","keyB":true,"keyN":12}'
 * @return {Map<string, Object>}
 */
export function jsonToMap(jsonStr) {
  return objToMap(JSON.parse(jsonStr));
}

/*
 * @param {number} elapsedMillis = date2.getTime() - date1.getTime();
 */
/**
 * Convert to human readable time, max entity currently supported is days.
 * <pre> 
 * var elapsedMillis = 71303 * 1000;
 * var dataHolder = getDiffDateHumanReadable(elapsedMillis, "J ", "M ", "T ", "h ", "min ", "sec");
 * -> dataHolder.result == "19h 48min 23sec"
 * </pre>
 * @param {number} elapsedMillis = date2.getTime() - date1.getTime();
 * @param {string|null} yearEntity not supported
 * @param {string|null} monthEntity not supported
 * @param {string|null} dayEntity
 * @param {string|null} hourEntity
 * @param {string|null} minuteEntity
 * @param {string|null} secondEntity
 * @return {{days:number, hours:number, minutes:number, seconds:number, result: string}} Use {string} dataHolder.result
 */
export function getDiffDateHumanReadable(elapsedMillis, yearEntity, monthEntity, dayEntity, hourEntity, minuteEntity, secondEntity) {
  var negative = "";
  if (elapsedMillis < 0) {
    negative = "- ";
    elapsedMillis *= -1;
  }
  var dayEntity = dayEntity || " ";
  var hourEntity = hourEntity || ":";
  var minuteEntity = minuteEntity || ":";
  var secondEntity = secondEntity || "";

  var diffDate = elapsedMillis / 1000; // sec
  // Anzahl Tage = Sekunden /24/60/60
  // floor() liefert nur den Anteil vor dem Komma
  var days = Math.floor(diffDate / 24 / 60 / 60);
  // den verbleibenden Rest berechnen = Stunden
  diffDate = diffDate - (days * 24 * 60 * 60);
  // den Stundenanteil herausrechnen
  var hours = Math.floor(diffDate / 60 / 60);
  diffDate = (diffDate - (hours * 60 * 60));
  // den Minutenanteil
  var minutes = Math.floor(diffDate / 60);
  diffDate = diffDate - (minutes * 60);
  // die verbleibenden Sekunden
  var seconds = Math.floor(diffDate);
  // und das ganze dann Anzeigen:
  var str = (seconds < 10) ? "0" + seconds : seconds;
  var result = seconds > 0 ? str + secondEntity : "";
  if (minutes > 0 || ((days > 0 || hours > 0) && seconds > 0)) {
    str = (minutes < 10) ? "0" + minutes : minutes;
    result = str + minuteEntity + result;
  }
  if (hours > 0 || (days > 0 && (minutes > 0 || seconds > 0))) {
    str = (hours < 10) ? "0" + hours : hours;
    result = str + hourEntity + result;
  }
  if (days > 0) {
    result = days + dayEntity + result;
  }
  result = negative + result;
  //alert(elapsedMillis + "> Noch: " + days + " Tage, + " + hours + " Stunden, " + minutes + " Minuten und " + seconds + " Sekunden. Result=" + result);
  var data = {
    days: days,
    hours: hours,
    minutes: minutes,
    seconds: seconds,
    result: trim(result)
  };
  return data;
}


/**
 * Camelizes a String, e.g
 *
 * font-weight -> fontWeight
 */
export function camelize(text) {
  return text.replace(/(?:^|[-_])(\w)/g, function (_, c, idx) {
    if (idx == 0) return c; //first letter stays lowercase
    return c ? c.toUpperCase() : '';
  });
}



export function toHex(decimal) {
  var hexChars = "0123456789ABCDEFabcdef";
  return "%" + hexChars.charAt(decimal >> 4) + hexChars.charAt(decimal & 0xF);
}



/**
 * function setupParameters
 * Creates an object property window.location.parameters which
 * is an associative array of the URL querystring parameters used
 * when requesting the current document.
 * If the parameter is present but has no value, such as the parameter
 * flag in http://example.com/index.php?flag&id=blah, true is stored.
 * original idea from Marlin Forbes (http://www.datashaman.com) MIT
 */
export function setupParameters() {
  var parameters = new Object();
  if (window.location.search) {
    parameters = splitParameters(window.location.search.substr(1));
  }
  window.location.parameters = parameters;
}

/**
 * Split the string with separators '&amp;'. 
 * @param {string} str The url
 * @return {{}} Containing arr[key]=value
 */
export function splitParameters(str) {
  var parameters = new Object();
  var paramArray = str.split('&');
  var length = paramArray.length;
  for (var index = 0; index < length; index++) {
    var param = paramArray[index].split('=');
    var name = param[0];
    var value = typeof param[1] == "string" ? decodeURIComponent(param[1].replace(/\+/g, ' ')) : true;
    parameters[name] = value;
  }
  return parameters;
}

/**
 * Access a URL paramater
 * @param {string} name
 * @param {any} defaultVal If Boolean the return is a raw true or false
 * @return {string|boolean|null} value or null if not found or Boolean-true for a key without value
 */
export function getParameter(name, defaultVal) {
  var defaultVal = isDefined(defaultVal) ? defaultVal : null;
  if (typeof window.location.parameters == "undefined")
    setupParameters();
  var ret = window.location.parameters[name];
  if (isDefined(ret)) {
    if (isDefined(defaultVal) &&
      (defaultVal === true || defaultVal === false || defaultVal instanceof Boolean)) {
      return parseBoolean(ret); // true or false
    }
    return ret;
  }
  return defaultVal;
}

var ME1 = "xmlBlaster.js: ";

/**
 * Checks if given variable is defined and not null
 * @param {object} variable The variable to test
 * @return {boolean} true if it has assigned a not-null value
 * @see {@link #isUndefined()} from prototype.js
 */
export function isDefined(variable) {
  if (variable === undefined)
    return false;
  if (variable === true || variable === false) return true;
  if (variable == null)
    return false;
  return true;
}


/**
 * Checks if given variable is defined and has a none empty string
 * @param {string} variable The variable to test
 * @return {boolean} true if contains a string with length > 0
 */
export function isFilled(variable) {
  if (isDefined(variable)) {
    if (isDefined(variable.length))
      return (variable.length > 0);
    else
      return true;
  }
}

/**
 * As the javascript Boolean has no parser
 * <pre>
 *  var value = "false";
 *  log.warn("string value=" + value + " evaluates Boolean(value) to="  + Boolean(value));
 * </pre>
 * The second term will be true!! (the Boolean constructor expect a raw true and not a string)
 * This function parse the string 'true' case insensitive.
 * @param {string} boolString e.g. "true"
 * @param {boolean} [defaultVal]
 * @return {boolean} true or false, defaults to false
 * @deprecated Use toBoolean
 */
export function parseBoolean(boolString, defaultVal) {
  return toBoolean(boolString, defaultVal);
}

/**
 * Checks if given variable is defined and is true. 
 * <p>
 * Case insensitive: "True" and "true" is both true
 * <p>
 * @see toBooleanRelaxed() which treats "1", "on", "true" as true
 * @param {any} variable The variable to test
 * @param {boolean=} defaultVal
 * @return {boolean} defaultVal If variable is empty or undefined or null
 * false in all other error cases without a given defaultVal
 */
export function toBoolean(variable, defaultVal) {
  if (isFilled(variable)) {
    //alert("Typeof="+typeof variable)
    if (typeof variable == "boolean") // typeof (new Boolean(true) is 'object'
      return variable == true;
    if (variable === true || variable === false)
      return variable;
    if (variable == true) // matches new Boolean(true)
      return true;
    if (("" + variable).toLowerCase() == "true")
      return true;
    return false;
  }
  return isDefined(defaultVal) ? defaultVal : false;
}

/**
 * Checks if given variable is defined and is true. 
 * "1" and "on" and "true" evaluates to true
 * <p>
 * Case insensitive: "True" and "true" is both true
 * @param {any} variable The variable to test
 * @param {boolean} defaultVal
 * @return {boolean} defaultVal if variable is empty or undefined or null
 * false in all other error cases without a given defaultVal
 */
export function toBooleanRelaxed(variable, defaultVal) {
  if (isFilled(variable)) {
    //alert("Typeof="+typeof variable)
    if (typeof variable == "boolean") // typeof (new Boolean(true) is 'object'
      return variable == true;
    if (variable === true || variable === false)
      return variable;
    if (variable == true) // matches new Boolean(true)
      return true;
    var str = ("" + variable).toLowerCase();
    if (str == "true" || str == "1" || str == "on")
      return true;
    return false;
  }
  return isDefined(defaultVal) ? defaultVal : false;
}

/**
 * Checks if given variable a native boolean. 
 * <p>
 * Case insensitive: "True" and "true" is both true
 * @param {any} variable The variable to test
 * @return {boolean} true if type Boolean or true|false
 */
export function isBoolean(variable) {
  if (variable === undefined || variable == null)
    return false;
  if (typeof variable == "boolean") // typeof (new Boolean(true) is 'object'
    return true;
  if (variable === true || variable === false)
    return true;
  if (variable == true) // matches new Boolean(true)
    return true;
  return false;
}

/**
 * For localization see new Intl.NumberFormat(localeId, {style: "decimal", maximumFractionDigits: mFD}).format(number);
 * @param {any} variable 
 * @param {number?} defaultVal 
 * @returns {number}
 */
export function toNumberTolerant(variable, defaultVal = 0) {
  if (!isDefined(defaultVal))
    defaultVal = 0;
  if (!isDefined(variable))
    return defaultVal;

  if (typeof variable == "number")
    return variable.valueOf();

  const id = getLocaleId("de-DE");
  if (id.indexOf("de") == 0) {
    variable = variable.replace(/,/g, ".");
  }

  const n = new Number(variable).valueOf();
  if (isNaN(n))
    return defaultVal;
  return n;
}

/**
 * Checks if given variable is defined and has a none empty string
 * For localization see new Intl.NumberFormat(localeId, {style: "decimal", maximumFractionDigits: mFD}).format(number);
 * @param {any} variable The variable to convert
 * @param {number} defaultVal Optionally a fall back native number, defaults to 0 if not given
 * @param {number|null} precision the number of decimals
 * @return {number} The native number, 0 for NaN and no defaultVal
 */
export function toNumber(variable, defaultVal, precision = null) {
  if (!isDefined(defaultVal))
    defaultVal = 0;
  if (!isDefined(variable))
    return defaultVal;

  if (typeof variable == "number") {
    let num = variable.valueOf();
    if (precision != null) {
      return round(num, precision); // decimals
    }
    return num;
  }

  var n = parseFloat(variable);
  //var n = parseFloat('KKK', 10); // NaN 
  //var n = parseInt('15,123', 10); // NaN
  //var number = new Number(variable);
  //if (isNaN(number))
  //  return defaultVal;
  //var n = number.valueOf();
  if (isNaN(n)) {
    return defaultVal;
  }
  if (precision != null) {
    return round(n, precision); // decimals
  }
  return n;
}

//http://de.wikipedia.org/wiki/Alpha_Blending#Berechnung
export function alphaBlendComponent(a, b, alphaA) {
  return Math.round(alphaA * a + (1 - alphaA) * b);
}

export function alphaBlend(A, B, alphaA) {
  var As = qx.util.ColorUtil.stringToRgb(A);
  var Bs = qx.util.ColorUtil.stringToRgb(B);

  var Cs = [];
  for (var i = 0; i < As.length; i++) {
    Cs[i] = alphaBlendComponent(As[i], Bs[i], alphaA);
  }

  var C = qx.util.ColorUtil.rgbToHexString(Cs);

  return C;
}

/**
 * Returns the linear interpolated value, bounded by high and low
 * @param {number} s the scalar value
 * @param {number} outLow
 * @param {number} outHigh
 * @return {number} interpolated Value
 */
export function linearInterpolation(s, outLow, outHigh) {
  if (s <= 0) return outLow;
  if (s >= 1) return outHigh;
  return outLow + s * (outHigh - outLow);
}

export function linearScalar(inCurr, inLow, inHigh) {
  return (inCurr - inLow) / (inHigh - inLow);
}

/**
this function combines above two functions

---------inLow---------inCurr---inHigh-----

--outLow------outCurr--outHigh--------   

inCurr-inLow   outCurr-outLow
------------ = --------------  = s (scalar from above)
inHigh-inLow   outHigh-outLow
*/
export function linearMagic(inLow, inCurr, inHigh, outLow, outHigh) {
  if (inCurr <= inLow) return outLow;
  if (inCurr >= inHigh) return outHigh;
  var outCurr = ((inCurr - inLow) / (inHigh - inLow)) * (outHigh - outLow) + outLow;
  return outCurr;
}

//https://stackoverflow.com/questions/1573053/javascript-function-to-convert-color-names-to-hex-codes
export function named2Hex(str) {
  var ctx = document.createElement('canvas').getContext('2d');
  ctx.fillStyle = str;
  return ctx.fillStyle;
}

export function componentToHex(c) {
  var hex = c.toString(16); // new Number(c).toString(16)
  return hex.length == 1 ? "0" + hex : hex;
}

/**
 * Split to lines
 * @param {string} text
 * @param {number} maxChars
 * @param {string|?} separator defaults to &lt;br />
 * @return {string} text with added &lt;br />
 */
export function splitHtml(text, maxChars, separator) {
  if (!isFilled(text)) return "";
  var separator = isFilled(separator) ? separator : "<br />";
  var maxChars = toNumber(maxChars, 256);
  if (maxChars < 20) maxChars = 20;
  var count = 0;
  for (var i = 0; i < text.length; i++) {
    if (count > maxChars && text[i] == ' ') {
      text = text.substring(0, i) + separator + text.substring(i + 1);
      count = 0;
      continue;
    }
    count++;
  }
  return text;
}

/**
 * @param {number} meters 10702.610987062495
 * @return {string} 10'702
 */
export function toNiceDistance(meters) {
  //"10702.610987062495"
  //if (meters < 1000) {
  //  return round(meters, 3);
  //}
  var integer = Math.round(meters);
  if (integer < 1000) return "" + integer;
  var ret = "";
  var str = "" + integer;
  var len = str.length;
  for (var i = 0; i < len; i++) {
    if ((len - i) % 3 == 0)
      ret += "' ";
    ret += str[i];
  }
  return ret;
}

/**
 * Returns integer for decimals < 1
 * @param {number} number e.g. 10.30295
 * @param {number} decimals e.g. 2
 * @return {number} 10.30
 */
export function round(number, decimals = null) {
  if (decimals != null) {
    if (decimals > 12)
      decimals = 12;
    if (decimals < 1) {
      return Math.round(number);
    }
    var P = Math.pow(10, decimals);
    var result = Math.round(number * P) / P;
    return result;
  }
  return number;
}

/**
 * @param {string} str "1"
 * @param {number} width 3
 * @return {string} "001"
 */
export function fillZero(str, width) {
  if (str == null)
    str = "";
  var dif = width - str.length;
  var res = "";
  for (var i = 0; i < dif; i++) {
    res += "0";
  }
  res += str;
  return res;
}

/**
 * Parse given XML string to DOM document.
 * W3C definition: interface Document : Node
 * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
 * @return {Document} The DOM document, access root node with
 *  var rootNode = doc.documentElement;
 */
export function getDOMDocument(xmlString) {
  var parser = new DOMParser();
  return parser.parseFromString(xmlString, "text/xml");;
}


///////////////
// DATE Section
/**
 TODO :
 http://stackoverflow.com/questions/492994/compare-two-dates-with-javascript
 in Summary: ==, !=, ===, and !==  need getTime() to work,
 comparison with >, <=, etc is fine on Date Object itself

 Date prototype extended in log4javascript.js

  Date.prototype.getDifference = function(date) {
    return this.getTime() - date.getTime();
  };

  Date.prototype.isBefore = function(d) {
    return this.getTime() < d.getTime();
  };
*/

/**
 * @param {Date} date1
 * @param {Date} date2
 * @return {boolean} date1 < date2
 */
export function isBefore(date1, date2) {
  return date1.getTime() < date2.getTime();
}

/**
 * 
 * @param {string} isoTimeStr "12:45" or "12:45:56.243"
 * @param {number} timezoneOffsetMillis including day light saving time
 * @param {boolean} defaultToCurrentDate
 * @return {Date}
 */
export function getDateFromIsoTimeStr(isoTimeStr, timezoneOffsetMillis = 0, defaultToCurrentDate = true) {
  var defaultToCurrentDate = toBoolean(defaultToCurrentDate, true);
  var timezoneOffsetMillis = toNumber(timezoneOffsetMillis, 0);
  if (!isFilled(isoTimeStr))
    return (defaultToCurrentDate ? new Date() : null);
  // All dates are calculated in milliseconds from 01 January, 1970 00:00:00 Universal Time (UTC) with a day containing 86,400,000 milliseconds
  //var index = isoTimeStr.indexOf("Z");
  //if (index > 0) {
  //  isoTimeStr = isoTimeStr.substring(0, index);
  //}
  var isoTimeStr = trim(isoTimeStr);

  // In case a complete Timestamp was given, strip date
  var indexSep = isoTimeStr.indexOf("T");
  if (indexSep == -1) {
    indexSep = isoTimeStr.indexOf(" ");
  }
  if (indexSep > 0) {
    isoTimeStr = isoTimeStr.substring(indexSep + 1);
  }

  var array = isoTimeStr.split(":");
  var hours = array.length > 0 ? toNumber(array[0], 0) : 0;
  var minutes = array.length > 1 ? toNumber(array[1], 0) : 0;
  var secondsStr = array.length > 2 ? array[2] : "";
  var seconds = 0;
  var milliseconds = 0;
  if (secondsStr.length > 0) {
    var secArr = secondsStr.split(".");
    seconds = secArr.length > 0 ? toNumber(secArr[0], 0) : 0;
    milliseconds = secArr.length > 1 ? toNumber(secArr[1], 0) : 0;
  }

  var worksWithoutDaylightSavingOnly = false;
  if (worksWithoutDaylightSavingOnly) {
    var year = 0; // We can't take "Thu Jan 01 1970" as this is without day light saving time! 
    var month = 0;
    var day = 0;
    var millis = 1000 * (hours * 3600 + minutes * 60 + seconds) + milliseconds + timezoneOffsetMillis;
    var date = new Date(millis);
    //var date = new Date(year, month, day, hours, minutes, seconds, milliseconds);
    return date;
  }
  else {
    var now = new Date();
    var year = now.getFullYear();
    var month = now.getMonth(); // 0-11
    //var day = now.getDay(); // day of the week (from 0-6)
    var day = now.getDate(); // day of the month (1-31)
    var tonight = new Date(year, month, day); // day of the month (1-31)
    tonight.setHours(hours);
    tonight.setMinutes(minutes);
    tonight.setSeconds(seconds);
    tonight.setMilliseconds(milliseconds);
    return tonight;
  }
}

/**
 * Parses with 'T' or ' ' date-time separator, works fine for GMT.
 * <br />
 * Local time input +3h and output to MEZ (MEZ is GMT+2h in summer):
 * "2009-07-26 22:14:55.679769+03:00" --> date.toString()="Sun Jul 26 2009 21:14:55 GMT+0200 (CEST)" ???
 * <br />
 * Example: 2005-03-28T17:05:30.5433Z+01:00
 * <br />
 * Up to three milli fraction digits are supported
 * <br />
 * CAUTION: This fails if no MILLIS are given!!! BUG TODO
 * @param {string} isoDateStr "2007-06-06T13:55:09Z" (Z stands 'Zulu' for UTC==GMT)
 * @param {boolean} defaultToCurrentDate defaults to true
 * true: if isoDateStr is invalid, the current date is returned.
 * false: if isoDateStr is invalid, null is returned
 * @return {Date} the local date object, never null for defaultToCurrentDate==true
 * @author web url
 * @deprecated Use getDateFromIsoTimestampStr() as we pass a TS not a Date
 */
export function getDateFromIsoDateStr(isoDateStr, defaultToCurrentDate) {
  return getDateFromIsoTimestampStr(isoDateStr, defaultToCurrentDate);
}
/**
 * @param {string|number|Date} isoDateObject ISO date string "2024-01-31T18:34:38.2454Z". 
 *    If isoDateObject is of type Date it is directly returned
 *    If isoDateObject is of type number the elapsed millis are assumed
 * @param {boolean} defaultToCurrentDate defaults to true
 * @returns {Date|null} null only on error && defaultToCurrentDate==false
 */
export function getDateFromIsoTimestampStr(isoDateObject, defaultToCurrentDate) {
  if (Object.prototype.toString.call(isoDateObject) === "[object Date]") {
    // Assume it is Date already (typeof isoDateObject != "string")
    // @ts-ignore
    return isoDateObject;
  }
  else if (typeof (isoDateObject) == "number") {
    // millis elapsed
    return new Date(isoDateObject);
  }
  /** @type {string} */
  // @ts-ignore
  let isoDateStr = isoDateObject;
  var defaultToCurrentDate = toBoolean(defaultToCurrentDate, true);
  if (!isFilled(isoDateStr)) {
    return (defaultToCurrentDate ? new Date() : null);
  }

  if (isoDateStr.length > "2024-01-31T18:34".length) {
    if (!isoDateStr.endsWith("Z") &&
      !(isoDateStr.includes("+") || isoDateStr.substring("2024-01-31".length).includes("-"))) {
      isoDateStr += "Z"; // Force UTC as new Date does not
    }
    isoDateStr = isoDateStr.replace(" ", "T");
    let d = new Date(isoDateStr);
    if (d) {
      return d;
    }
  }

  //var index = isoDateStr.indexOf("Z");
  //if (index > 0) {
  //  isoDateStr = isoDateStr.substring(0, index) + "+00:00";
  //}
  var space = isoDateStr.indexOf(" ");
  if (space > 0)
    isoDateStr = isoDateStr.substring(0, space) + "T" + isoDateStr.substring(space + 1);

  // JS Parser funzt perfekt mit "2013-02-20T03:06:14.061Z" Format welches wir in GPSdata verwenden:
  // (aber ev. nicht mit anderen Formaten)
  //var millis = Date.parse(isoDateStr);
  //if (millis > 0) {
  //  //var millis = dateObj.getMilliseconds();
  //  return new Date(millis);
  //}

  var regexp = "([0-9]{4})(-([0-9]{2})(-([0-9]{2})" +
    "(T([0-9]{2}):([0-9]{2})(:([0-9]{2})(\.([0-9]+))?)?" +
    "(Z|(([-+])([0-9]{2}):([0-9]{2})))?)?)?)?";
  /** @type {any[]} */
  var d = isoDateStr.match(new RegExp(regexp));
  if (d == null)
    return (defaultToCurrentDate ? new Date() : null);

  var offset = 0;
  var date = new Date(d[1], 0, 1);

  if (d[3]) { date.setMonth(d[3] - 1); }
  if (d[5]) { date.setDate(d[5]); }
  if (d[7]) { date.setHours(d[7]); }
  if (d[8]) { date.setMinutes(d[8]); }
  if (d[10]) { date.setSeconds(d[10]); }
  // somehow those are missing d[12] contains  the millis but not the returned date:
  if (d[12]) { date.setMilliseconds(Number("0." + d[12]) * 1000); }
  if (d[14]) {
    offset = (Number(d[16]) * 60) + Number(d[17]);
    offset *= ((d[15] == '-') ? 1 : -1);
  }

  offset -= date.getTimezoneOffset();
  var time = (Number(date) + (offset * 60 * 1000));
  var date = new Date();
  date.setTime(Number(time));
  return date;
}

/**
 * Parse the given string and extract the time hh:mm:ss. 
 * @param {string} isoDateStr "2007-06-06T13:55:09Z" (Z stands for UTC==GMT)
 * @return {string} "13:55:09"
 */
export function getTimeStrFromIsoDateStr(isoDateStr) {
  if (!isFilled(isoDateStr))
    return "";
  var index = isoDateStr.indexOf(" ");
  if (index == -1)
    index = isoDateStr.indexOf("T");
  if (index == -1)
    return isoDateStr;
  var time = isoDateStr.substr(index + 1);
  return time.substr(0, 8);
}

/**
 * Parse the given string and extract the date YYYY-MM-DD. 
 * @param {string} isoDateStr "2007-06-06T13:55:09Z" (Z stands for UTC==GMT)
 * @return {string} "2007-06-06"
 */
export function getDateStrFromIsoDateStr(isoDateStr) {
  if (!isFilled(isoDateStr))
    return "";
  var index = isoDateStr.indexOf(" ");
  if (index == -1)
    index = isoDateStr.indexOf("T");
  if (index == -1)
    return isoDateStr;
  var time = isoDateStr.substr(0, index);
  return time;
}

/**
 * Convert Greenwich Mean Time to local time. 
 * @param {Date} gmt
 * @return {Date} local browser date
 * @deprecated Makes no sense
 */
export function getLocalDateFromGmt(gmt) {
  var da = gmt || new Date();
  /*
  There is always an offset and no GMT Date object
  var offsetMinutes = da.getTimezoneOffset();
  var millisGmt = da.getTime() - (60*1000*offsetMinutes);
  da.setTime(millisGmt); // now we are GMT
  */
  return da;
}

/**
 * Get a GMT Date instance or convert the given Date to GMT
 * @param {Date} da_ pass null (to get the current GMT time) or a local time
 * @return {Date} For Z=UTC==GMT
 */
export function getCurrentGmtDate(da_) {
  var da = da_ || new Date();
  /*
  There is always an offset and no GMT Date object
  var offsetMinutes = da.getTimezoneOffset();
  var millisGmt = da.getTime() + (60*1000*offsetMinutes);
  da.setTime(millisGmt); // now we are GMT
  var newOffset = da.getTimezoneOffset(); // should be 0 !!!!
  */
  return da;
}

/**
 * Offset to GMT in minutes.
 * The time-zone offset is the difference, in minutes, between UTC and local time.
 * Note that this means that the offset is positive if the local timezone is behind UTC
 * and negative if it is ahead.
 * For example, if your time zone is UTC+10 (Australian Eastern Standard Time), -600 will be returned.
 * Daylight savings time prevents this value from being a constant even for a given locale
 * @param {Date=} da A date can be given for performance reasons 
 * @return {number} +60 -> MEZ -> de_DE,fr_FR,...
 */
export function getTimezoneOffsetMinutes(da) {
  var d = da || new Date();
  // obtain local UTC offset in minutes
  var localOffset = d.getTimezoneOffset();
  return localOffset;
}

/**
 * Sydney standard timezone is 10 and DST is 11, New York standard timezone is -5 and DST is -4).
 * @param {Date} da
 * @return {number} Without daylightsaving
 */
export function stdTimezoneOffsetMinutes(da) {
  var d = da || new Date();
  var jan = new Date(d.getFullYear(), 0, 1);
  var jul = new Date(d.getFullYear(), 6, 1);
  // obtain local UTC offset in minutes
  return Math.max(jan.getTimezoneOffset(), jul.getTimezoneOffset());
}

export function getDaylightSavingDifferenceMinutes(da) {
  var d = da || new Date();
  var jan = new Date(d.getFullYear(), 0, 1);
  var jul = new Date(d.getFullYear(), 6, 1);
  // obtain local UTC offset in minutes
  return jan.getTimezoneOffset() - jul.getTimezoneOffset();
}

export function isDaylightSavingTime(da) {
  var d = da || new Date();
  // obtain local UTC offset in minutes
  return d.getTimezoneOffset() < stdTimezoneOffsetMinutes(d);
}

/**
 * Test with http://www.inter-locale.com/LocalesDemo.jsp
 * <p>
 * language: lower-case, two-letter codes as defined by ISO-639
 * <p>
 * country: upper-case, two-letter codes as defined by ISO-3166
 * @param {string} defaultLocale
 * @return {string} "de", "en", "en-US", "de-DE", "fr", "fr-FR", "es-ES", ...
 */
export function getLocaleId(defaultLocale = "") {
  var id = defaultLocale;
  if (navigator) {
    if (navigator.language) {
      id = navigator.language;
    }
    else if (navigator.browserLanguage) {
      id = navigator.browserLanguage;
    }
    else if (navigator.systemLanguage) {
      id = navigator.systemLanguage;
    }
    else if (navigator.userLanguage) {
      id = navigator.userLanguage;
    }
  }
  return id;
}

/**
 * Usefull to find translation key of a token
 * @param {string} localeId "en-US"
 * @return {string[]} [ "en_US", "en", "" ]
 */
export function getLocaleIdJavaArr(localeId) {
  var localeId = localeId || getLocaleId("");
  localeId = replaceAllTokens(localeId, "-", "_");
  var array = new Array();
  var index = localeId.indexOf("_");
  if (index != -1) {
    array.push(localeId); // "en_US"
    var lang = localeId.substring(0, index);
    array.push(lang); // "en"
  }
  else if (localeId.length > 1) {
    array.push(localeId); // "en"
  }
  array.push("");
  return array;
}

/**
 * Test with http://www.inter-locale.com/LocalesDemo.jsp
 * <p>
 * language: lower-case, two-letter codes as defined by ISO-639
 * <p>
 * country: upper-case, two-letter codes as defined by ISO-3166
 * <p>
 * This is not the Javascript form "en-US" but the Java form "en_US"
 * @return {string} "de" "en" or "en_US" "de_DE"
 * @see qx.locale.Manager.getInstance().getLocale() "en_US", Manager.getLanguage() returns "en" and Manager.getTerritory() returns "US"
 */
export function getLocaleIdJava() {
  var id = getLocaleId();
  id = replaceAllTokens(id, "-", "_");
  return id;
}

/**
 * Needs a pre-formated localeId e.g. the java notation
 * @param {string} localeId "de_DE"
 * @return {string} "de-DE"
 */
export function toLocaleIdJs(localeId) {
  if (localeId && localeId.includes("_")) {
    if (localeId.includes("-")) {
      var index = localeId.indexOf("-");
      localeId = localeId.substring(0, index);
    }
    return localeId.replace("_", "-");
  }
  return localeId;
}

/**
 * language: lower-case, two-letter codes as defined by ISO-639
 * without country
 * <p>
 * Caution: Does not switch if user changes language by clicking on flag
 * @param {string=} force_en_IfNotIn if not null e.g. "en,de,fr" if now a "jp" arrives we will return "en"
 * @return {string} "de" "en"
 */
export function getLocaleIdLanguage(force_en_IfNotIn = null) {
  var id = getLocaleId("en");
  var index = id.indexOf("-");
  if (index > 0) {
    id = id.substring(0, index);
  }
  if (force_en_IfNotIn != null && force_en_IfNotIn.length > 0) {
    var i = force_en_IfNotIn.indexOf(id);
    if (i == -1)
      id = "en";
  }
  return id;
}

/**
 * ISO 8601 date time formatting.
 * <br />
 * http://www.w3.org/TR/NOTE-datetime
 * @param {Date} [da_] a date object
 * @return {string} "2007-06-06"
 */
export function getDateStr(da_) {
  const da = da_ || new Date();
  const dy = da.getFullYear()   // Get full year (as opposed to last two digits only)
  const dm = da.getMonth() + 1  // Get month and correct it (getMonth() returns 0 to 11)
  const dd = da.getDate()    // Get date within month
  //  if ( dy < 1970 ) dy = dy + 100;  // We still have to fix the millenium bug
  const ys = new String(dy)  // Convert year, month and date to strings
  let ms = new String(dm)
  let ds = new String(dd)
  if (ms.length == 1) ms = "0" + ms;   // Add leading zeros to month and date if required
  if (ds.length == 1) ds = "0" + ds;
  const iso = ys + "-" + ms + "-" + ds;
  return iso;
}

/**
 * ISO 8601 date time formatting.
 * <br />
 * http://www.w3.org/TR/NOTE-datetime
 * @param {Date} [da_] an optional Date object, else current date is used
 * @param {boolean} [withSeconds] defaults to true
 * @param {boolean} [withMillis] defaults to false
 * @return {string} "13:55:09"
 */
export function getTimeStr(da_, withSeconds, withMillis) {
  var da = da_ || new Date();
  var withSeconds = toBoolean(withSeconds, true);
  var withMillis = toBoolean(withMillis, false);
  var hourS = new String(da.getHours());
  var minS = new String(da.getMinutes());
  var secS = new String(da.getSeconds());
  var iso = "";
  if (hourS.length == 1) iso += "0";
  iso += da.getHours();
  iso += ":";
  if (minS.length == 1) iso += "0";
  iso += da.getMinutes();
  if (withSeconds) {
    iso += ":";
    if (secS.length == 1) iso += "0";
    iso += da.getSeconds();
  }
  if (withMillis) {
    iso += ".";
    iso += da.getMilliseconds();
  }
  return iso;
}

/**
 * ISO 8601 date time formatting for GMT. 
 * <br />
 * http://www.w3.org/TR/NOTE-datetime
 * @param {Date} [da_] pass null (to get the current GMT time) or a Date object
 * @param {boolean} [withMillis] Append milli-seconds like ".325", defaults to false
 * @param {boolean} [withT] default is to use T as a date time separator
 * @return {string} "2007-06-06T13:55:09Z" (Z stands for UTC==GMT)
 */
export function getCurrentIsoGmtTimestampStr(da_, withMillis, withT) {
  var withT = toBoolean(withT, true);
  var da = da_ || new Date();
  /* Not needed as we already use the getUTC*() methods!
  //move the Date to the GMT with da.getTimezoneOffset()
  //convert to msec since Jan 1 1970
  var localTime = da.getTime();
  //obtain local UTC offset and convert to msec
  var localOffset = da.getTimezoneOffset() * 60000;
  //obtain UTC time in msec
  var utc = localTime + localOffset;
  //convert msec value to date string
  da = new Date(utc); 
  //var millis = Date.valueOf();
  */
  var dy = da.getUTCFullYear()   // Get full year (as opposed to last two digits only)
  var dm = da.getUTCMonth() + 1  // Get month and correct it (getMonth() returns 0 to 11)
  var dd = da.getUTCDate()    // Get date within month
  //if ( dy < 1970 ) dy = dy + 100;  // We still have to fix the millenium bug
  var ys = new String(dy)  // Convert year, month and date to strings
  var ms = new String(dm)
  var ds = new String(dd)
  if (ms.length == 1) ms = "0" + ms;   // Add leading zeros to month and date if required
  if (ds.length == 1) ds = "0" + ds;
  var iso = ys + "-" + ms + "-" + ds;

  var hourS = new String(da.getUTCHours());
  var minS = new String(da.getUTCMinutes());
  var secS = new String(da.getUTCSeconds());
  iso += (withT ? "T" : " ");
  if (hourS.length == 1) iso += "0";
  iso += da.getUTCHours();
  iso += ":";
  if (minS.length == 1) iso += "0";
  iso += da.getUTCMinutes();
  iso += ":";
  if (secS.length == 1) iso += "0";
  iso += da.getUTCSeconds();
  if (toBoolean(withMillis, false))
    iso += "." + da.getUTCMilliseconds();
  iso += "Z";
  return iso;
}

/**
 * Useful for file names, does not contain TZ!
 * @return {string} "2012-12-26T17_07_34"
 */
export function getCurrentIsoLocalTimestampStrForFileName() {
  var iso = getCurrentIsoLocalTimestampStr(null, false, true, false);
  iso = replaceAllTokens(iso, ":", "_");
  return iso;
}

/**
 * ISO 8601 date time formatting for the local time
 * <br />
 * http://www.w3.org/TR/NOTE-datetime
 * http://de.wikipedia.org/wiki/ISO_8601
 * @param {Date} [da_] pass null (to get the current local time) or a Date instance
 * @param {boolean} [withMillis] Append milli-seconds like ".325", defaults to false
 * @param {boolean} [withT] default is to use T as a date time separator
 * @param {boolean} [withTzPostfix] default is to append 'Z' for zulu/UTC/GMT or for example +02:00
 * @return {string} "2007-06-06T13:55:09-60" (-60==MEZ; Z stands for UTC==GMT)
 * <pre>
 * 2009-01-01T12:00:00+01:00      12:00:00 Uhr am 1. Januar 2009 in Wien (MEZ)
 * 2009-06-30T18:30:00+02:00       18:30:00 Uhr am 30. Juni 2009 in Wien (MESZ - Sommerzeit)
 * </pre>
 */
export function getCurrentIsoLocalTimestampStr(da_, withMillis, withT, withTzPostfix) {
  var withT = toBoolean(withT, true);
  var withTzPostfix = toBoolean(withTzPostfix, true);
  var da = da_ || new Date();
  //var millisGmt = da.getTime() + (60*1000*da.getTimezoneOffset())
  //da.setTime(millisGmt); // now we are GMT

  var millis = Date.valueOf();
  var dy = da.getFullYear()   // Get full year (as opposed to last two digits only)
  var dm = da.getMonth() + 1  // Get month and correct it (getMonth() returns 0 to 11)
  var dd = da.getDate()    // Get date within month
  //if ( dy < 1970 ) dy = dy + 100;  // We still have to fix the millenium bug
  var ys = new String(dy)  // Convert year, month and date to strings
  var ms = new String(dm)
  var ds = new String(dd)
  if (ms.length == 1) ms = "0" + ms;   // Add leading zeros to month and date if required
  if (ds.length == 1) ds = "0" + ds;
  var iso = ys + "-" + ms + "-" + ds;

  var hourS = new String(da.getHours());
  var minS = new String(da.getMinutes());
  var secS = new String(da.getSeconds());
  iso += (withT ? "T" : " ");
  if (hourS.length == 1) iso += "0";
  iso += da.getHours();
  iso += ":";
  if (minS.length == 1) iso += "0";
  iso += da.getMinutes();
  iso += ":";
  if (secS.length == 1) iso += "0";
  iso += da.getSeconds();
  if (toBoolean(withMillis, false))
    iso += "." + da.getUTCMilliseconds();
  if (withTzPostfix) {
    // getTimezoneOffsetMinutes(): Number +60 -> MEZ -> de_DE,fr_FR,... obtain local UTC offset in minutes
    // TZD  = time zone designator (Z or +hh:mm or -hh:mm)
    // The time-zone offset is the difference, in minutes, between UTC and local time.
    // Note that this means that the offset is positive if the local timezone is behind UTC
    // and negative if it is ahead.
    // For example, if your time zone is UTC+10 (Australian Eastern Standard Time), -600 will be returned.
    // Daylight savings time prevents this value from being a constant even for a given locale
    var localOffset = da.getTimezoneOffset();
    if (localOffset == 0) {
      iso += "Z";
      return iso;
    }
    localOffset *= -1;
    var minutes = Math.abs(localOffset) % 60;
    let minutesStr = "";
    if (minutes == 0) {
      minutesStr = "00";
    }
    else if (minutes < 10) {
      minutesStr = "0" + ("" + minutes);
    }
    var hour = Math.abs(localOffset) / 60;
    let hourStr = "";
    if (hour == 0) {
      hourStr = (localOffset > 0) ? "+00" : "-00";
    }
    else if (hour < 10) {
      hourStr = ((localOffset > 0) ? "+0" : "-0") + hour;
    }
    else {
      hourStr = ((localOffset > 0) ? "+" : "-") + hour;
    }
    iso += hourStr + ":" + minutesStr;
  }
  return iso;
}

/**
 * Helper to test equality.
 *   new Number(1) == new Number(1)
 *   1 == new Number(1)
 *   "A" == new String("A")
 * evaluates to false
 * but this equals() functions return true!
 *   "" == null will return false here.
 * @param {string} str1
 * @param {string} str2
 * @return {boolean} true or false
 */
export function equals(str1, str2) {
  if (!str1 && str2 || str1 && !str2) return false;
  return "" + str1 === "" + str2;
  /*
  if (!str1 && !str2) return true;
  if (!str1) return false;
  if (!str2) return false;
  var str1_ = (str1 instanceof String) ? str1.valueOf() : str1;
  var str2_ = (str2 instanceof String) ? str2.valueOf() : str2;
  return str1_ == str2_;
  */
}

/**
 * Replace a token in a string
 * @param {object} s  string to be processed
 * @param {string} t  token to be found and removed
 * @param {string} u  token to be inserted
 * @return {object}  new string or s if not of type string
 * 
 * since August 2020 modern Browsers have String.replaceAll function
 * https://stackoverflow.com/questions/1144783/how-to-replace-all-occurrences-of-a-string?
 * https://flaviocopes.com/how-to-replace-all-occurrences-string-javascript/
 * https://khef.co/2019-javascript-string-replace-regex-vs-substring/
 */
export function replaceAllTokens(s, t, u) {
  if (typeof s == 'undefined' || s == null) {
    return null;
  }
  if (typeof s != "string") {
    return s;
  }
  var i = s.indexOf(t);
  //var r = "";
  if (i == -1) return s;
  /* //may reach call stack size limit on mobile browsers
  r += s.substring(0,i) + u;
  if ( i + t.length < s.length)
    r += replaceAllTokens(s.substring(i + t.length, s.length), t, u);
  return r;
  */
  if (s.replaceAll) { //available on String since August 2020
    return s.replaceAll(t, u); //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/replaceAll
  } else {
    return s.split(t).join(u); //for Chrome<85, Edge<85, FF<77, FF Android<79, Safari < 13.1, Safari iOs < 13.4, IE
  }
}

/**
 * Replace XML restricted chars in attributes
 * TODO: currently still as for tag content
 */
//was defined twice, last one one
//export function escapeXmlAttr(text) {
//  return escapeXml(text);
//}

/**
 * Port xmlBlaster.js to NOT use "&#034;" but use "" enclosure: &apos;
 * flagCsv = ReplaceVariable.replaceAll(flagCsv, "&#034;", "\"");
 * See PoiConfig.java
 * @param {string} text
 * @param {boolean} escapeApos true for &#034; -> " etc escapes
 * @return {string}
 */
export function escapeCsvValue(text, escapeApos, separator) {
  var separator = separator || ",";
  if (typeof text == "undefined")
    text = null;
  if (text == null || typeof text != "string" || text.length == 0)
    return text;
  if (separator == ",") {
    text = replaceAllTokens(text, ",", "&comma;");
  }
  var escapeApos = (escapeApos === undefined || escapeApos == null) ? false : escapeApos;
  if (escapeApos) {
    text = replaceAllTokens(text, "\"", "&#034;");
    //needed?
    //text = replaceAllTokens(text, "'", "&#039;");
  }
  if (separator == ";") {
    text = replaceAllTokens(text, ";", "%3B");
  }
  if (separator == "|") {
    text = replaceAllTokens(text, "|", "%7C");
  }
  return text;
}

/**
 * Assuming "," as separator of the original csv
 * @param {string} text "You&comma;and me"
 * @return {string} "You, and me"
 */
export function unescapeCsvValue(text, escapeApos, separator) {
  /*
  XmlBuffer.java
  private static final char[] AMP = "&amp;".toCharArray();
  private static final char[] LT = "&lt;".toCharArray();
  private static final char[] GT = "&gt;".toCharArray();
  private static final char[] QUOT = "&quot;".toCharArray();// '"'
  private static final char[] APOS = "&apos;".toCharArray();//'\''
  private static final char[] SLASH_R = "&#x0D;".toCharArray();
  private static final char[] NULL = "&#x0;".toCharArray();
  */
  if (text == null || text.length == 0) {
    return text;
  }

  if (escapeApos && text.includes('\\"')) {
    text = replaceAllTokens(text, '\\"', '"');
  }

  if (!text.includes("%") && !text.includes("&")) {
    return text;
  }

  separator = separator || ",";
  if (separator == ",") {
    text = replaceAllTokens(text, "&comma;", ",");
  }
  else if (separator == ";") {
    text = replaceAllTokens(text, "%3B", ";");
  }
  else if (separator == "|") {
    text = replaceAllTokens(text, "%7C", "|");
  }
  text = replaceAllTokens(text, "&amp;", '&');
  var escapeApos = (escapeApos === undefined || escapeApos == null) ? false : escapeApos;
  if (escapeApos && text.indexOf("&#") != -1) {
    text = replaceAllTokens(text, "&#034;", "\"");
    text = replaceAllTokens(text, "&#039;", "'");
  }
  if (escapeApos) {
    text = replaceAllTokens(text, "&quot;", '"');
    text = replaceAllTokens(text, "&apos;", "'");
  }
  return text;
}

//https://stackoverflow.com/questions/376373/pretty-printing-xml-with-javascript
export function prettifyXml(sourceXml) {

  //https://stackoverflow.com/a/49458964
  function formatXml(xml, tab) { // tab = optional indent value, default is tab (\t)
    var formatted = '', indent = '';
    tab = tab || '\t';
    xml.split(/>\s*</).forEach(function (node) {
      if (node.match(/^\/\w/)) indent = indent.substring(tab.length); // decrease indent by one 'tab'
      formatted += indent + '<' + node + '>\r\n';
      if (node.match(/^<?\w[^>]*[^\/]$/)) indent += tab;              // increase indent
    });
    return formatted.substring(1, formatted.length - 3);
  }

  return formatXml(sourceXml, "  "); //works in chrome and firefox
}

/**
 * Replace XML restricted chars so that text can be used for XML
 * @param {string} text e.g. <a>&</a>
 * @return {string} e.g. <a>&amp;</a>
 */
export function escapeXml(text) {
  if (!isDefined(text)) {
    return "";
  }
  if (!isDefined(text.length)) {
    return text; // e.g. if of type "Number"
  }
  var length = text.length;
  var ret = "";
  var i;
  for (i = 0; i < length; i++) {
    var c = text.charAt(i);
    switch (c) {
      case '\0':
        ret += "&#x0;";
        break;
      case '&':
        ret += "&amp;";
        break;
      case '<':
        ret += "&lt;";
        break;
      case '>':
        ret += "&gt;";
        break;
      case '"':
        ret += "&quot;";
        break;
      case '\'':
        ret += "&apos;";
        break;
      case '\r':
        ret += "&#x0D;";
        break;
      default:
        ret += c;
    }
  }
  return ret;
}
/**
 * Is this OK for xml attributes?
 * @param {} text
 * @return {string}
 */
export function escapeXmlAttr(text) {
  if (!isDefined(text)) return "";
  if (!isDefined(text.length)) {
    return text; // e.g. if of type "Number"
  }
  var length = text.length;
  var ret = "";
  var i;
  for (i = 0; i < length; i++) {
    var c = text.charAt(i);
    switch (c) {
      case '\0':
        ret += "&#x0;";
        break;
      case '&':
        ret += "&amp;";
        break;
      case '"':
        ret += "&quot;";
        break;
      case '\'':
        ret += "&apos;";
        break;
      case '\r':
        ret += "&#x0D;";
        break;
      default:
        ret += c;
    }
  }
  return ret;
}


/**
 * Find the given tag from the given xml string and return its value.
 * Does not work if the tag exists multiple time or occures somewhere else in the text
 * @param {string} xml
 * @param {string} tag For example "nodeId" for a tag &lt;nodeId>value&lt;/nodeId>
 * @param {string} defaultValue
 * @return defaultValue if none is found
 */
export function extractFromTag(xml, tag, defaultValue = null) {
  if (!xml || !tag) return defaultValue;
  var startToken = "<" + tag + ">";
  var endToken = "</" + tag + ">";
  var start = xml.indexOf(startToken);
  var end = xml.indexOf(endToken);
  if (start != -1 && end != -1) {
    return xml.substring(start + startToken.length, end);
  }
  return defaultValue;
}

/**
 * Extracted quoted token in text. 
 * @param {string} text "A nice 'test' to do"
 * @param {string} quoteChar '
 * @param {string} defaultValue to use if not found
 * @param {number} fromIndex start lookup at index in text
 * @return {string} test
 */
export function extractQuoted(text, quoteChar, defaultValue, fromIndex = 0) {
  var fromIndex = toNumber(fromIndex, 0);
  if (text == null || quoteChar == null) return defaultValue;
  var start = text.indexOf(quoteChar, fromIndex);
  var end = text.indexOf(quoteChar, start + 1);
  if (start != -1 && end != -1) {
    return text.substring(start + quoteChar.length, end);
  }
  return defaultValue;
}


/**
 * Parse a Number from a tag content. 
 * @param {Node} node W3C DOM Node
 * @param {number|?} defaultVal The fallback (default itself to 0)
 * @return {number} an empty tag return defaultVal and if not given returns 0
 */
export function collectNumberFromNodeChilds(node, defaultVal = 0) {
  defaultVal = toNumber(defaultVal, 0);
  if (!isDefined(node))
    return defaultVal;
  if (!isDefined(node.childNodes))
    return defaultVal;
  var val = collectTextFromNodeChilds(node);
  val = trim(val);
  return toNumber(val, defaultVal);
}

/**
 * Parse a bool from a tag content. 
 * @param {Node} node W3C DOM Node
 * @param {boolean|?} defaultVal The fallback (default itself to false)
 * @return {boolean} an empty tag like &lt;persistent/> returns true
 */
export function collectBooleanFromNodeChilds(node, defaultVal = false) {
  defaultVal = toBoolean(defaultVal, false);
  if (!isDefined(node))
    return defaultVal;
  if (!isDefined(node.childNodes))
    return defaultVal;
  var val = collectTextFromNodeChilds(node);
  val = trim(val);
  if (val == "") return true; // <persistent/> empty tag returns true!!
  return toBoolean(val, defaultVal);
}

/**
 * Collect the text of the given node.
 * <p>
 * We need to do this as firefox stores max 4096 bytes
 * in the node.firstChild.nodeValue, the other bytes
 * are chunks in follow up nodes.
 * <p> 
 * If it is a CDATA section all children are collected!
 * @param {Node} node W3C DOM Node
 * @param {string|?} defaultVal The fallback
 * @return {string} the #text between the tag, never null but "" if nothing found and no defaultVal
 * @see Testsuite domStringLenTests.html 
 */
export function collectTextFromNodeChilds(node, defaultVal = "") {
  var text = (isDefined(defaultVal)) ? defaultVal : "";
  if (!isDefined(node))
    return text;
  if (!isDefined(node.childNodes))
    return text;
  for (var i = 0, l = node.childNodes.length; i < l; i++) {
    var n = node.childNodes[i];
    var value = n.nodeValue;
    if (value == null)
      value = n.text; // IE7 had n.nodeValue=null and n.text="k320i"
    if (i == 0) {
      // Try performance by avoiding indexOf looping over 
      if (org.xmlBlaster.startsWith(trimFront(value), "<![CDATA[")) {
        // Inside CDATA: recursively collect all childs
        return serializeNode(node.childNodes[1]);
      }
    }
    text += value; //text += n.textContent;
  }
  return text;
}

/**
 * Serialize a DOM to xml String
 * @param {Node} node The XML W3C node with children tree:
 * interface Document : Node
 * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
 * @return {string}
 */
export function serializeNode(node) {
  /* TODO DOM3
  * if (document.implementation && document.implementation.hasFeature &&
   document.implementation.hasFeature('LS', '3.0')) {
      // Browser supports DOM 3 load and save
      var output = document.implementation.createLSOutput();
      output.encoding = "UTF-8";
      try {
             var serialize_result = serializer.write(doc, output);
      } catch (e) {
             alert('serializer.write: Fehlercode ' + e.code);
      }
      if (serialize_result)
             alert(output.characterStream);
  */

  if (!isDefined(node))
    return "";
  if (typeof XMLSerializer != 'undefined') { // Firefox, Opera
    return new XMLSerializer().serializeToString(node);
  } else {
    throw "Browser does not support to serialize xml node";
  }
}


/**
 * Is called to parse &lt;key> or &lt;content> or &lt;qos>, or multiple &lt;qos>.
 * <p/>
 * Note: Supported parsing of content is
 * <pre> 
 * 1)  &lt;! [ CDATA[&lt;s>...] ] &gt;
 * 2)  base64
 * 3)  xml escaped
 * <pre> 
 * @param {Document} domDocument The complete XML W3C DOM tree:
 * interface Document : Node
 * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
 * @param {Node} currentNode W3C node, the msgUnit root node lile <update>
 * @param methodName e.g. "update"
 * @return {MsgUnit[]} Array with ONE {@link MsgUnit} instance or empty array on error
 *         For 'unSubscribe' and 'erase' there may be more than one {@link MsgUnit} instance
 */
export function parseMsgUnit(domDocument, currentNode, methodName) {
  var msgUnit = new MsgUnit(methodName);
  var msgUnits = new Array();
  for (var i = 0; i < currentNode.childNodes.length; i++) {
    var node = currentNode.childNodes[i];
    if (node.nodeType != Node.ELEMENT_NODE) {
      var val = isDefined(node.nodeValue) ? node.nodeValue : "";
      var tmp = trim(val);
      if (tmp.length == 0) continue;
      val = ": '" + val + "'";
      var txt = "parseMsgUnit(): Ignoring xmlBlaster response node " + getXPathLocation(node, true) + val;
      (node.nodeType == Node.COMMENT_NODE || node.nodeType == Node.TEXT_NODE) ? log.debug(ME1 + txt) : log.warn(ME1 + txt);
      continue;
    }
    //if (!node.firstChild) {
    //   var val = (node.nodeValue!=undefined&&node.nodeValue!=null) ? (": "+node.nodeValue) : "";
    //   log.warn(ME1+"parseMsgUnit(): node has no child: " + getXPathLocation(node, true) + val);
    //   continue;
    //}
    switch (node.nodeName) {
      case "sessionId":
        msgUnit.sessionId = collectTextFromNodeChilds(node);
        break;
      case "qos":
        var hasParsedQos = isDefined(msgUnit.qosData);
        if (hasParsedQos) {
          msgUnit = new MsgUnit(methodName);
        }
        if (methodName == "connect")
          msgUnit.qosData = new ConnectQosData(domDocument, node);
        else // "update", "erase", etc
          msgUnit.qosData = QosData.parse(domDocument, node);
        msgUnits.push(msgUnit);
        break;
      case "key":
        msgUnit.keyData = KeyData.parse(domDocument, node);
        break;
      case "content":
        // Content can be base64 encoded, with similar markup as clientProperty:
        var cp = new ClientProperty('content');
        if (node.getAttribute('type') != null) cp.type = node.getAttribute('type');
        if (node.getAttribute('encoding') != null) cp.encoding = node.getAttribute('encoding');
        if (node.getAttribute('charset') != null) cp.charset = node.getAttribute('charset');
        cp.value = collectTextFromNodeChilds(node);
        msgUnit.content = cp.getValue();
        break;
      default:
        log.warn(ME1 + "parseMsgUnit(): Ignoring xmlBlaster response tag " + getXPathLocation(node, true));
        break;
    }
  }
  if (msgUnits.length == 0) msgUnits.push(msgUnit); // an empty <update></update> markup for example
  return msgUnits;
}

/**
 * Get nice readable DOM node types
 * @param {number} nodeType The W3C NodeType interface
 * @returns {string}
 */
export function getNodeTypeName(nodeType) {
  switch (nodeType) {
    case Node.ELEMENT_NODE: return "ELEMENT_NODE"; // 1
    case Node.ATTRIBUTE_NODE: return "ATTRIBUTE_NODE"; // 2
    case Node.TEXT_NODE: return "TEXT_NODE"; // 3
    case Node.CDATA_SECTION_NODE: return "CDATA_SECTION_NODE"; // 4
    case Node.ENTITY_REFERENCE_NODE: return "ENTITY_REFERENCE_NODE"; // 5
    case Node.ENTITY_NODE: return "ENTITY_NODE"; // 6
    case Node.PROCESSING_INSTRUCTION_NODE: return "PROCESSING_INSTRUCTION_NODE"; // 7
    case Node.COMMENT_NODE: return "COMMENT_NODE"; // 8
    case Node.DOCUMENT_NODE: return "DOCUMENT_NODE"; // 9
    case Node.DOCUMENT_TYPE_NODE: return "DOCUMENT_TYPE_NODE"; // 10
    case Node.DOCUMENT_FRAGMENT_NODE: return "DOCUMENT_FRAGMENT_NODE"; // 11
    case Node.NOTATION_NODE: return "NOTATION_NODE"; // 12
    default: return nodeType;
  }
}

/**
 * @param {Node} node A current DOM node object
 * @param {boolean} addTypeInfo if true the type of node is appended
 * @return {string} e.g. "/#document/xmlBlasterResponse/qos/#comment"
 */
export function getXPathLocation(node, addTypeInfo = false) {
  if (node == null) return "";
  var origNode = node;
  var text = node.nodeName;
  while (node.parentNode) {
    node = node.parentNode;
    if (node.nodeType == Node.DOCUMENT_NODE ||
      node.nodeType == Node.DOCUMENT_TYPE_NODE ||
      node.nodeType == Node.DOCUMENT_FRAGMENT_NODE) break;
    text = node.nodeName + /*"-" + node.nodeType + */ "/" + text;
  }
  if (addTypeInfo && addTypeInfo == true)
    text += "-" + getNodeTypeName(origNode.nodeType) + ":" + origNode.nodeType;
  text = "/" + text;
  return text;
}

/**
 * Eg PublishReturnQos
 * @param {string} returnQosXml
 * @return {MsgUnit} or null
 */
export function parseXmlBlasterResponseQos(returnQosXml) {
  var domDocument = getDOMDocument(returnQosXml);
  var msgUnitArr = parseXmlBlasterResponse(domDocument);
  if (msgUnitArr == null || msgUnitArr.length < 1) {
    return null;
  }
  return msgUnitArr[0];
}

/**
 * Entry point for xmlBlaster returned scripting language.
 * <p />
 * Usually as returned by Ajax, example:
 * <pre>
 * &lt;xmlBlasterResponse>
 *  &lt;update>
 *    &lt;qos>...&lt;/qos>
 *    &lt;key>...&lt;/key>
 *    &lt;content>...&lt;/content>
 *  &lt;/update>
 * &lt;/xmlBlasterResponse>
 * </pre>
 * @param {Document} domDocument The complete XML W3C DOM tree:
 * interface Document : Node
 * As returned by Ajax or by getDOMDocument(xmlString) function
 * see http://www.w3.org/TR/DOM-Level-2-Core/core.html#i-Document
 * @return {MsgUnit[]} containing {@link MsgUnit} instances
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html
 */
export function parseXmlBlasterResponse(domDocument) {
  var rootNode = domDocument.documentElement;
  var msgUnits = new Array();
  if (!isDefined(rootNode)) {
    log.error(ME1 + " parseXmlBlasterResponse rootNode==null");
    return msgUnits;
  }

  if (rootNode.nodeName == "parsererror") {
    var val = isDefined(rootNode.nodeValue) ? (": " + rootNode.nodeValue) : "";
    var val2 = (rootNode.childNodes.length > 0 && isDefined(rootNode.childNodes[0].nodeValue)) ? (": " + rootNode.childNodes[0].nodeValue) : "";
    var txt = "parseXmlBlasterResponse(): Illegal xmlBlaster response (" + rootNode.nodeName + " type=" + getNodeTypeName(rootNode.nodeType) + val + val2;
    log.error(ME1 + txt);
    //alert(txt);
    return msgUnits;
    /*
    20:26:24 DEBUG - parsing /parsererror/#text type=3
    20:26:24 WARN - parseXmlBlasterResponse(): Ignoring xmlBlaster response node #text of type 3:
                     XML-Verarbeitungsfehler: nicht wohlgeformt Adresse: file:///home/xmlblast/jsunit/testRunner.html Zeile Nr. 1, Spalte 280:
    20:26:24 DEBUG - parsing /parsererror/sourcetext type=1
    20:26:24 WARN - Ignoring xmlBlaster response tag sourcetext
    */
  }
  /*
  TODO: Handle such errors:
  "<html><head><title>Apache Tomcat/5.5.15 - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1>HTTP Status 404 - /xmlBlaster/ajax</h1><HR size=\"1\" noshade=\"noshade\"><p><b>type</b> Status report</p><p><b>message</b> <u>/xmlBlaster/ajax</u></p><p><b>description</b> <u>The requested resource (/xmlBlaster/ajax) is not available.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>Apache Tomcat/5.5.15</h3></body></html>"
  */

  for (let i = 0; i < rootNode.childNodes.length; i++) {
    const node = rootNode.childNodes[i];
    log.debug(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node, true) + "): parsing now ...");
    if (node.nodeType != Node.ELEMENT_NODE) {
      var val = isDefined(node.nodeValue) ? node.nodeValue : "";
      var tmp = trim(val);
      if (tmp.length == 0) continue;
      val = ": '" + val + "'";
      var txt = "parseXmlBlasterResponse(): Ignoring xmlBlaster response node " + getXPathLocation(node, true) + val;
      (node.nodeType == Node.COMMENT_NODE || node.nodeType == Node.TEXT_NODE) ? log.debug(ME1 + txt) : log.warn(ME1 + txt);
      continue;
    }
    let msgUnitArr = null;
    switch (node.nodeName) {
      case "exception": // <exception errorCode='user.notConnected'>...<message><![CDATA[#16983M Connection is lost, please login again]]></message>
        /*<xmlBlasterResponse>
            <exception errorCode='user.configuration.identicalClient'>
            <isServerSide>true</isServerSide>
            <message><![CDATA[#16409M You are not allowed to login with the cluster node name /node/dev/client/joe, access denied.]]></message>
            <node>dev</node>
            <location>Authorize</location>
            <lang>en</lang>
            ...
            <embeddedMessage><![CDATA[Original erroCode=user.security.authentication.accessDenied]]></embeddedMessage>
          </exception>
         </xmlBlasterResponse>
                    */
        var msgUnit = new MsgUnit(node.nodeName);
        msgUnit.xmlBlasterException = new XmlBlasterException();
        var ex = msgUnit.xmlBlasterException;
        ex.setTomcatServerSide(true);
        if (node.getAttribute('errorCode') != null) ex.errorCodeStr = node.getAttribute('errorCode');
        var nodeList = node.getElementsByTagName("message");
        if (nodeList != null && nodeList.length > 0 && nodeList[0].firstChild)
          ex.setMessage(nodeList[0].firstChild.nodeValue);
        var nodeList = node.getElementsByTagName("embeddedMessage");
        if (nodeList != null && nodeList.length > 0 && nodeList[0].firstChild)
          ex.setEmbeddedMessage(nodeList[0].firstChild.nodeValue);
        var nodeList = node.getElementsByTagName("location");
        if (nodeList != null && nodeList.length > 0 && nodeList[0].firstChild)
          ex.setLocation(nodeList[0].firstChild.nodeValue);
        var nodeList = node.getElementsByTagName("isServerSide");
        if (nodeList != null && nodeList.length > 0 && nodeList[0].firstChild) {
          var isServerSide = parseBoolean(nodeList[0].firstChild.nodeValue, true);
          if (isServerSide == true)
            ex.setXmlBlasterServerSide(true);
          else
            ex.setTomcatServerSide(true);
        }
        msgUnits.push(msgUnit);
        break;
      case "update":  // has <qos>, <content>, <key> tag
      case "publish":
      case "subscribe":
      case "connect":
        msgUnitArr = parseMsgUnit(domDocument, node, node.nodeName);
        if (msgUnitArr.length > 0) {
          //if (MapObject == log.getLevel())
          //  log.debug(ME1+"parseXmlBlasterResponse() Parsed: " + msgUnitArr[0].dump());
          msgUnits.push(msgUnitArr[0]);
        }
        else
          log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node, true) + "): has empty MsgUnit");
        break;
      case "unSubscribe": // has multiple <qos> tags
      case "erase":
        msgUnitArr = parseMsgUnit(domDocument, node, node.nodeName);
        for (var jj = 0; jj < msgUnitArr.length; jj++) {
          log.debug(ME1 + "Parsed: " + msgUnitArr[jj].dump());
          msgUnits.push(msgUnitArr[jj]);
        }
        if (msgUnitArr.length == 0)
          log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node, true) + "): has empty MsgUnit");
        break;
      case "get":  // has NO <message> grouping tag get(device.xxx.json) response 2011-02-04:  "<xmlBlasterResponse> <get>  <qos>  <sender>
        var foundMessage = false;
        for (var j = 0; j < node.childNodes.length; j++) {
          if (node.childNodes[j].nodeName == "message") {
            foundMessage = true;
            break;
          }
        }
        if (!foundMessage) {
          msgUnitArr = parseMsgUnit(domDocument, node, node.nodeName);
          if (msgUnitArr.length > 0) {
            log.debug("Parsed: " + msgUnitArr[0].dump());
            msgUnits.push(msgUnitArr[0]);
          }
          else {
            log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node.childNodes[j]) + "): has empty MsgUnit");
          }
          break;
        }
      // else falling through to next "get", it is not known if this case exists (2011-02-04)
      //case "get":  // has a <message> grouping tag ???
      case "publishArr":
        for (var j = 0; j < node.childNodes.length; j++) {
          if (node.childNodes[j].nodeName != "message") {
            log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node.childNodes[j], true) + "): Ignoring xmlBlaster response tag");
            continue;
          }
          msgUnitArr = parseMsgUnit(domDocument, node.childNodes[j], node.nodeName);
          if (msgUnitArr.length > 0) {
            log.debug("Parsed: " + msgUnitArr[0].dump());
            msgUnits.push(msgUnitArr[0]);
          }
          else
            log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node.childNodes[j]) + "): has empty MsgUnit");
        }
        break;
      case "disconnect":
        // "<xmlBlasterResponse><disconnect>true</disconnect></xmlBlasterResponse>"
        msgUnitArr = parseMsgUnit(domDocument, node, node.nodeName);
        for (var jj = 0; jj < msgUnitArr.length; jj++) {
          log.debug(ME1 + "Parsed: " + msgUnitArr[jj].dump());
          msgUnits.push(msgUnitArr[jj]);
        }
        if (msgUnitArr.length == 0)
          log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node, true) + "): has empty MsgUnit");
        break;
      default:
        log.warn(ME1 + "parseXmlBlasterResponse(" + getXPathLocation(node, true) + "): Ignoring xmlBlaster response tag");
        break;
    }
  }
  return msgUnits;
}


/**
 * leading whitespaces
 * @param {string} value
 * @returns {string}
 */
export function trimFront(value) {
  if (value == undefined || value == null) {
    return null;
  }
  for (var i = 0, l = value.length; i < l; i++) {
    var c = value[i];
    if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\v' || c == '\f') {
      continue;
    }
    if (i == 0) {
      return value;
    }
    return value.substring(i);
  }
  return "";
  //if (!isDefined(value)) return null;
  // Caution: Fails for huge strings (>2-3MB) because if too deep recursion
  //var re = /\s*((\S+\s*)*)/;
  //return value.replace(re, "$1");
}

/**
 * ending whitespaces
 * Caution: Fails for huge strings (>2-3MB) because if too deep recursion
 * @param {string} value
 * @returns {string}
 */
export function trimEnd(value) {
  if (value == undefined || value == null) return null;
  //if (!isDefined(value)) return null;
  var re = /((\s*\S+)*)\s*/;
  return value.replace(re, "$1");
}

/** 
 * trims whitespaces
 * @param {string} value, if null|undefined null is returned
 * @return {string} never undefined
 */
export function trim(value = null) {
  if (value == null) return null;
  if (value.trim) {
    return value.trim(); // Works fine for huge strings
  }
  //if (!isDefined(value)) return null;
  return trimFront(trimEnd(value));
}

/**
 * Convert a native array to a csv string. 
 * @param {Array} arr array of data
 * @param {string|?} seperator, defaults to ","
 * @return never null, e.g. "blue,red,black"
 */
export function arrayToCsv(arr, seperator) {
  if (arr === undefined)
    return "";
  if (seperator === undefined || seperator == null || seperator.length < 1)
    seperator = ",";
  var csv = "";
  for (var i = 0, l = arr.length; i < l; i++) {
    if (i > 0)
      csv += seperator;
    csv += arr[i];
  }
  return csv;
}
