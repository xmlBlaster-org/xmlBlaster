/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import javax.management.*;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.jmx.AsyncMBeanServer;
import org.xmlBlaster.client.jmx.ConnectorFactory;

import org.xmlBlaster.client.jmx.ConnectorException;
import java.rmi.RemoteException;

/**
 * Class that encapsulates Communication to xmlBlaster / MBeanServer from GUI
 */
public class ConnectorClient {

  private LogChannel log = null;
  private Global glob = null;
  private static AsyncMBeanServer aServer = null;
  private final static String ME = "ConnectorClient";
  private ConnectorFactory connectorFactory;

  public ConnectorClient(Global glob, String serverName) {
    this.glob = glob;
    // should we allow to pass null here for the global ? 
    // If yes, then we should have a clear separation of the non-xmlBlaster code
    if (this.glob == null) this.glob = Global.instance();

    log = this.glob.getLog("jmxGUI");
    //trying to establish connection to JmxServer
    log.info(ME,"New Connector client - trying to establish connection to JmxServer....");
    try {
      this.connectorFactory = ConnectorFactory.getInstance(this.glob);
//      aServer = connectorFactory.createAsyncConnector("xmlBlaster", serverName);
        aServer = connectorFactory.getMBeanServer(serverName);
    }
    catch (ConnectorException ex) {
      log.error(ME,"Error when creating AsyncMBeanServerInstance " + ex.toString());
      ex.printStackTrace();
    }

//    if ( aServer != null ) {log.info(ME,"Asynchron MBeanServer available: " + aServer.getDefaultDomain());}
//    else {log.error(ME,"Error when creating AsyncMBeanServerInstance - Reference to Object Null");}
  }

  /**
   * Returns AsyncMBeanServer Object. For basic access to MBean-functions within the clients
   * @return
   * @throws ConnectorException
   */
  public AsyncMBeanServer getServer() throws ConnectorException {
    if ( this.aServer != null ) { return this.aServer; }
    else {
      throw new ConnectorException("MBeanServer not available");
    }
  }

/**
 * Returns a list of servers that have a JMX-Server running
 * TODO: implement cluster-lookup from xmlBlaster-lib.
 * JMX-implementation not working correctly
 */
  public String[] getServerList() {
    return this.connectorFactory.getMBeanServerList();
  }

  /**
   * logout from Server
   */
  public void logout() {
    if (this.log.CALL) this.log.call(ME, "logout");
    if (this.log.DUMP) Thread.currentThread().dumpStack();
    aServer.close();
  }

  /**
   * Returns Information about all attributes that are handeled by the given
   * MBean
   * @param name name of the MBean
   * @return Array of MBeanAttributeInfo
   */
  public MBeanAttributeInfo[] exploreMBeansByObjectName(String name) {
    if (this.log.CALL) this.log.call(ME, "exploreMBeansByObjectName '" + name + "'");
    MBeanInfo mbInfo = getMBeanInfo(name);
    return mbInfo.getAttributes();

  }

  /**
   * Discovers the attributes and operations that an MBean exposes for management.
   * @param name name of the Bean
   * @return MBeanInfo
   */
  private MBeanInfo getMBeanInfo(String name) {
    if (this.log.CALL) this.log.call(ME, "getMBeanInfo '" + name + "'");
    MBeanInfo mbInfo = null;
    ObjectName RequestBroker = null;
    if (aServer != null ) {
      try {
      RequestBroker = new ObjectName(name);
      mbInfo = (MBeanInfo) aServer.getMBeanInfo(RequestBroker).get();
      }
      catch (Exception ex) {
        log.error(ME,"Error when retrieving information about " + name  +" >> " + ex.toString());
        ex.printStackTrace();
      }
    }
    else log.error(ME,"Lost AsyncServer!");
    return mbInfo;
  }

  /**
   * Discovers the Operations that an MBEan exposes for management
   * @param name name of the Bean
   * @return Array of MBeanOperationInfo
   */
  public MBeanOperationInfo[] exploreMBeanOperationsByObjectName(String name) {
    if (this.log.CALL) this.log.call(ME, "exploreMBeanOperationsByObjectName '" + name + "'");
    MBeanInfo mbInfo = getMBeanInfo(name);
    return mbInfo.getOperations();
  }
}
