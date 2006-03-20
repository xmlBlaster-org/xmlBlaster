/*------------------------------------------------------------------------------
Name:      XmlBlasterInvocationHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import java.lang.reflect.*;
import java.util.*;


import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.key.*;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.*;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.admin.extern.*;
import org.xmlBlaster.util.qos.address.*;


public class XmlBlasterInvocationHandler implements I_Callback, java.lang.reflect.InvocationHandler  {

  static SerializeHelper ser;
  static long messageID = -1;
  private SubscribeKey subsKey = null;
  private final Global global;
   private static Logger log = Logger.getLogger(XmlBlasterInvocationHandler.class.getName());
  private I_XmlBlasterAccess xmlBlasterAccess;
  private String ME = "XmlBlasterInvocationHandler";
  private static int port = 3424;

  private Map callbackBuffer = Collections.synchronizedMap(new HashMap());

  public XmlBlasterInvocationHandler(String serverName, Global global) {
    this.global = global;
//    this.global = Global.instance();

    if (log.isLoggable(Level.FINER)) 
       log.severe("Constructor for '" + serverName + "'");

    log.info("XmlBlasterInvocationHandler called");

    try {
      Address addr = new Address(global);

      Properties prop = new Properties();
      prop.setProperty("bootstrapHostname",serverName);
      prop.setProperty("bootstrapPort","3424");

      global.init(prop);
      this.xmlBlasterAccess = global.getXmlBlasterAccess();

      log.info("Connecting to embedded xmlBlaster on port "+ port +" Address " + addr.getBootstrapUrl());
      if (!this.xmlBlasterAccess.isConnected()) {
         ConnectQos qos = new ConnectQos(this.global, "InternalConnector", "connector");
         this.xmlBlasterAccess.connect(qos, this);
      }
      SubscribeKey subKey = new SubscribeKey(this.global, "xmlBlasterMBeans_Return");

      SubscribeQos sQos = new SubscribeQos(this.global);
      sQos.setWantLocal(false);
      this.xmlBlasterAccess.subscribe(subKey, sQos);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

  }

  /**
   * Method Invoke is called by the client,
   * The MethodName is extracted and wrapped into an MethodInvocation Object
   * <p>
   * The MethodInvocation is published to the the server.<br>
   * The Mapping for the return values after the invocation is done via a callbackbuffer
   */
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    log.info("invoke: within XmlBlasterInvocationHandler called - Method called: " + method);
    MethodInvocation mi = new MethodInvocation(method);
    mi.setParams(args);

    String ID = "" + messageID++;
    mi.setId(ID);
    log.info("invoke: Put MethodInvocation-ID into callback-buffer in order to rematch again '" + mi.getId() + "' and method '" + method.getName() + "'");
    Callback cb = new XmlBlasterCallback(ID, mi);
    callbackBuffer.put(ID, cb);

    if (method.getName().equals("close")) {
      if (log.isLoggable(Level.FINEST)) {
         log.finest("invoke 'close': ");
         Thread.currentThread().dumpStack();
      }
      close();
      return null;
    }              

    ser = new SerializeHelper(this.global);
    PublishReturnQos rqos = this.xmlBlasterAccess.publish(new MsgUnit("<key oid='xmlBlasterMBeans_Invoke'/>",ser.serializeObject(mi),"<qos/>"));
    log.info("invoke: Returning callback-object: " + cb);
    return cb;
  }


  synchronized private void close() {
//   this.xmlBlasterAccess.disconnect(new DisconnectQos());
    log.severe("Disconnecting from xmlBlaster.... (not really disconnecting)");
  }

/**
 * Update invoked, when Message on subscribed Topic is received.
 * Most probably it is the returning Object from a recent MethodInvocation
 */
  synchronized public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
    if (log.isLoggable(Level.FINE)) this.log.fine("update: Received asynchronous message in \"update()\" clientSide '" +
                       updateKey.getOid() + "' state=" + updateQos.getState() + " from xmlBlaster - extracting MethodInvocation...");

    //get MI from byteArray
    MethodInvocation mi = null;
    SerializeHelper serHelp = new SerializeHelper(global);
    try {
      mi = (MethodInvocation) serHelp.deserializeObject(content);
      log.info("update: Method received: " + mi.getMethodName());
    }
    catch (Exception ex) {
      log.severe("update: Error when trying to expand MethodInvocationObject " + ex.toString());
      ex.printStackTrace();
    }
    String ID = mi.getId();
    log.info(" update: ID from MethodInvocation that maps callback-buffer: " + ID);
    XmlBlasterCallback cb = (XmlBlasterCallback) callbackBuffer.get(ID);
    if (log.isLoggable(Level.FINE)) {
       log.fine(" update: Whats in Received Object?? - " + mi.getReturnValue());
       log.fine("update: the callback with ID '" + ID + "' is '" + cb + "'");
    }
    if (cb != null ) cb.setMethodInvocation(mi);
    return "";
   }
}
