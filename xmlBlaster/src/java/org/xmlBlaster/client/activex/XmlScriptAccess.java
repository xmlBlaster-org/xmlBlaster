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
import java.beans.SimpleBeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

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
public class XmlScriptAccess extends SimpleBeanInfo implements I_Callback {
   private static String ME = "XmlScriptAccess";
   private final Global glob;
   private final LogChannel log;
   private XmlScriptInterpreter interpreter;
   private Reader reader;
   private OutputStream outStream;
   private UpdateListener updateListener;

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
    * Add a C# / VisualBasic listener over the ActiveX bridge. 
    */
   public void addUpdateListener(UpdateListener updateListener) /* throws java.util.TooManyListenersException */ {
      this.updateListener = updateListener;
   }

   /**
    * Remove a C# / VisualBasic listener. 
    */
   public void removeUpdateListener(UpdateListener updateListener) {
      this.updateListener = null;
   }

   /**
    * Fire an event into C# / VisualBasic containing an updated message. 
    */
   protected String notifyUpdateEvent(String cbSessionId, String key, byte[] content, String qos) {
      if (this.updateListener == null) {
         log.warn(ME, "No updateListener is registered, ignoring " + key);
         return "<qos><state id='WARNING'/></qos>";
      }
      UpdateEvent ev = new UpdateEvent(this, cbSessionId, key, content, qos);
      log.info(ME, "Notifying updateListener with new message " + key);
      return this.updateListener.update(ev);
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
    * @param args Command line arguments for example { "-protocol", SOCKET, "-trace", "true" }
    */
   public void initArgs(String[] args) {
      this.glob.init(args);
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
         this.interpreter = new XmlScriptInterpreter(this.glob, this.glob.getXmlBlasterAccess(),
                                                     this, null, this.outStream);
         this.interpreter.parse(this.reader);
         return this.outStream.toString();
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "sendRequest failed: " + e.getMessage());
         throw new RuntimeException(e.getMessage());
      }
      catch (Exception e) {
         log.error(ME, "sendRequest failed: " + e.toString());
         e.printStackTrace();
         throw new RuntimeException(e.toString());
      }
   }

   /**
    * Enforced by I_Callback
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      log.info(ME, "Callback update arrived: " + updateKey.getOid());
      return notifyUpdateEvent(cbSessionId, updateKey.toXml(), content, updateQos.toXml());
   }

   /**
    * For testing: java org.xmlBlaster.client.activex.XmlScriptAccess
    */
   public static void main(String args[]) {
      XmlScriptAccess access = new XmlScriptAccess();
      Properties props = access.createPropertiesInstance();
      //props.put("protocol", "SOCKET");
      props.put("trace", "true");
      access.initialize(props);
      class TestUpdateListener implements UpdateListener {
         public String update(UpdateEvent updateEvent) {
            System.out.println("TestUpdateListener.update: " + updateEvent.getKey());
            return "<qos><state id='OK'/></qos>";
         }
      }
      TestUpdateListener listener = new TestUpdateListener();
      access.addUpdateListener(listener);
      String request = "<xmlBlaster>" +
                       "   <connect/>" +
                       "   <subscribe><key oid='test'></key><qos/></subscribe>" +
                       "   <publish>" +
                       "     <key oid='test'><airport name='london'/></key>" +
                       "     <content>This is a simple script test</content>" +
                       "     <qos/>" +
                       "   </publish>" +
                       "   <wait delay='1000'/>" +
                       "   <disconnect />" +
                       "</xmlBlaster>";
      String response = access.sendRequest(request);
      System.out.println("Response is: " + response);
   }
}

