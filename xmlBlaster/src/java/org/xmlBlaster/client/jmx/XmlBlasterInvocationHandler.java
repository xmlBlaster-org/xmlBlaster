/*------------------------------------------------------------------------------
Name:      XmlBlasterInvocationHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import java.lang.reflect.*;
import java.util.*;


import org.jutils.log.LogChannel;
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
  private LogChannel log;
  private I_XmlBlasterAccess returnCon;
  private I_XmlBlasterAccess invokeCon;
  private String ME = "XmlBlasterInvocationHandler";
  private static int port = 3424;

  private Map callbackBuffer = Collections.synchronizedMap(new HashMap());

  public XmlBlasterInvocationHandler(String serverName) {
    this.global = new Global().instance();
    this.log = this.global.getLog("jmx");
    log.info(ME,"XmlBlasterInvocationHandler called");

    try {
      Address addr = new Address(global);

      Properties prop = new Properties();
      prop.setProperty("bootstrapHostname",serverName);
      prop.setProperty("bootstrapPort","3424");

      global.init(prop);
      invokeCon = global.getXmlBlasterAccess();
      returnCon = global.getXmlBlasterAccess();

      log.info(ME,"Connecting to embedded xmlBlaster on port "+ port +" Adresse " + addr.getBootstrapUrl());
      ConnectQos qos = new ConnectQos(global, "InternalConnector", "connector");
      returnCon.connect(qos, this);
      SubscribeKey subKey = new SubscribeKey(this.global, "xmlBlasterMBeans_Return");

      SubscribeQos sQos = new SubscribeQos(this.global);
      sQos.setWantLocal(false);
      returnCon.subscribe(subKey, sQos);
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
    log.info(ME,"Invoke() within XmlBlasterInvocationHandler called - Method called: " + method);
    MethodInvocation mi = new MethodInvocation(method);
    mi.setParams(args);

    String ID = "" + messageID++;
    mi.setId(ID);
    log.info(ME,"Put MethodInvocation-ID into callback-buffer in order to rematch again! " + mi.getId() );
    Callback cb = new XmlBlasterCallback(ID, mi);
    callbackBuffer.put(ID, cb);

    if (method.getName().equals("close")) {
      close();
      return null;
    }

    ser = new SerializeHelper(new Global());
    PublishReturnQos rqos = invokeCon.publish(new MsgUnit("<key oid='xmlBlasterMBeans_Invoke'/>",ser.serializeObject(mi),"<qos/>"));
    log.info(ME,"Returning callback-object: " + cb);
    return cb;
  }


  private void close() {
    invokeCon.disconnect(new DisconnectQos());
    returnCon.disconnect(new DisconnectQos());
    log.info(ME,"Disconnecting from xmlBlaster....");
  }

/**
 * Update invoked, when Message on subscribed Topic is received.
 * Most probably it is the returning Object from a recent MethodInvocation
 */
  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
    System.out.println("\nReceived asynchronous message in \"update()\" clientSide '" +
                       updateKey.getOid() + "' state=" + updateQos.getState() + " from xmlBlaster - extracting MethodInvocation...");

    //get MI from byteArray
    MethodInvocation mi = null;
    SerializeHelper serHelp = new SerializeHelper(global);
    try {
      mi = (MethodInvocation) serHelp.deserializeObject(content);
      log.info(ME,"Method received: " + mi.getMethodName());
    }
    catch (Exception ex) {
      log.error(ME,"Error when trying to expand MethodInvocationObject " + ex.toString());
      ex.printStackTrace();
    }
    String ID = mi.getId();
    log.info(ME," ID from MethodInvocation that maps callback-buffer: " + ID);
    XmlBlasterCallback cb = (XmlBlasterCallback) callbackBuffer.get(ID);
//    log.info(ME," Whats in Received Object?? - " + mi.getReturnValue());
    if (cb != null ) cb.setMethodInvocation(mi);
    return "";
   }
}