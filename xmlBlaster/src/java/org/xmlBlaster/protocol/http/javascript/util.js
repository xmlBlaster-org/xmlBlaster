/*------------------------------------------------------------------------------
Name:      util.js
Project:   xmlBlaster.org
Comment:   Implementing some Javascript utility objects
Author:    ruff@swand.lake.de konrad.krafft@doubleslash.de
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
   var appName     = "browser name: <i>" + navigator.appName + "</i><br>";
   var appVersion  = "browser version: <i>" + navigator.appVersion + "</i><br>";
   var platform    = "operating system: <i>" + navigator.platform + "</i><br>";
   return "<hr>" + appName + appVersion + platform;
}

/**
 * Logging of errors/warnings/infos
 * Example:
 *    Log.error("The variable listenerList is empty.");
 *    Log.warning("Performance over internet is slow.");
 *    Log.info("Login granted.");
*/
var logWindow = null;         // the window handle - a popup with the log output table
var MAX_LOG_ENTRIES = 60;     // max number of rows in window
var levelColor = new Array(); // every log level is displayed in a characteristic color
levelColor["ERROR"] = "red";
levelColor["WARNING"] = "yellow";
levelColor["INFO"] = "green";

// The log object, containing infos about one logging output
function logObject(level_, codePos_, text_)
{
   this.level = level_;
   this.codePos = codePos_;
   this.text = text_;
}
var logEntries = new Array();

function internal_(codePos, str)
{
   logToWindow("ERROR", codePos, str);
}

function error_(str)
{
   var codePos = error_.caller.toString().substring(10, error_.caller.toString().indexOf(")") + 1);
   logToWindow("ERROR", codePos, str);
   // alert("ERROR in " + codePos + ": " + str);
}
function warning_(str)
{
   var codePos = warning_.caller.toString().substring(10, warning_.caller.toString().indexOf(")") + 1);
   logToWindow("WARN", codePos, str);
}
function info_(str)
{
   var codePos = info_.caller.toString().substring(10, info_.caller.toString().indexOf(")") + 1);
   logToWindow("INFO", codePos, str);
}

// Catch and handle browser errors
function catchError(text, url, row)
{
   if (url.indexOf("http://") != -1) { // shorten the URL string
      url = url.slice(7);
      var ind = url.indexOf("/");
      if (ind != -1) {                 // shorten the URL string again
         url = url.slice(ind+1);
      }
   }
   Log.internal(url + ": " + row, text);
   return true; // Suppress errors from browser
}
// Set event handler, to catch the internal errors as well
self.onerror = catchError;
for (var jj=0; jj<top.frames.length; jj++) {
   top.frames[ii].onerror = catchError;
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

// This function logs to a pop up window
// Every logging output is a colored row in a table.
function logToWindow(level, codePos, text)
{
   if (text.length <= 0)
      return;

   if (logWindow == null || logWindow.closed) {
      logWindow = window.open("","XmlBlasterLog",
                  "content=no-cache," +
                  "width=500,height=400,screenX=300,screenY=200," +
                  "menubar=no,status=no,toolbar=no,titlebar=no," +
                  "scrollbars=yes,resizable=yes,alwaysraised=yes,dependent=yes");
   }
   var d = logWindow.document;

   if (level == "CLEAR") {
      logEntries.length = 0;
   }
   else if (logEntries.length >= MAX_LOG_ENTRIES) {
      // only half the logs, not empty them totally
      alert("Stripping logging output to " + logEntries.length / 2 + " lines");
      var jj=0;
      for (var ii=logEntries.length/2; ii<logEntries.length; ii++) {
         logEntries[jj] = logEntries[ii];
         jj++;
      }
      logEntries.length = logEntries.length / 2;
   }
   else {
      logEntries[logEntries.length] = new logObject(level, codePos, text);
   }

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
   d.writeln(headerStr);

   var tableStr =
         "<TABLE NAME='ChatTable' BORDER='2' WIDTH='100%'>\n" +
         "   <TR>" +
         "      <TD WIDTH='10%'>" +
         "Level" +
         "      </TD>" +
         "      <TD WIDTH='10%'>" +
         "Where" +
         "      </TD>" +
         "      <TD WIDTH='80%'>" +
         "Logging text" +
         "      </TD>" +
         "   </TR>\n";
   for (var ii=0; ii<logEntries.length; ii++) {
      tableStr +=
         "   <TR>" +
         "      <TD BGCOLOR='" + levelColor[logEntries[ii].level] + "'>" +
         "         <FONT size=2>" +
         logEntries[ii].level +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "      <TD>" +
         "         <FONT size=2>" +
         logEntries[ii].codePos +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "      <TD>" +
         "         <FONT size=2>" +
         logEntries[ii].text +
         "         </FONT>" +
         "      &nbsp;</TD>" +
         "   </TR>\n";
   }
   tableStr += "</TABLE>\n";
   var clearStr = 'javascript:opener.logToWindow("CLEAR", "CLEAR", "CLEAR")';
   tableStr += "<A HREF='" + clearStr + "'>clear</A>\n";

   d.writeln(tableStr);
   d.writeln('</BODY>\n</HTML>');
   d.close();
   logWindow.focus();
   return;
}

// The log handler, use this to invoke your logging output
function LogHandler()
{
   this.internal = internal_; // errors from browser are caught
   this.error = error_;       // Log.error() output
   this.warning = warning_;
   this.info = info_;
   this.DEBUG = false;
}

// The log handler, use this to invoke your logging output
// Example:
//    Log.warning("Performance over internet is slow.");
var Log = new LogHandler();

