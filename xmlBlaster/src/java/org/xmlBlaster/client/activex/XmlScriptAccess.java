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
import java.util.List;
import java.util.LinkedList;
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
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.util.MsgUnit;

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

   private List updateStack = new LinkedList();

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
      log.info(ME, "Registering update listener");
      Thread.dumpStack();
      this.updateListener = updateListener;
   }

   /**
    * Remove a C# / VisualBasic listener. 
    */
   public void removeUpdateListener(UpdateListener updateListener) {
      log.info(ME, "Removing update listener");
      Thread.dumpStack();
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
      String ret = this.updateListener.update(ev);
      log.info(ME, "Notifying updateListener done: returned '" + ret + "'");
      return ret;
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

   public Global getGlobal() {
      return this.glob;
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
    * Subscribe to messages. 
    * @param xmlKey Which message topics to retrieve
    * @param xmlQos Control the behavior and further filter messages with mime based filter plugins
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos, I_Callback)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public SubscribeReturnQos subscribe(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().subscribe(xmlKey, xmlQos, null);
   }

   /**
    * Get synchronous messages. 
    * @param xmlKey Which message topics to retrieve
    * @param xmlQos Control the behavior and further filter messages with mime based filter plugins
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">interface.get requirement</a>
    * @see I_XmlBlasterAccess#get(GetKey, GetQos)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public MsgUnit[] get(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().get(
                  new GetKey(this.glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                  new GetQos(this.glob, glob.getQueryQosFactory().readObject(xmlQos)));
   }

   /**
    * Publish a message. 
    * @param xmlKey The message topic
    * @param content The payload
    * @param xmlQos Control the behavior
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    * @see I_XmlBlasterAccess#publish(MsgUnit)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public PublishReturnQos publish(String xmlKey, String contentStr, String qos) throws XmlBlasterException {
      MsgUnit msgUnit = new MsgUnit(this.glob, xmlKey, contentStr, qos);
      return this.glob.getXmlBlasterAccess().publish(msgUnit);
   }

   /**
    * Enforced by I_Callback
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      log.info(ME, "Callback update arrived: " + updateKey.getOid());
      UpdateMsgUnit msgUnit = new UpdateMsgUnit(cbSessionId, updateKey, content, updateQos);
      if (this.updateStack != null)
         this.updateStack.add(msgUnit);
      return notifyUpdateEvent(cbSessionId, updateKey.toXml(), content, updateQos.toXml());
   }

   /**
    * Poll for updated message. 
    * <br />
    * NOTE: This is a work around until we have fixed sending events to ActiveX
    * clients directly
    * <br />
    * TODO: Change linked list holding the updated messages to a I_Queue
    * which supports persistence to not loose pending messages on shutdown.
    * @return Never null
    */
   public UpdateMsgUnit[] consumeUdateMessages() {
      if (this.updateStack == null) return new UpdateMsgUnit[0];
      UpdateMsgUnit[] ret = (UpdateMsgUnit[])this.updateStack.toArray(new UpdateMsgUnit[this.updateStack.size()]);
      this.updateStack.clear();
      log.info(ME, "Consuming " + ret.length + " updated messages");
      return ret;
   }

   /**
    * Switch on/off if you want to access updated messages with consumeUdateMessages(). 
    * Currently default to act=true until the event bug is fixed
    */
   public void activateUpdateConsumer(boolean act) {
      if (act) {
         if (this.updateStack == null) {
            this.updateStack = new LinkedList();
         }
      }
      else {
         this.updateStack = null;
      }
   }

   /**
    * For testing: java org.xmlBlaster.client.activex.XmlScriptAccess
    */
   public static void main(String args[]) {
      try {
         XmlScriptAccess access = new XmlScriptAccess();
         access.activateUpdateConsumer(true);
         Properties props = access.createPropertiesInstance();
         props.put("protocol", "SOCKET");
         //props.put("trace", "true");
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
                          "</xmlBlaster>";
         String response = access.sendRequest(request);
         System.out.println("Response is: " + response);
         UpdateMsgUnit[] msgs = access.consumeUdateMessages();
         for(int i=0; i<msgs.length; i++) {
            System.out.println("Access queued update message '" + msgs[i].getUpdateKey().toXml() + "'");
         }
         msgs = access.consumeUdateMessages();
         if (msgs.length != 0)
            System.out.println("ERROR: queued update message not consumed properly");

         System.out.println("***** Publishing ...");
         PublishReturnQos ret = access.publish("<key oid='test'/>", "Bla", "<qos/>");
         System.out.println("***** Published message ret=" + ret.getState());
         response = access.sendRequest("<xmlBlaster>disconnect/></xmlBlaster>");
      }
      catch (Throwable e) {
         System.out.println("ERROR: Caught exception: " + e.toString());
      }
   }
}

