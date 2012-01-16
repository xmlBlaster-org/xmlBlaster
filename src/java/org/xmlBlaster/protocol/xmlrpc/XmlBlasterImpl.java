/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.XmlScriptParser;


/**
 * Implements the xmlBlaster server XMLRPC interface. Because the xml-rpc
 * protocol does not recognize user-defined classes, these must be converted to
 * something which xml-rpc does understand. That's why following transformations
 * will take place:
 * <pre>
 * MsgUnitRaw are converted to Vector
 * MsgUnitRaw[] are converted to Vector (of Vector)
 * String[] are converted to Vector (of String)
 * boolean are converted to int
 * void return is not allowed so we return an empty string instead
 * </pre>
 * <p />
 * @author "Michele Laghi" (michele@laghi.eu)
 */
public class XmlBlasterImpl {
   private final static String ME = XmlBlasterImpl.class.getName();
   private static Logger log = Logger.getLogger(ME);
   private final org.xmlBlaster.protocol.I_XmlBlaster blasterNative;
   private final AddressServer addressServer;
   private final Global glob;
   private Map<String, WeakReference<CallbackXmlRpcDriverSingleChannel>> cbMap;
   private long waitTime;
   /**
    * Constructor.
    */
   public XmlBlasterImpl(Global glob, XmlRpcDriver driver, org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor ...");
      this.glob = glob;
      this.blasterNative = blasterNative;
      this.addressServer = driver.getAddressServer();
      cbMap = new HashMap<String, WeakReference<CallbackXmlRpcDriverSingleChannel>>();
   }


   /**
    * Subscribe to messages.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe() xmlKey=\n"
                                 + xmlKey_literal + ") ...");
      String oid = blasterNative.subscribe(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return oid;
   }


   /**
    * invokeSubscribe to messages.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public Object xmlScriptInvoke(String literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("xmlScriptInvoke: " + literal);
      
      XmlScriptParser parser = new XmlScriptParser();
      final I_ProgressListener progressListener = null;
      final I_PluginConfig pluginConfig = null;
      parser.init(glob, progressListener, pluginConfig);
      InputStream is = new ByteArrayInputStream(literal.getBytes());
      try {
         MsgInfo[] msgInfoArr = parser.parse(is);
         if (msgInfoArr != null) {
            for (int i=0; i < msgInfoArr.length; i++) {
               MsgInfo msgInfo = msgInfoArr[i];
               MethodName method = msgInfo.getMethodName();
               String sessionId = msgInfo.getSecretSessionId();
               MsgUnitRaw[] msgUnitRawArr = msgInfo.getMessageArr();
               String key = null;
               MsgUnitRaw msgUnitRaw = null;
               
               if (msgUnitRawArr != null && msgUnitRawArr.length > 0) {
                  msgUnitRaw = msgUnitRawArr[0];
                  key = msgUnitRaw.getKey();
               }
               String qos = msgInfo.getQos();
               
               if (method.isConnect()) {
                  
               }
               else if (method.isDisconnect()) {
                  
               }
               else if (method.isErase()) {
                  return erase(sessionId, key, qos);
               }
               else if (method.isGet()) {
                  final String asString = "true";
                  return get(sessionId, key, qos, asString);
               }
               else if (method.isPublish()) {
                  byte[] content = null;
                  if (msgUnitRaw != null)
                     content = msgUnitRaw.getContent();
                  return publish(sessionId, key, content, qos);
               }
               else if (method.isPublishArray()) {
                  String[] strArr = blasterNative.publishArr(addressServer, sessionId, msgUnitRawArr);
                  return ProtoConverter.stringArray2Vector(strArr);
               }
               else if (method.isPublishOnway()) {
                  blasterNative.publishOneway(addressServer, sessionId, msgUnitRawArr);
                  return "";
               }
               else if (method.isSubscribe()) {
                  return subscribe(sessionId, key, qos);
               }
               else if (method.isUnSubscribe()) {
                  return unSubscribe(sessionId, key, qos);
               }
               else { // how to handle pings !!!!!!
                  // log.warning("The method '" + method.getMethodName() + "' is not implemented");
                  return "OK";
               }
               return null;
            }
         }
         
         
      }
      catch (IOException ex) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "An exception occured when parsing the string " + literal);
      }
      
      return null;
   }


   /**
    * void return is not allowed so we return an empty string instead
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">The interface.unSubscribe requirement</a>
    */
   public Vector unSubscribe(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe() xmlKey=\n" + xmlKey_literal + ") ...");
      String[] retArr = blasterNative.unSubscribe(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return ProtoConverter.stringArray2Vector(retArr);
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish (String sessionId, String xmlKey_literal, byte[] content,
         String publishQos_literal)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish() ...");
      MsgUnitRaw msgUnit = new MsgUnitRaw(xmlKey_literal, content, publishQos_literal);
      return blasterNative.publish(this.addressServer, sessionId, msgUnit);
   }


   /**
    * This variant allows to publish simple string based messages
    * (the content is a string).
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish (String sessionId, String xmlKey_literal, String content,
                          String publishQos_literal)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish() ....");

      MsgUnitRaw msgUnit = new MsgUnitRaw(xmlKey_literal, content.getBytes(), publishQos_literal);

//      // convert the xml literal strings
//      PublishQos publishQos = new PublishQos(publishQos_literal);

      String retVal = blasterNative.publish(this.addressServer, sessionId, msgUnit);
      return retVal;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish (String sessionId, Vector msgUnitWrap)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish() ...");

      MsgUnitRaw msgUnit = ProtoConverter.vector2MsgUnitRaw(msgUnitWrap);

      //PublishQos publishQos = new PublishQos(msgUnit.getQos());

      // String retVal = blasterNative.publish(sessionId, xmlKey, msgUnit, publishQos);
      String retVal = blasterNative.publish(this.addressServer, sessionId, msgUnit);
      return retVal;
   }




   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   // public Vector publishArr(String sessionId, Vector msgUnitArrWrap) throws XmlBlasterException {
   public Vector publishArr(String sessionId, Object[] msgUnitArrWrap) throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("Entering publishArr() for " + msgUnitArrWrap.length + " entries ...");
      int arrayLength = msgUnitArrWrap.length;

      if (arrayLength < 1) {
         if (log.isLoggable(Level.FINE))
            log.fine("Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
         return new Vector(); // empty Vector return
      }

      try {
         if (msgUnitArrWrap != null) {
            MsgUnitRaw[] msgUnitArr = ProtoConverter.objMatrix2MsgUnitRawArray(msgUnitArrWrap);
            String[] strArr = blasterNative.publishArr(this.addressServer, sessionId, msgUnitArr);
            return ProtoConverter.stringArray2Vector(strArr);
         }
         else 
            throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, ME, "Empty array to be published");
      }
      catch (ClassCastException e) {
         log.severe("not a valid MsgUnitRaw: " + e.toString());
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, ME, "Not a valid Message Unit: Class Cast Exception", e);
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public void publishOneway(String sessionId, Vector msgUnitArrWrap)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishOneway() for " + msgUnitArrWrap.size() + " entries ...");
      int arrayLength = msgUnitArrWrap.size();

      if (arrayLength < 1) {
         if (log.isLoggable(Level.FINE)) log.fine("Entering xmlBlaster.publishOneway(), nothing to do, zero msgUnits sent");
         return;
      }

      try {
         MsgUnitRaw[] msgUnitArr = ProtoConverter.vector2MsgUnitRawArray(msgUnitArrWrap);
         blasterNative.publishOneway(this.addressServer, sessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.severe("Caught exception which can't be delivered to client because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
    */
   public Vector erase(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering erase() xmlKey=\n" + xmlKey_literal + ") ...");

      String[] retArr = blasterNative.erase(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return ProtoConverter.stringArray2Vector(retArr);
   }

   public Vector get(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      return get(sessionId, xmlKey_literal, qos_literal, null);
   }

   /**
    * Synchronous access
    * @return content
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
    */
   public Vector get(String sessionId, String xmlKey_literal, String qos_literal, String asString)
      throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      MsgUnitRaw[] msgUnitArr = blasterNative.get(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      // convert the MsgUnitRaw array to a Vector array
      boolean contentAsString = asString != null && "true".equals(asString);
      Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(contentAsString, msgUnitArr);

      return msgUnitArrWrap;
   }


   /**
    * Synchronous request for updates (simulates an asynchronous update)
    * @return content
    */
   public Object[] updateRequest(String sessionId, String waitTimeTxt, String asString) throws XmlBlasterException {
      
      if (log.isLoggable(Level.FINER)) 
         log.finer("Entering updateRequest() waitTime = " + waitTime + " ms ...");
      // check in the own sessionId pipe if there is some data available, if not wait some time and then
      // return
      // if there are some messages available send them to the client and await an acknowledge or an
      // exception from him.
      if (sessionId == null)
         throw new XmlBlasterException(Global.instance(), ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "no sessionId defined");

      CallbackXmlRpcDriverSingleChannel cb = null;
      synchronized(cbMap) {
         WeakReference<CallbackXmlRpcDriverSingleChannel> tmp = null;
         tmp = cbMap.get(sessionId);
         if (tmp == null) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "The callback is null (shutdown?)");
            // return null;
         }
         cb = tmp.get();
      }
      
      Object[] ret = new Object[3];
      // 0: methodName
      // 1: refId
      // 2: Vector containing data
      long tmpWaitTime = Long.parseLong(waitTimeTxt);
      long pingInterval = cb.getPingInterval();
      long halfTime = pingInterval/2;
      if (pingInterval > 0 && tmpWaitTime > halfTime)
         waitTime = halfTime;
      else
         waitTime = tmpWaitTime;
         
      boolean contentAsString = asString != null && "true".equals(asString);
      
      if (cb != null) {
         try {
            cb.setCurrentThread(Thread.currentThread());
            cb.respan("XmlBlasterImpl.updateRequest-before.blocking");
            try {
               while (true) {
                  LinkedBlockingQueue<UpdateEvent> updQueue = cb.getUpdateQueue();
                  if (updQueue == null)
                     throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "The callback is shutdown");
                  UpdateEvent ue = (UpdateEvent)updQueue.poll(this.waitTime, TimeUnit.MILLISECONDS);
                  if (ue != null) {
                     String methodName = ue.getMethod();
                     long refId = ue.getUniqueId();
                     ret[0] = methodName;
                     ret[1] = "" + refId;
                     if ("update".equals(methodName))  {
                        ret[2] = ProtoConverter.messageUnitArray2Vector(contentAsString, ue.getMsgUnit());
                        cb.respan("XmlBlasterImpl.updateRequest-after-update");
                        return ret;
                     }
                     else if ("updateOneway".equals(methodName)) {
                        ret[2] = ProtoConverter.messageUnitArray2Vector(contentAsString, ue.getMsgUnit());
                        cb.respan("XmlBlasterImpl.updateRequest-after-updateOneway");
                        return ret;
                     }
                     else {
                        log.severe("The method '" + methodName + "' is unkown in this context and should not occur, will ignore it");
                     }
                  }
                  else {
                     cb.respan("XmlBlasterImpl.updateRequest-no-event");
                     return null; // better chance next time
                  }
               }
            }
            catch (InterruptedException ex) {
               // removeCallback(sessionId);
               // throw new XmlBlasterException(glob, ErrorCode.INTERNAL_INTERRUPTED, ME, "The update has been interrupted");
               return null;
            }
         }
         finally {
            cb.setCurrentThread(null);
         }
      }
      else {
         removeCallback(sessionId, "callback was null, i.e. garbage collector has removed it when it still was in map");
         throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_ILLEGALSTATE, ME, "No callback found for session " + sessionId);
      }
   }
   

   
   public void interrupt(String sessionId) {
      WeakReference<CallbackXmlRpcDriverSingleChannel> tmp = cbMap.get(sessionId);
      if (tmp == null)
         return;
      CallbackXmlRpcDriverSingleChannel cb = tmp.get();
      if (cb != null)
         cb.interrupt();
   }

   public String shutdownCb(String sessionId) {
      if (log.isLoggable(Level.FINER))
         log.finer("Entering shutdownCb() sessionId = " + sessionId);
      // notify the blocking update that the client has terminated to handle its work.
      WeakReference<CallbackXmlRpcDriverSingleChannel> tmp = cbMap.get(sessionId);
      if (tmp == null)
         return "";
      
      CallbackXmlRpcDriverSingleChannel cb = tmp.get();
      if (cb != null)
         cb.interrupt();
      return "";
   }
   
   /**
    * Synchronous request for updates (simulates an asynchronous update)
    * 
    * @return dummy to make the xmlrpc happy (if it where null it would not be registered).
    */
   public String updateAckOrException(String sessionId, String reqId, Object[] ack, String ex) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("Entering updateAckOrException() ack = " + ack + " ex " + ex);
      // notify the blocking update that the client has terminated to handle its work.
      WeakReference<CallbackXmlRpcDriverSingleChannel> tmp = cbMap.get(sessionId);
      if (tmp == null)
         return "";
      
      CallbackXmlRpcDriverSingleChannel cb = tmp.get();
      if (cb != null) {
         try {
            cb.respan("XmlBlasterImpl.ack-before-waiting");
            UpdateEvent ue = null;
            if (ex != null && ex.length() > 0) { // ex
               // String method, MsgUnitRaw[] msgUnit, String qos, String[] ret, long uniqueId
               ue = new UpdateEvent("exception", null, ex, null, Long.parseLong(reqId));
            }
            else { // ack
               String[] ackTxt = new String[ack.length];
               for (int i=0; i < ackTxt.length; i++)
                  ackTxt[i] = (String)ack[i];
               ue = new UpdateEvent("ack", null, null, ackTxt, Long.parseLong(reqId));
            }
            cb.getAckQueue().offer(ue, waitTime, TimeUnit.MILLISECONDS);
         }
         catch (InterruptedException e) {
            e.printStackTrace();
         }
         catch (XmlBlasterException e) {
            e.printStackTrace();
         }
      }
      return "";
   }


   /**
    * Test the xml-rpc connection and if xmlBlaster is available for requests.
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   public String ping(String qos) throws XmlBlasterException {
      return blasterNative.ping(this.addressServer, qos);
   }

   //   public String toXml() throws XmlBlasterException;

   public String toXml(String extraOffset) throws XmlBlasterException {
      return blasterNative.toXml(extraOffset);
   }
   
   
   public void registerSessionId(String sessionId, boolean singleChannel, boolean useCDATA) throws XmlBlasterException {
      CallbackXmlRpcDriver
         cb = (CallbackXmlRpcDriver)glob.removeFromWeakRegistry(Thread.currentThread());
      if (cb != null) {
         cb.postInit(sessionId, this, singleChannel, useCDATA);
         // hack to pass the sessionId (the secret session id) to the callback implementation
         // this is needed since there is no way to associate the sessionId with the callback otherwise
         CallbackXmlRpcDriverSingleChannel chDriver = cb.getSingleChannelDriver();
         if (chDriver != null) {
            synchronized(cbMap) {
               cbMap.put(sessionId, new WeakReference<CallbackXmlRpcDriverSingleChannel>(chDriver));
            }
         }
      }
      else
         log.severe("The callback has not been registered for this protocol " + sessionId);
   }
   
   public void removeCallback(String sessionId, String reason) {
      synchronized(cbMap) {
         if (sessionId != null) {
            log.info("Removing Callback for sessionId '" + sessionId + "' for reason: " + reason);
            cbMap.remove(sessionId);
         }
      }
   }
   
   public CallbackXmlRpcDriverSingleChannel getCb(String sessionId) {
      synchronized(cbMap) {
         WeakReference<CallbackXmlRpcDriverSingleChannel> tmp = cbMap.get(sessionId);
         if (tmp == null)
            return null;
         return tmp.get();
      }      
   }
   
}

