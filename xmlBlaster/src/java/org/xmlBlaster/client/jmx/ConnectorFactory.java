/*------------------------------------------------------------------------------
Name:      ConnectorFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.*;

import java.util.*;
import java.lang.reflect.*;
import java.rmi.*;

import javax.naming.*;
import javax.jms.*;
import java.util.ArrayList;
import java.util.Vector;


public class ConnectorFactory  {

  private static ArrayList serverList = null;


  public static AsyncMBeanServer createAsyncConnector(
      String transport, String serverName) throws ConnectorException {

    if (transport.equalsIgnoreCase("xmlBlaster")) {
      try {
        return (AsyncMBeanServer)Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class[] { AsyncMBeanServer.class },
            new XmlBlasterInvocationHandler(serverName)
        );
      }
      catch (Exception e) {
        e.printStackTrace();
        throw new ConnectorException(
            "Error connecting to xmlBlaster Service", e
        );
      }
    }
    else
    throw new ConnectorException("Unknown transport " +
        transport);
  }

  public static String[] getMBeanServerList() {

    AsyncMBeanServer myServer = null;
    Object obj = null;

    try {
      ObjectName requestBroker_name = new ObjectName("xmlBlaster:name=requestBroker");
      myServer = createAsyncConnector("xmlBlaster", "127.0.0.1");
      myServer.createMBean("org.xmlBlaster.engine.RequestBroker",requestBroker_name);
      obj = myServer.getAttribute(requestBroker_name,"NodeList").get();
    }
    catch (Exception ex) {
    }
    return new String[]{obj.toString()};
  }

}
