/*------------------------------------------------------------------------------
Name:      XmlScriptAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Bean to export with Windows ActiveX bridge
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Properties;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.script.XmlScriptInterpreter;


/**
 * This bean can be exported to a Microsoft dll and accessed by C# or Visual Basic.Net 
 * <p />
 * Here we support only XML scripting access as described in the <i>client.script</i> requirement.
 * <p />
 * One instance of this can hold one permanent connection to the xmlBlaster server,
 * multi threaded access is supported.
 * 
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html">client.script requirement</a>
 * @see <a href="http://java.sun.com/j2se/1.4.2/docs/guide/beans/axbridge/developerguide/index.html">ActiveX Bridge Developer Guide</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XmlScriptAccess {
   private static String ME = "XmlScriptAccess";
   private final Global glob;
   private final LogChannel log;
   private XmlScriptInterpreter interpreter;
   private Reader reader;
   private OutputStream outStream;
   private OutputStream updStream;

   /**
    * Create a new access bean. 
    * We read a xmlBlaster.properties file if one is found
    */
   public XmlScriptAccess() {
      System.out.println("Calling ctor of XmlScriptAccess");
      this.glob = new Global();  // Reads xmlBlaster.properties
      this.log = glob.getLog("demo");
   }

   /**
    * Access a Properties object. 
    * @return We create a new instance for you
    */
   public Properties createPropertiesInstance() {
      return new Properties();
   }
   
   public void initialize(Properties properties) {
      this.glob.init(properties);
   }

   /**
    * Send xml encoded requests to the xmlBlaster server. 
    * @exception All caught exceptions are thrown as RuntimeException
    */
   public String sendRequest(String xmlRequest) {
      try {
         this.reader = new StringReader(xmlRequest);
         this.outStream = new ByteArrayOutputStream();
         // TODO: Dispatch events:
         this.updStream = this.outStream;
         this.interpreter = new XmlScriptInterpreter(this.glob, this.glob.getXmlBlasterAccess(), this.outStream, this.updStream, null);
         this.interpreter.parse(this.reader);
         return this.outStream.toString();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Client failed: " + e.getMessage());
         throw new RuntimeException(e.getMessage());
      }
      catch (Exception e) {
         log.error(ME, "Client failed: " + e.toString());
         throw new RuntimeException(e.toString());
         // e.printStackTrace();
      }
   }

   /**
    * For testing: java org.xmlBlaster.client.activex.XmlScriptAccess
    */
   public static void main(String args[]) {
      XmlScriptAccess access = new XmlScriptAccess();
      Properties props = access.createPropertiesInstance();
      //props.put("protocol", "SOCKET");
      access.initialize(props);
      String request = "<xmlBlaster> <connect/> <disconnect /> </xmlBlaster>";
      String response = access.sendRequest(request);
      System.out.println("Response is: " + response);
   }
}

