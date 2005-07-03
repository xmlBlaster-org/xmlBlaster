/*------------------------------------------------------------------------------
Name:      XmlBlasterConnector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;


import org.jutils.log.*;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.client.key.UpdateKey;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;

import org.xmlBlaster.client.qos.ConnectReturnQos;
                                                         
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;

import org.xmlBlaster.util.qos.address.Address;

import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.SubscribeKey;
import java.io.IOException;

import java.util.Properties;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.authentication.SessionInfo;
import javax.management.*;


/**
 * XmlBlasterConnector is a tiny embedded xmlBlaster server instance. 
 * It is both publisher and subscriber to a topic.
 * XmlBlasterConnector extracts MethodInvocation Object from the Message Unit and
 * handles the invocation, stores the return value
 */
public class XmlBlasterConnector implements XmlBlasterConnectorMBean, I_Callback {

  private String ME = "XmlBlasterConnector";
  private I_XmlBlasterAccess xmlBlasterAccess;
  private MBeanServer server = null;
  private SerializeHelper serHelp;
  private LogChannel log;
  private Global glob = null;

  private EmbeddedXmlBlaster embeddedXmlBlaster = null;

  private static int port = 3424;

  public static int UNKNOWN = -9999;
  public static int SENDING = 1000;
  public static int FINISHED = 1;

  public boolean jmxInterfaceAlive = false;

  public XmlBlasterConnector() {
    this.glob = new Global();
    this.log = this.glob.getLog("jmx");
    serHelp = new SerializeHelper(glob);
  }

  /* THIS SHOULD NOT BE USED SINCE THE Global SHOULD NOT BE PASSED FROM THE SERVER 
  public XmlBlasterConnector(Global glob) {
    if (this.glob == null) this.glob = new Global();
    this.log = this.glob.getLog("jmx");
    serHelp = new SerializeHelper(glob);

  }
  */

/**
 * Starts the connector - starts an embedded xmlBlaster that listens on port 3424
 * Subscribes to topic where MethodInvocations are placed
 * Starts another (new!) MBeanServer
 */
  public void start(String agentID) {
    server = MBeanServerFactory.createMBeanServer(agentID);
    log.info(ME,"external Start of MBeanAdaptor, new MBeanServer...");
    log.warn(ME,"Starting external....");
    startEmbeddedXmlBlasterServer();
  }

  /**
   * Starts the connector - starts an embedded xmlBlaster that listens on port 3424
   * Subscribes to topic where MethodInvocations are placed
   * Uses MBeanServer from JMXWrapper
   */
  public void startInternal(MBeanServer server) {
    this.server = server;
    log.info(ME,"internal Start of MBeanAdaptor, MBeanServer reused... "+ server);
    startEmbeddedXmlBlasterServer();
  }

/**
 * Start EmbeddedXmlBlaster
 */
  public void startEmbeddedXmlBlasterServer() {
    try {
      log.info(ME, "Starting new Embedded xmlBlaster responsible for internal jmx-messages on port " + port);

      Global localServerGlob = new Global();

      Properties prop = new Properties();
      prop.setProperty("bootstrapPort","3424");
      prop.setProperty("bootstrapHostname","127.0.0.1");
      prop.setProperty("SecurityServer.Plugin","NONE");
      prop.setProperty("xmlBlaster.jmx.XmlBlasterAdaptor","false");
      prop.setProperty("admin.remoteconsole.port","0");
      prop.setProperty("plugin/socket/port","0");
      prop.setProperty("plugin/xmlrpc/port","0");
      prop.setProperty("plugin/rmi/registryPort","0");
      prop.setProperty("cluster","false");

//      prop.setProperty("trace", "true");
//      prop.setProperty("call", "true");
//      prop.setProperty("dump", "true");

      localServerGlob.init(prop);
      embeddedXmlBlaster = EmbeddedXmlBlaster.startXmlBlaster(localServerGlob);
      glob.init(prop);

      //connect to embedded xmlBlaster
      Address addr = new Address(glob);
      addr.setBootstrapPort(port);

      glob.setBootstrapAddress(addr);
      this.xmlBlasterAccess = glob.getXmlBlasterAccess();

      log.info(ME,"Connecting to embedded xmlBlaster on port "+ port);

      if (this.xmlBlasterAccess != null) jmxInterfaceAlive=true;
      log.info(ME, "Registered new xmlBlasterConnector, running on "+glob.getBootstrapAddress().getBootstrapUrl());
      log.info(ME,"registering new topic \"xmlBlasterMBeans\"");
      SubscribeKey subKey = new SubscribeKey(this.glob, "xmlBlasterMBeans_Invoke");

      SubscribeQos sQos = new SubscribeQos(this.glob);
      sQos.setWantLocal(false);
      ConnectQos qos = new ConnectQos(glob, "InternalConnector", "connector");

      ConnectReturnQos rQos = this.xmlBlasterAccess.connect(qos, this);
      log.info(ME,"Connect: " + rQos.toString());
      this.xmlBlasterAccess.subscribe(subKey, sQos);
      log.info(ME,"internal JMX Connector ready and waiting...");
    }
    catch (Exception ex) {
      log.error(ME,"Error when invoking internal Server for Connector: " + ex.toString());
      ex.printStackTrace();
    }

  }

  /**
   * update() is invoked, when a methodInvocation for the given key arrives
   * MethodInvocation is deserialized, the method is invoked and the return value
   * is stored (within the MethodInvocation Object)
   */
  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
  {
  log.info(ME, "Received asynchronous message \"update()\" ServerSide '" +
                     updateKey.getOid() + "' state=" + updateQos.getState() + " from xmlBlaster");

  //extract MethodInvocation from received Message
  MethodInvocation mi = null;
  log.info(ME,"Is the Server available ? - " + server.getDefaultDomain() );
  try {
    mi = (MethodInvocation) serHelp.deserializeObject(content);
    log.info(ME,"Called Method: " + mi.getMethodName());
  }
  catch (Exception ex) {
    log.error(ME,"Error when trying to expand MethodInvocationObject " + ex.toString());
  }


//  if (  mi.getTargetHost().equalsIgnoreCase(this.glob.getCbHostname()) ) {

    String ID = mi.getId();
    log.info(ME, "MethodInvocationID on server:  " + ID);

    //The local MBeanServer is placed into the MethodInvocationObject
    mi.setMBeanServer(server);

    mi.setTargetHost(glob.getLocalIP());

    //invoke Method within MethodInvocationObject. Within MethodInvocation the real invocation is forwarded
    //to the previously set MBeanServer reference<br>
    //Invoke() stores the return value the the MethodInvocation-Object
    //The return value can be retrieved by calling the
    //getReturnValue()-Method
    mi.invoke();

    //Cases that cannot be handeled within MethodInvocation:

    //addNotificationListener
    if (mi.getMethodName().equals("addNotificationListener"))
    {
      addNotification(mi);
    }

    //removeNotificationHandler
    if (mi.getMethodName().equals("removeNotificationListener"))
    {
      removeNotification(mi);
    }

    //re-set ID
    mi.setId(ID);

    log.info(ME,"Status of the MethodInvocation ? " + mi.getStatus());


    //re-publish new MethodInvocation Object
    try {
      log.info(ME,"Trying to connect as internal user");
      ConnectQos qos = new ConnectQos(glob, "InternalConnector", "connector");
      log.info(ME,"Trying to republish MethodInvocationObject again...");
      PublishReturnQos rqos = this.xmlBlasterAccess.publish(new MsgUnit("<key oid='xmlBlasterMBeans_Return'/>", serHelp.serializeObject(mi),"<qos/>"));
    }
    catch (Exception ex) {
      ex.printStackTrace();
      log.error(ME, "Error when trying to republish MethodInvocation " + ex.toString());
      ex.printStackTrace();
    }
/*  }
  else log.info(ME,"Method for another host!");*/
  return "";
  }

  private void removeNotification(MethodInvocation mi) {
    Object[] params = mi.getParams();
      try {
        server.removeNotificationListener((ObjectName)params[0],
            new NotifListener(this, this.xmlBlasterAccess, glob));
      }
      catch (Exception ex) {
        log.error(ME,"Error when disabling serversided Notification for MBean " + ex);
      }
  }

  private void addNotification(MethodInvocation mi) {
    Object[] params = mi.getParams();
      try {
        server.createMBean((String) params[1], (ObjectName) params[0] );
      }
      catch (Exception ex) {
        log.error(ME,"Error when creating MBean on server for Notification-matters " + ex.toString());
      }

      try {
        server.addNotificationListener((ObjectName)params[0],
            new NotifListener(this, this.xmlBlasterAccess, glob),
                                       (javax.management.NotificationFilter)params[2],
                                       null);
      }
      catch (InstanceNotFoundException ex) {
        log.error(ME,"Error when enabling serversided Notification for MBean " + ex);
      }
  }

  /**
   * Display internal status to Management
   */
  public boolean isConnectorAlive() {
    return jmxInterfaceAlive;
  }

/**
 * Stop Service
 */
  public void stop(String agentId) {
    EmbeddedXmlBlaster.stopXmlBlaster(embeddedXmlBlaster);
    jmxInterfaceAlive=false;
  }

}
