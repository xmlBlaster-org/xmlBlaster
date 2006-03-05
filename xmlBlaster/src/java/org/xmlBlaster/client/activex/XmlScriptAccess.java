/*------------------------------------------------------------------------------
Name:      XmlScriptAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Bean to export with Windows ActiveX bridge
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Properties;
import java.beans.SimpleBeanInfo;

import EDU.oswego.cs.dl.util.concurrent.Latch; // http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.client.script.XmlScriptClient;
import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.SynchronousCache;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
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
 * This bean can be exported to a Microsoft dll (ActiveX component) and
 * be accessed by C# or Visual Basic.Net 
 * <p>
 * Here we support XML scripting access as described in the <i>client.script</i> requirement
 * by calling <code>sendRequest()</code> or alternatively you can use the methods
 * like <code>publishStr()</code> or <code>subscribe()</code> directly. The latter
 * methods have the advantage to return a ready parsed object to the ActiveX component,
 * for example Visual Basic can directly call all methods of <code>SubscribeReturnQos</code>
 * which is returned by <code>subscribe()</code>.
 * </p>
 * <p>
 * Compile the ActiveX control with <code>build activex</code> and see Visual Basic
 * and C# samples in directory <code>xmlBlaster/demo/activex</code>.
 * </p>
 * <p>
 * As events into ActiveX can't have a return value and can't throw
 * an exception back to us we handle it here as a callback, for example
 * Visual Basic needs to call <code>sendUpdateReturn()</code> or <code>sendUpdateException()</code> after
 * processing a message received by <code>update()</code>.
 * Our update thread blocks until one of those two methods is called, however
 * the blocking times out after 10 minutes which is adjustable with
 * the property <code>client/activex/responseWaitTime</code> given in milli seconds.
 * </p>
 * <p>
 * One instance of this can hold one permanent connection to the xmlBlaster server,
 * multi threaded access is supported.
 * </p>
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html">client.script requirement</a>
 * @see <a href="http://java.sun.com/j2se/1.4.2/docs/guide/beans/axbridge/developerguide/index.html">ActiveX Bridge Developer Guide</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XmlScriptAccess extends SimpleBeanInfo implements I_Callback {
   private static String ME = "XmlScriptAccess";
   private final Global glob;
   private static Logger log = Logger.getLogger(XmlScriptAccess.class.getName());
   private XmlScriptInterpreter interpreter;
   private Reader reader;
   private OutputStream outStream;
   private UpdateListener updateListener;

   // As events into ActiveX can't have a return value and can't throw
   // an exception back to us we handle it here as a callback
   private Latch updateReturnLatch;
   private String updateReturnQos;
   private XmlBlasterException updateReturnException;
   private long responseWaitTime;

   /**
    * Create a new access bean. 
    * We read a xmlBlaster.properties file if one is found
    */
   public XmlScriptAccess() {
      this.glob = new Global();  // Reads xmlBlaster.properties


      // Wait max 10 minutes for update() method in C#/VB to return:
      this.responseWaitTime = this.glob.getProperty().get("client/activex/responseWaitTime", 1000L * 60L * 10L);
      if (log.isLoggable(Level.FINER)) log.finer("Calling ctor of XmlScriptAccess, responseWaitTime=" + this.responseWaitTime);

      // Use socket protocol as default setting
      String protocol = this.glob.getProperty().get("protocol", "");
      if ("".equals(protocol)) {
         try {
            this.glob.getProperty().set("protocol", "SOCKET");
         }
         catch (XmlBlasterException e) {
            log.severe("Failed setting SOCKET protocol, we continue nevertheless: " + e.toString());
         }
      }
   }

   /**
    * Add a C# / VisualBasic listener over the ActiveX bridge. 
    * This method is called automatically when activating the bridge
    */
   public void addUpdateListener(UpdateListener updateListener) /* throws java.util.TooManyListenersException */ {
      log.info("Registering update listener");
      if (log.isLoggable(Level.FINEST)) Thread.dumpStack();
      this.updateListener = updateListener;
   }

   /**
    * Remove a C# / VisualBasic listener. 
    * This method is called automatically when deactivating the bridge
    */
   public void removeUpdateListener(UpdateListener updateListener) {
      log.info("Removing update listener");
      if (log.isLoggable(Level.FINEST)) Thread.dumpStack();
      this.updateListener = null;
   }

   /**
    * Fire an event into C# / VisualBasic containing an updated message. 
    * <br />
    * Note: The ActiveX event can't convey a return value or an exception back
    * to us. There for we block the thread and wait until the
    * activeX component has delivered us a return value or an exception by
    * calling setUpdateReturn() or setUpdateException() 
    */
   protected synchronized String notifyUpdateEvent(String cbSessionId, UpdateKey key, byte[] content, UpdateQos qos) throws XmlBlasterException {
      if (this.updateListener == null) {
         log.warning("No updateListener is registered, ignoring " + key.toXml());
         return "<qos><state id='WARNING'/></qos>";
      }
      UpdateEvent ev = new UpdateEvent(this, cbSessionId, key, content, qos);
      if (log.isLoggable(Level.FINE)) log.fine("Notifying updateListener with new message " + key.toXml());
      this.updateReturnLatch = new Latch();

      this.updateListener.update(ev);

      boolean awaikened = false;
      while (true) {
         try {
            if (log.isLoggable(Level.FINE)) log.fine("notifyUpdateEvent() Entering wait ...");
            awaikened = this.updateReturnLatch.attempt(this.responseWaitTime); // block max. milliseconds
            break;
         }
         catch (InterruptedException e) {
            log.warning("Waking up (waited on " + key.getOid() + " update response): " + e.toString());
            // try again
         }
      }
      try {
         if (awaikened) {
            if (this.updateReturnQos != null) {
               if (log.isLoggable(Level.FINE)) log.fine("Notifying updateListener done: returned '" + this.updateReturnQos + "'");
               return this.updateReturnQos;
            }
            else if (this.updateReturnException != null) {
               log.warning("Update failed: " + this.updateReturnException.getMessage());
               throw this.updateReturnException;
            }
            else {
               log.severe("Update failed, no return available");
               throw new XmlBlasterException(this.glob, ErrorCode.USER_UPDATE_ERROR, ME, "Update to ActiveX failed, no return available");
            }
         }
         else {
            String str = "Timeout of " + this.responseWaitTime + " milliseconds occured when waiting on " + key.getOid() + " return value";
            log.warning(str);
            throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME, str);
         }
      }
      finally {
         this.updateReturnLatch = null;
      }
   }

   /**
    * ActiveX code needs to call this method to set the return value
    * for the current update message. 
    * Alternatively you can call setUpdateException() to pass back
    * an exception.
    * <br />
    * Note: You have to call setUpdateReturn() OR setUpdateException()
    * for each update message to release the blocking thread!
    *
    * @param updateReturnQos for example "<qos><state id='OK'/></qos>"
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html">The interface.update requirement</a>
    */
   public void setUpdateReturn(String updateReturnQos) {
      if (this.updateReturnLatch == null) {
         log.warning("Ignoring setUpdateReturn(), updateReturnLatch == null, probably a timeout occurred");
         return;
      }
      this.updateReturnQos = updateReturnQos;
      this.updateReturnException = null;
      this.updateReturnLatch.release();
      if (log.isLoggable(Level.FINER)) log.finer("setUpdateReturn() called");
   }

   /**
    * ActiveX code can call this method to return an exception for 
    * the current update message
    * @param errorCode Only known ErrorCode strings of type "user.*" are allowed
    * @see org.xmlBlaster.util.def.ErrorCode
    */
   public void setUpdateException(String errorCode, String message) {
      if (this.updateReturnLatch == null) {
         log.warning("Ignoring setUpdateException(), updateReturnLatch == null, probably a timeout occurred");
         return;
      }
      this.updateReturnQos = null;
      ErrorCode code = null;
      try {
         code = ErrorCode.toErrorCode(errorCode);
      }
      catch (IllegalArgumentException e) {
         log.warning("Don't know error code '" + errorCode + "', changing it to " + ErrorCode.USER_UPDATE_ERROR.toString());
         message += ": original errorCode=" + errorCode;
         code = ErrorCode.USER_UPDATE_ERROR;
      }

      this.updateReturnException = new XmlBlasterException(this.glob, code, ME, message);
      this.updateReturnLatch.release();
      if (log.isLoggable(Level.FINER)) log.finer("setUpdateException() called");
   }

   /**
    * Access a Properties object to be used later for initialize(). 
    * @return We create a new instance for you
    */
   public Properties createPropertiesInstance() {
      return new Properties();
   }
   
   /**
    * Initialize the environment. 
    */
   public void initialize(Properties properties) {
      this.glob.init(properties);
   }

   /**
    * Initialize the environment. 
    * If you use the initialize(Properties) variant or this method makes no difference.
    * @param args Command line arguments for example { "-protocol", SOCKET, "-trace", "true" }
    */
   public void initArgs(String[] args) {
      this.glob.init(args);
   }

   /**
    * Access the handle of this xmlBlaster connection. 
    */
   public Global getGlobal() {
      return this.glob;
   }

   /**
    * Send xml encoded requests to the xmlBlaster server. 
    * @exception All caught exceptions are thrown as RuntimeException
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html">client.script requirement</a>
    */
   public String sendRequest(String xmlRequest) {
      try {
         this.reader = new StringReader(xmlRequest);
         this.outStream = new ByteArrayOutputStream();
         // TODO: Dispatch events:
         this.interpreter = new XmlScriptClient(this.glob, this.glob.getXmlBlasterAccess(),
                                                     this, null, this.outStream);
         this.interpreter.parse(this.reader);
         return this.outStream.toString();
      }
      catch (XmlBlasterException e) {
         log.warning("sendRequest failed: " + e.getMessage());
         throw new RuntimeException(e.getMessage());
      }
      catch (Exception e) {
         log.severe("sendRequest failed: " + e.toString());
         e.printStackTrace();
         throw new RuntimeException(e.toString());
      }
   }

   /**
    * Setup the cache mode.
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#createSynchronousCache(int)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cache.html">client.cache requirement</a>
    */
   public SynchronousCache createSynchronousCache(int size) {
      return this.glob.getXmlBlasterAccess().createSynchronousCache(size);
   }

   /**
    * Login to xmlBlaster. 
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#connect(ConnectQos, org.xmlBlaster.client.I_Callback)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   public ConnectReturnQos connect(String xmlQos) throws XmlBlasterException {
      ConnectQos qos = new ConnectQos(this.glob, this.glob.getConnectQosFactory().readObject(xmlQos));
      return this.glob.getXmlBlasterAccess().connect(qos, this);
   }

   /**
    * Leaves the connection to the server. 
    * The server side resources are not freed if the client has connected fail save
    * and messages are queued until we login again with the same name and publicSessionId
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#leaveServer(java.util.Map)
    */
   public void leaveServer() {
      this.glob.getXmlBlasterAccess().leaveServer(null);
   }

   /**
    * Has the connect() method successfully passed? 
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#isConnected()
    */
   public boolean isConnected() {
      return this.glob.getXmlBlasterAccess().isConnected();
   }

   /**
    * If no communication takes place longer the the lifetime of the session
    * we can refresh the session to avoid auto logout
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#refreshSession()
    */
   public void refreshSession() throws XmlBlasterException {
      this.glob.getXmlBlasterAccess().refreshSession();
   }

   /**
    * Access the callback server which is currently used in I_XmlBlasterAccess. 
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#getCbServer()
    */
   public I_CallbackServer getCbServer() {
      return this.glob.getXmlBlasterAccess().getCbServer();
   }

   /**
   * @see org.xmlBlaster.client.I_XmlBlasterAccess#getId()
    */
   public String getId() {
      return this.glob.getXmlBlasterAccess().getId();
   }

   /**
    * Logout from the server, free all server and client side resources. 
    * @return false if connect() wasn't called before or if you call disconnect() multiple times
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#disconnect(DisconnectQos)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   public boolean disconnect(String xmlQos) throws XmlBlasterException {
      DisconnectQos disconnectQos = new DisconnectQos(this.glob, glob.getDisconnectQosFactory().readObject(xmlQos));
      return this.glob.getXmlBlasterAccess().disconnect(disconnectQos);
   }

   /**
    * Subscribe to messages. 
    * @param xmlKey Which message topics to retrieve
    * @param xmlQos Control the behavior and further filter messages with mime based filter plugins
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos, I_Callback)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public SubscribeReturnQos subscribe(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().subscribe(xmlKey, xmlQos, null);
   }

   /**
    * Cancel subscription. 
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#unSubscribe(UnSubscribeKey, UnSubscribeQos)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">interface.unSubscribe requirement</a>
    */
   public UnSubscribeReturnQos[] unSubscribe(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().unSubscribe(
                       new UnSubscribeKey(this.glob, this.glob.getQueryKeyFactory().readObject(xmlKey)), 
                       new UnSubscribeQos(this.glob, this.glob.getQueryQosFactory().readObject(xmlQos)));
   }

   /**
    * Get synchronous messages. 
    * @param xmlKey Which message topics to retrieve
    * @param xmlQos Control the behavior and further filter messages with mime based filter plugins
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">interface.get requirement</a>
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#get(GetKey, GetQos)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public MsgUnit[] get(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().get(
                  new GetKey(this.glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                  new GetQos(this.glob, glob.getQueryQosFactory().readObject(xmlQos)));
   }

  /**
   * @see org.xmlBlaster.client.I_XmlBlasterAccess#getCached(GetKey, GetQos)
   */
   public MsgUnit[] getCached(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().getCached(
                  new GetKey(this.glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                  new GetQos(this.glob, glob.getQueryQosFactory().readObject(xmlQos)));
   }

   //public PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException {
   //   return this.glob.getXmlBlasterAccess().publish(msgUnit);
   //}

   /**
    * Publish a message. 
    * @param xmlKey The message topic
    * @param contentStr The payload as a string
    * @param xmlQos Control the behavior
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#publish(org.xmlBlaster.util.MsgUnit)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public PublishReturnQos publishStr(String xmlKey, String contentStr, String xmlQos) throws XmlBlasterException {
      MsgUnit msgUnit = new MsgUnit(this.glob, xmlKey, contentStr, xmlQos);
      return this.glob.getXmlBlasterAccess().publish(msgUnit);
   }

   /**
    * Publish a message. 
    * @param xmlKey The message topic
    * @param content The payload as binary blob
    * @param xmlQos Control the behavior
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#publish(org.xmlBlaster.util.MsgUnit)
    * @exception XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public PublishReturnQos publishBlob(String xmlKey, byte[] content, String xmlQos) throws XmlBlasterException {
      MsgUnit msgUnit = new MsgUnit(this.glob, xmlKey, content, xmlQos);
      return this.glob.getXmlBlasterAccess().publish(msgUnit);
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">interface.erase requirement</a>
    */
   public EraseReturnQos[] erase(String xmlKey, String xmlQos) throws XmlBlasterException {
      return this.glob.getXmlBlasterAccess().erase(
                  new EraseKey(this.glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                  new EraseQos(this.glob, glob.getQueryQosFactory().readObject(xmlQos)));
   }

   /**
    * Enforced by I_Callback
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Callback update arrived: " + updateKey.getOid());
      return notifyUpdateEvent(cbSessionId, updateKey, content, updateQos);
   }

   /**
    * Dump state of this client connection handler into an XML ASCII string.
    * @return internal state
    */
   public String toXml() {
      return this.glob.getXmlBlasterAccess().toXml();
   }

   /**
    * For testing: java org.xmlBlaster.client.activex.XmlScriptAccess
    */
   public static void main(String args[]) {
      try {
         final XmlScriptAccess access = new XmlScriptAccess();
         //access.activateUpdateConsumer(true);
         Properties props = access.createPropertiesInstance();
         props.put("protocol", "SOCKET");
         //props.put("trace", "true");
         access.initialize(props);
         class TestUpdateListener implements UpdateListener {
            public void update(UpdateEvent updateEvent) {
               System.out.println("TestUpdateListener.update: " + updateEvent.getKey());
               access.setUpdateReturn("<qos><state id='OK'/></qos>");
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
         //UpdateMsgUnit[] msgs = access.consumeUdateMessages();
         //for(int i=0; i<msgs.length; i++) {
         //   System.out.println("Access queued update message '" + msgs[i].getUpdateKey().toXml() + "'");
         //}
         //msgs = access.consumeUdateMessages();
         //if (msgs.length != 0)
         //   System.out.println("ERROR: queued update message not consumed properly");

         System.out.println("***** Publishing ...");
         PublishReturnQos ret = access.publishStr("<key oid='test'/>", "Bla", "<qos/>");
         System.out.println("***** Published message ret=" + ret.getState());
         Thread.sleep(2000);
         response = access.sendRequest("<xmlBlaster>disconnect/></xmlBlaster>");
      }
      catch (Throwable e) {
         System.out.println("ERROR: Caught exception: " + e.toString());
      }
   }
}

