/*------------------------------------------------------------------------------
Name:      util.js
Project:   xmlBlaster.org
Comment:   Implementing some Javascript utility objects
Author:    ruff@swand.lake.de
Version:   $Id: util.js,v 1.3 2000/03/17 16:05:18 kkrafft2 Exp $
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
top.onerror = catchError;
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

function logToWindow(level, codePos, text)
{
   alert("Level: "+level+"\ncodePos: "+codePos+"\ntext: "+text);
}

// This function logs to a pop up window
// Every logging output is a colored row in a table.
function __logToWindow__(level, codePos, text)
{
   if (text.length <= 0)
      return;

   if (logWindow == null || logWindow.closed) {
      logWindow = window.open("","XmlBlasterLog",
                  "content=no-cache,scrollbars=yes,resizable=yes," +
                  "width=500,height=400,screenX=300,screenY=200," +
                  "menubar=no,status=no,toolbar=no,titlebar=no" +
                  "alwaysraised=yes,dependent=yes");
   }
   var d = logWindow.document;

   if (logEntries.length >= MAX_LOG_ENTRIES) {
      // TODO: only half the logs, not empty them totally
      logEntries[logEntries.length] = new logObject(level, codePos, text);
   }

   logEntries[logEntries.length] = new logObject(level, codePos, text);

   var headerStr =
      '<HTML>' +
      '<HEAD>' +
      '   <link REL="stylesheet" type="text/css" href="xmlBlaster.css">' +
      '   <title>Log your code!</title>' +
      '</HEAD>' +
      '<BODY>' +
      '<CENTER><H3>Logging output from XmlBlaster Javascript</H3></CENTER>';
   d.writeln(headerStr);

   var tableStr =
         "<TABLE NAME='ChatTable' BORDER='2' WIDTH='100%'>" +
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
         "   </TR>";
   for (var ii=0; ii<logEntries.length; ii++) {
      tableStr +=
         "   <TR>" +
         "      <TD BGCOLOR='" + levelColor[logEntries[ii].level] + "'>" +
         logEntries[ii].level +
         "      &nbsp;</TD>" +
         "      <TD>" +
         logEntries[ii].codePos +
         "      &nbsp;</TD>" +
         "      <TD>" +
         logEntries[ii].text +
         "      &nbsp;</TD>" +
         "   </TR>";
   }
   tableStr += "</TABLE>";

   d.writeln(tableStr);
   d.writeln('</BODY></HTML>');
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
}

// The log handler, use this to invoke your logging output
// Example:
//    Log.warning("Performance over internet is slow.");
var Log = new LogHandler();



/*
   Copyright 1997 Stefan Mintert / Addison-Wesley
   Use like this:
   var varA = "Hello, ";
   var varB = "world!";
   "http://localhost" + var_encode("varA", "varB");
   // the variable names are passed as a string
   // The values are URL-encoded: http://localhost#varA%3DHello%2C%20%3BvarB%3Dworld%21
   // %21 is hex value for '!'
   // %3B is hex value for ';'
   // %3D is hex value for '='
   // %2C is hex value for ','
   // %26 is hex value for '&'
   // %20 is hex value for ' '
   // http://www.xy.com/?flag=jfl&frame=yes&id=38aa6c5ccece7
*/
function var_encode()
{
   var hashstring ="";
   var wert

   for (i = 0; i < arguments.length; i++)
   {
      hashstring += arguments[i]+"=";
      wert = eval(arguments[i]);
      hashstring += wert.toString();
      if (i < arguments.length-1)
        hashstring += ";";
   }
   return "#"+escape(hashstring);
}

function var_decode()
{
   var hashstring = self.location.hash;
   var et_Position, name_wert, gleich_Position, name, wert;


   if ( hashstring.charAt(0) == "#")
     hashstring = hashstring.substr(1,hashstring.length);

   eval(unescape(hashstring));

}
