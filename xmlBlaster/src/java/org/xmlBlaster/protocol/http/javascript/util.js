/*------------------------------------------------------------------------------
Name:      util.js
Project:   xmlBlaster.org
Comment:   Implementing some Javascript utility objects
           - a Logging class, showing the log output in a separate window
           - browser detection
Author:    xmlBlaster@marcelruff.info konrad.krafft@doubleslash.de
------------------------------------------------------------------------------*/


/**
 * Check the browser version
 */
var isNetscape;
var isExplorer;
if (navigator.appVersion.substring(0,1) < "4") {
   alert("Please use a browser version higher than 4.x\n Let's try it nevertheless.");
   isNetscape = (navigator.appName.indexOf("Netscape") != -1);
   isExplorer = (navigator.appName.indexOf("Microsoft") != -1);
}

/**
 * Get some browser infos formatted as HTML
 */
function getBrowserInfo()
{
   var appName     = "Browser name: <i>" + navigator.appName + "</i><br>";
   var appVersion  = "Browser version: <i>" + navigator.appVersion + "</i><br>";
   var platform    = "Operating system: <i>" + navigator.platform + "</i><br>";
   return appName + appVersion + platform;
}


/**
 * Parse for a key in URL, for example:
 *    index.html?loginName=martin&passwd=xyz&callUrl=myApp.html
 *
 * @param  urlParams - The parameters from the url,
 *         e.g. "?loginName=martin&passwd=xyz&callUrl=myApp.html"
 *         You can obtain this with e.g. 'top.location.search'
 * @param  key - The key to search, e.g. 'loginName'
 * @return The value, e.g. 'martin' or null if not found
 */
function getFromUrlParam(urlParams, key, defaultValue)
{
   // Log.trace("Looking for key='" + key + "' in URL params=" + urlParams);
   if (urlParams == null || urlParams.length < 5) {
      Log.trace("Your URL parameters are empty, can't find '" + key + "', using default = '" + defaultValue + "'");
      return defaultValue;
   }

   urlParams = unescape(urlParams);
   if (urlParams.indexOf("?") == 0)
      urlParams.substring(1, urlParams.length);  // strip '?'

   paramArr = urlParams.split("&");
   for (var ii=0; ii<paramArr.length; ii++) {
      var param = paramArr[ii];
      var pos = param.indexOf(key);
      if (pos != -1) {
         value = param.substring(pos + key.length + 1); // strip the '=' as well
         Log.trace("URL parameter '" + key + "' found, value = '" + value + "'");
         return value;
      }
   }
   Log.trace("Your URL parameter '" + key + "' is missing, returning " + defaultValue);
   return defaultValue;
}

/**
 * Walk recursively through all frames and list them.
 * Not yet tested!
 */
function debugFrames()
{
   var str = top.name +"<br />";
   var level = 0;
   return debugF(level+1, str, top.name);
}
function debugF(level, str, currentFrame)
{
   for (ii=0; ii<currentFrame.length; ii++) {
      var indent = "";
      if (level == 2) indent = "&nbsp;"
      var fr = currentFrame[ii];
      str += (indent + fr.name + "<br />");
      //str += debugF(level+1, str, fr);
   }
   return str;
}


/**
 * Logging of errors/warnings/infos.
 * Example:
 *    Log.error("The variable listenerList is empty.");
 *    Log.warn("Performance over internet is slow.");
 *    Log.info("Login granted.");
 *    Log.trace("Entering ping() method.");
*/
var logWindow = null;         // the window handle - a popup with the log output table
var collectForDisplay=false;  // To avoid for each logging a window update, collect log lines
var newLogsAvailable = false; // Is there a new log message?
var logStrippingInfo = null;  // Text to display if we stripped to many log entries from log window
var MAX_LOG_ENTRIES = 100;    // max number of rows in window
var levelColor = new Array(); // every log level is displayed in a characteristic color
levelColor["ERROR"] = "red";
levelColor["WARNING"] = "yellow";
levelColor["INFO"] = "green";
levelColor["TRACE"] = "white";

/**
 * The log object, containing infos about one logging output.
 */
function logObject(level_, codePos_, text_)
{
   var d = new Date();
   var hour = d.getHours();
   var min = new String(d.getMinutes() + 1);
   if (min.length == 1) min = "0" + min;
   var sec = new String(d.getSeconds());
   if (sec.length == 1) sec = "0" + sec;

   this.time = hour + ":" + min + ":" + sec;
   this.level = level_;
   this.codePos = codePos_;
   this.text = text_;
}
/**
 * Try to free resources.
 */
function clearLogObject(obj)
{
   obj.time = null;
   obj.level = null;
   obj.codePos = null;
   obj.text = null;
}
var logEntries = new Array();

function internal_(codePos, str)
{
   logToWindow("ERROR", codePos, str);
}

function error_(str)
{
   if (Log.ERROR) {
      var codePos = "";
      if (error_.caller != null)
         codePos = error_.caller.toString().substring(10, error_.caller.toString().indexOf(")") + 1);
      logToWindow("ERROR", codePos, str);
   }
   else
      alert("ERROR in " + codePos + ": " + str);
}
function warn_(str)
{
   if (Log.WARNING) {
      var codePos = "";
      if (warn_.caller != null)
         codePos = warn_.caller.toString().substring(10, warn_.caller.toString().indexOf(")") + 1);
      logToWindow("WARNING", codePos, str);
   }
}
function info_(str)
{
   if (Log.INFO) {
      var codePos = "";
      if (info_.caller != null)
         codePos = info_.caller.toString().substring(10, info_.caller.toString().indexOf(")") + 1);
      logToWindow("INFO", codePos, str);
   }
}
function trace_(str)
{
   if (Log.TRACE) {
      var codePos = "";
      if (trace_.caller != null)
         codePos = trace_.caller.toString().substring(10, trace_.caller.toString().indexOf(")") + 1);
      logToWindow("TRACE", codePos, str);
   }
}

/**
 * Catch and handle browser errors (internal errors)
 */
function catchError(text, url, row)
{
   var pos = "Browser";
   if ((typeof url) == "undefined" || url == null) {
      pos = "Check you javascript error console!";
   }
   else if (url.indexOf("http://") != -1) { // shorten the URL string
      pos = url.slice(7);
      var ind = pos.indexOf("/");
      if (ind != -1) {                 // shorten the URL string again
         pos = pos.slice(ind+1);
      }
   }
   Log.internal(pos + ": " + row, text);
   return false; // Allow errors from browser
   //return true; // Suppress errors from browser
}
// Set event handler, to catch the internal errors as well
self.onerror = catchError;
for (var jj=0; jj<top.frames.length; jj++) {
   top.frames[jj].onerror = catchError;
}


// This function logs to the status bar of netscape/explorer
function logToStatusBar(level, codePos, strText)
{
   var text = codePos + ": " + str;
   if(window.statusbar.visible == true) {
      window.defaultStatus = str;
      window.status = str;
   }
   else
      alert(str);
}

function __logToWindow__(level, codePos, text)
{
   alert("Level: "+level+"\ncodePos: "+codePos+"\ntext: "+text);
}


function closeLogWindow()
{
   /* Crashes netscape!?
   if ((typeof logWindow) != "undefined") {
      if (logWindow.closed == false) {
         logWindow.close();
      }
   }
   */
}


/**
 * This function logs to a pop up window
 * Every logging output is a colored row in a table.
 */
function logToWindow(level, codePos, text)
{
   if (text.length <= 0)
      return;

   if (logWindow == null || logWindow.closed) {
      logWindow = window.open("","XmlBlasterLog",
                  "content=no-cache," +
                  "width=600,height=400,screenX=300,screenY=200," +
                  "menubar=no,status=no,toolbar=no,titlebar=no," +
                  "scrollbars=yes,resizable=yes,alwaysraised=yes,dependent=yes");
   }

   if (level == "CLEAR") {
      logEntries.length = 0;
      displayLogs('forceRefresh');
      return;
   }

   if (level == "REFRESH") {
      displayLogs('forceRefresh');
      return;
   }

   if (logEntries.length >= MAX_LOG_ENTRIES) {
      // only half the logs, not empty them totally
      logStrippingInfo = "Stripping logging output to " + logEntries.length / 2 + " lines";
      var jj=0;
      for (var ii=logEntries.length/2; ii<logEntries.length; ii++) {
         clearLogObject(logEntries[jj]);
         logEntries[jj] = logEntries[ii];
         jj++;
      }
      logEntries.length = logEntries.length / 2;
   }

   logEntries[logEntries.length] = new logObject(level, codePos, text);

   //alert("codePos="+codePos+", text="+text);
   displayLogs('newEntry');
}

/**
 * To avoid to many log refreshs, we collect for 2 seconds all logs and
 * display them in one window refresh
 * @param caller  Telling us if this call is invoked from a timeout
 */
function displayLogs(caller)
{
   if (caller == 'forceRefresh') {
      collectForDisplay = false;
      if ((typeof displayRefreshHandler) != "undefined")
         window.clearTimeout(displayRefreshHandler);
   }
   else if (caller == 'newEntry') {
      newLogsAvailable = true;
      if (collectForDisplay)
         return;
   }
   else if (caller == 'fromTimer' && !newLogsAvailable) {
      collectForDisplay = false;
      if ((typeof displayRefreshHandler) != "undefined")
         window.clearTimeout(displayRefreshHandler);
      return;
   }

   newLogsAvailable = false;

   var d = logWindow.document;
   d.open("text/html","replace");

   var headerStr =
      '<HTML>\n' +
      '<HEAD>\n' +
      '   <title>Log your code!</title>\n' +
      '   <style type="text/css">\n' +
      '   <!--\n' +
      '      body, a, table, tr, td, th {FONT-FAMILY: verdana,arial,helvetica,sans-serif; font-size:11pt; }' +
      '   //-->\n' +
      '   </style>\n' +
      '</HEAD>\n' +
      '<BODY>\n' +
      '<CENTER><H3>Logging output from XmlBlaster Javascript</H3></CENTER>\n';
   if (logStrippingInfo != null) {
      headerStr += logStrippingInfo;
      logStrippingInfo = null;
   }
   d.writeln(headerStr);

   var tableStr =
         "<TABLE NAME='ChatTable' BORDER='2' WIDTH='100%'>\n" +
         "   <TR>" +
         "      <TD WIDTH='8%'>" +
         "Time" +
         "      </TD>" +
         "      <TD WIDTH='10%'>" +
         "Level" +
         "      </TD>" +
         "      <TD WIDTH='12%'>" +
         "Where" +
         "      </TD>" +
         "      <TD WIDTH='70%'>" +
         "Logging text" +
         "      </TD>" +
         "   </TR>\n";
   for (var ii=0; ii<logEntries.length; ii++) {
      tableStr +=
         "   <TR>" +
         "      <TD BGCOLOR='white'>" +
         "         <FONT size=2>" +
         logEntries[ii].time +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "      <TD BGCOLOR='" + levelColor[logEntries[ii].level] + "'>" +
         "         <FONT size=2>" +
         logEntries[ii].level +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "      <TD BGCOLOR='white'>" +
         "         <FONT size=2>" +
         logEntries[ii].codePos +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "      <TD BGCOLOR='white'>" +
         "         <FONT size=2>" +
         logEntries[ii].text +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "   </TR>\n";
   }
   tableStr += "</TABLE>\n";
   var clearStr = 'javascript:opener.logToWindow("CLEAR", "CLEAR", "CLEAR")';
   tableStr += "<A HREF='" + clearStr + "'>clear</A>\n";

   var refreshStr = 'javascript:opener.logToWindow("REFRESH", "REFRESH", "REFRESH")';
   if (top.Log.INFO == true) {
      var noInfoStr = 'javascript:top.opener.top.Log.INFO=false; ' + refreshStr;
      tableStr += "&nbsp;<A HREF='" + noInfoStr + "'>Info off</A>\n";
   }
   else {
      var infoStr = 'javascript:top.opener.top.Log.INFO=true; ' + refreshStr;
      tableStr += "&nbsp;&nbsp;<A HREF='" + infoStr + "'>Info on</A>\n";
   }

   if (top.Log.TRACE == true) {
      var noTraceStr = 'javascript:top.opener.top.Log.TRACE=false; ' + refreshStr;
      tableStr += "&nbsp;<A HREF='" + noTraceStr + "'>Trace off</A>\n";
   }
   else {
      var traceStr = 'javascript:top.opener.top.Log.TRACE=true; ' + refreshStr;
      tableStr += "&nbsp;&nbsp;<A HREF='" + traceStr + "'>Trace on</A>\n";
   }

   d.writeln(tableStr);
   d.writeln('<P /></BODY>\n</HTML>');
   d.close();
   //logWindow.focus();
   logWindow.scrollTo(0, 10000);

   // To avoid to many log refreshs, we collect for 2 seconds all logs and
   // display them in one window refresh
   collectForDisplay = true;
   displayRefreshHandler = window.setTimeout( "displayLogs('fromTimer')", 2000 );

   return;
}

/**
 * The log handler object, containing the necessary variables.
 */
function LogHandler()
{
   this.internal = internal_; // errors from browser are caught
   this.error = error_;       // Log.error() output
   this.warn = warn_;
   this.info = info_;
   this.trace = trace_;
   this.ERROR = true;         // Set in your code Log.ERROR=false; to switch off
   this.WARNING = true;       // Set in your code Log.WARNING=false; to switch off
   this.INFO = true;          // Set in your code Log.INFO=false; to switch off
   this.TRACE = false;        // Set in your code Log.TRACE=true; to switch on
   //alert("Leaving logHandler in util.js");
}

/**
 * The log handler, use this to invoke your logging output.
 * Example:
 *    Log.warn("Performance over internet is slow.");
 */
var Log = new LogHandler();

