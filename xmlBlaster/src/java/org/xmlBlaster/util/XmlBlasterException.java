/*------------------------------------------------------------------------------
Name:      XmlBlasterException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
Version:   $Id: XmlBlasterException.java,v 1.6 2002/05/11 08:09:02 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.jutils.JUtilsException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.Log;


/**
 * The basic exception handling class for xmlBlaster.
 * <p />
 * This exception will be thrown in remote RMI calls as well.
 * <p />
 * Set "-Log.exception true" on command line to get Log.error()
 * for a new XmlBlasterException thrown (includes method and line number).
 * @author "Marcel Ruff" <ruff@swand.lake.de>
 */
public class XmlBlasterException extends Exception implements java.io.Serializable
{
   public String id;
   public String reason;

   /** Set to true if a new exception shall be logged with Log.error() */
   private static boolean logException;
   
   /** Where to find the method and line number in the stack trace */
   private static int numIgnore;

   
   static {
      logException = Global.instance().getProperty().get("Log.exception", false);
      String jvm = System.getProperty("java.vm.info");
      numIgnore = 3;
      if (jvm != null) {
         if (jvm.indexOf("IBM") >= 0)
            numIgnore = 5;
      }
   }

   public XmlBlasterException(String id, String reason)
   {
      this.id = id;
      this.reason = reason;
      if (logException) log();
   }

   public XmlBlasterException(JUtilsException e)
   {
      this.id = e.id;
      this.reason = e.reason;
      if (logException) log();
   }

   public XmlBlasterException(ConnectionException e)
   {
      this.id = e.id;
      this.reason = e.reason;
      if (logException) log();
   }

   private void log()
   {
      String location = null;
      try {
         StringWriter stringWriter = new StringWriter();
         PrintWriter printWriter = new PrintWriter(stringWriter);
         (new Exception()).printStackTrace(printWriter);
         String trace = stringWriter.getBuffer().toString();
         StringReader stringReader = new StringReader(trace);
         BufferedReader bufferedReader = new BufferedReader(stringReader);
         //for (int ii=0; ii<numIgnore; ii++) System.out.println(bufferedReader.readLine()); // ignore first 3 lines (is this location)
         for (int ii=0; ii<numIgnore; ii++) bufferedReader.readLine(); // ignore first 3 lines (is this location)
         String stackEntry = bufferedReader.readLine().trim();
         int space = stackEntry.indexOf(" ");
         int paren = stackEntry.indexOf("(");
         int colon = stackEntry.indexOf(":");
         String method = stackEntry.substring(space + 1, paren);
         String sourceName = stackEntry.substring(paren + 1, colon);
         int line = 0;
         try {
            paren = stackEntry.indexOf(")");
            String ln = stackEntry.substring(colon+1, paren);
            line = Integer.parseInt(ln);
         }
         catch (NumberFormatException e) {}
         //location = sourceName + ":" + method + ":" + line;
         location = method + ":" + line;
      }
      catch (IOException e) {}

      if (location != null)
         Log.error(location + "-" + id, reason);
      else
         Log.error(id, reason);

   }

   public String toString()
   {
      return "id=" + id + " reason=" + reason;
   }

   /**
    * Create a XML representation of the Exception.
    * <pre>
    *   &lt;exception id='" + id + "'>
    *      &lt;class>JavaClass&lt;/class>
    *      &lt;reason>&lt;![cdata[
    *        bla bla
    *      ]]>&lt;/reason>
    *   &lt;/exception>
    * </pre>
    */
   public String toXml()
   {
      StringBuffer buf = new StringBuffer(reason.length() + 256);
      buf.append("<exception id='").append(id).append("'>\n");
      buf.append("   <class>").append(getClass().getName()).append("</class>\n");
      buf.append("   <reason><![CDATA[").append(reason).append("]]></reason>\n");
      buf.append("</exception>");
      return buf.toString();
   }
}
