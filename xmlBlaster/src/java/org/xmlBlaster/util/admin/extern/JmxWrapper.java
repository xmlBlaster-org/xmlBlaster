/*------------------------------------------------------------------------------
Name:      JmxWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.ErrorCode;

import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;

import com.sun.jdmk.comm.HtmlAdaptorServer;

import com.sun.jmx.trace.Trace;



/**
 * JmxWrapper wraps the MBeanServer instance and a HTTPAdaptor.
 */
public class JmxWrapper
{
   private final Global glob;
   private final LogChannel log;
   private final String ME;
   private static MBeanServer mbeanServer;
   private static HtmlAdaptorServer html;
   private final static String NAME_PREFIX = "xmlBlaster:name=";

   private MBeanServer getMBeanServer() {
      if (this.mbeanServer == null) {
         synchronized (this) {
            if (this.mbeanServer == null) {
               this.mbeanServer = MBeanServerFactory.createMBeanServer();
            }
         }
      }
      return this.mbeanServer;
   }

   private HtmlAdaptorServer getHtmlAdaptorServer() {
      if (this.html == null) {
         synchronized (this) {
            if (this.html == null) {
               this.html = new HtmlAdaptorServer();
            }
         }
      }
      return this.html;
   }


   /**
    * Constructs an initial JmxWrapper object.
    */
   public JmxWrapper(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("jmx");
      this.ME = "JmxWrapper" + this.glob.getLogPrefixDashed();
      init();
   }

   public void init() throws XmlBlasterException {
      if (this.mbeanServer == null) {
         synchronized (this) {
            if (this.mbeanServer == null) {
               try {
                  this.mbeanServer = MBeanServerFactory.createMBeanServer(glob.getLocalIP());
                  this.html = new HtmlAdaptorServer();
                  ObjectName html_name = new ObjectName("Adaptor:name=html,port=8082");
                  this.mbeanServer.registerMBean(this.html, html_name);
                  log.info(ME, "Registered with JMX server, activated HTML adaptor on http://"+glob.getLocalIP()+":8082");
               }
               catch(Exception ex) {
                  throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, " could not create MBean Server", ex);
               }
               this.html.start();
              try {
                startXmlBlasterConnector(this.mbeanServer);
              }
              catch (XmlBlasterException ex) {
                log.error(ME,"Error when starting xmlBlasterConnector " + ex.toString());
                ex.printStackTrace();
              }
            }
         }
      }
   }


   /**
    * registers the specified mbean into the mbean server.
    * the name you specify here is of the kind '/node/heron/client/joe'. What will be registered
    * into the mbean server will then be 'xmlBlaster:name=/node/heron/client/joe'.
    */
   public void register(Object obj, String name) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "register(" + name + ")");
      if (obj == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, " could not register '" + name + "' into MBean Server since object to register is null.");
      }
      try {
         ObjectName objName = new ObjectName(this.NAME_PREFIX + name);
         this.mbeanServer.registerMBean(obj, objName);
      }
      catch(Exception ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, " could not register '" + name + "' into MBean Server.", ex);
      }
   }

   /**
    * unregisters the specified mbean from the mbean server.
    */
   public void unRegister(String name) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "unRegister(" + name + ")");
      try {
         ObjectName objName = new ObjectName(this.NAME_PREFIX + name);
         this.mbeanServer.unregisterMBean(objName);
      }
      catch(Exception ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, " could not register '" + name + "' into MBean Server.", ex);
      }
   }
/**
 * Starts XmlBlasterConnector on mbeanServer-Instance
 */

   public void startXmlBlasterConnector(MBeanServer mbeanServer) throws XmlBlasterException {
     try {
       log.info(ME, "Registering embedded xmlBlasterConnector for JMX...");

       //start xmlBlaster-Connector:
       ObjectName xmlBlasterConnector_name = new ObjectName("Adaptor:transport=xmlBlaster,port=3424");
       mbeanServer.createMBean("org.xmlBlaster.util.admin.extern.XmlBlasterConnector", xmlBlasterConnector_name, null);
       //start internal xmlBlasterInstance
       log.info(ME,"Trying to start internal xmlBlaster - with own MBeanServer as Parameter");
      try {
        mbeanServer.invoke(xmlBlasterConnector_name, "startInternal", new Object[] {this.mbeanServer}, new String[]{MBeanServer.class.getName()});
      }
      catch (Exception ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, " could not register  connector into MBean Server.", ex);
      }

     }
     catch (Exception e) {
       e.printStackTrace();
       log.error(ME,"Error when registering new Connector >>  " + e.toString() );
       throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, " could not start embedded xmlBlasterConnector", e);
     }
   }
}
