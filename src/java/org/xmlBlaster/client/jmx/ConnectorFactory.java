/*------------------------------------------------------------------------------
Name:      ConnectorFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.*;

import java.lang.reflect.*;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import java.util.TreeMap;
import java.util.Iterator;

public class ConnectorFactory  {

   private static ConnectorFactory singletonFactory;
   private static Global applicationGlobal;
   private static Object syncObject = new Object();

   private Global global;
   private static Logger log = Logger.getLogger(ConnectorFactory.class.getName());
   private TreeMap childGlobals;
   private TreeMap servers;

   public static ConnectorFactory getInstance(Global global) {
      if (singletonFactory == null) {
         synchronized(syncObject) {
            if (singletonFactory == null) {
               singletonFactory = new ConnectorFactory(global);
               applicationGlobal = global;
            }
         }
      }
      if (global != applicationGlobal) {
         log.severe("getInstance: The global used for this invocation is not the same as the application global");
         Thread.dumpStack();
      }
      return singletonFactory;
   }

   private ConnectorFactory(Global global) {
      this.global = global;

      this.childGlobals = new TreeMap();
      this.servers = new TreeMap();
   }


   /**
    * Creates an instance of an AsyncMBeanServer. 
    * @param transport the string identifying the communication type. Currently only
    * xmlBlaster is supported.
    * @param serverName the name of the server to create. If null is passed, '127.0.0.1' is
    * assumed.
    */
   private synchronized AsyncMBeanServer addAsyncConnector(String transport, String serverName) 
      throws ConnectorException {
      AsyncMBeanServer server = null;

      if (transport.equalsIgnoreCase("xmlBlaster")) {
         Global childGlobal = this.global.getClone(null);
         this.childGlobals.put(serverName, childGlobal);
         try {
            server = (AsyncMBeanServer)Proxy.newProxyInstance(
               Thread.currentThread().getContextClassLoader(),
               new Class[] { AsyncMBeanServer.class },
               new XmlBlasterInvocationHandler(serverName, childGlobal));
            this.servers.put(serverName, server);
            return server;
         }
         catch (Exception e) {
            e.printStackTrace();
            throw new ConnectorException("Error connecting to xmlBlaster Service", e);
         }
      }
      else throw new ConnectorException("Unknown transport " + transport);
   }

   /** 
    * returns the async mbean server within this child global. It returns null if no one
    * has been added yet.
    */
   private AsyncMBeanServer getExistingAsyncConnector(String serverName) {
      return (AsyncMBeanServer)this.servers.get(serverName);
   }

   /**
    * Gets the async mbean server specified with the given name.
    * @param global is the parent global, i.e. the global configured on application start.
    */
   public AsyncMBeanServer getMBeanServer(String serverName) throws ConnectorException {
      String transport = "xmlBlaster"; // in future it could be passed in the args.
      AsyncMBeanServer server = getExistingAsyncConnector(serverName);
      try {
         if (server == null) {
            server = addAsyncConnector(transport, serverName);
            ObjectName requestBroker_name = new ObjectName(transport + ":name=requestBroker");
            server.createMBean("org.xmlBlaster.engine.RequestBroker",requestBroker_name);
         }
      }
      catch (Exception ex) {
         log.severe("Exception occured in 'getMBeanServers': " + ex.getMessage());
         ex.printStackTrace();
         throw new ConnectorException("Error when retreiving mbean server ", ex);
      }
      return server;
   }


   public String[] getMBeanServerList() {
      Iterator iter = this.servers.values().iterator();
      String[] ret = new String[this.servers.size()];
      int i=0;
      try {
         while (iter.hasNext()) {
            AsyncMBeanServer server = (AsyncMBeanServer)iter.next();
            ObjectName requestBroker_name = new ObjectName("xmlBlaster:name=requestBroker");
            ret[i] = server.getAttribute(requestBroker_name,"NodeList").get().toString();
            i++;
         }
      }
      catch(Exception ex) {
         log.severe("Exception occured in 'getMBeanServerList': " + ex.getMessage());
         ex.printStackTrace();
      }
      return ret;
   }

}
