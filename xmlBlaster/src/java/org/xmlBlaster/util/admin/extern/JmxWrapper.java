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
   private final String ME = "JmxWrapper";
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
      init();
   }

   public void init() throws XmlBlasterException {
      if (this.mbeanServer == null) {
         synchronized (this) {
            if (this.mbeanServer == null) {
               try {
                  this.mbeanServer = MBeanServerFactory.createMBeanServer();
                  this.html = new HtmlAdaptorServer();
                  ObjectName html_name = new ObjectName("Adaptor:name=html,port=8082");
                  this.mbeanServer.registerMBean(this.html, html_name);
                  log.info(ME, "Registered with JMX server, activated HTML adaptor on http://"+glob.getLocalIP()+":8082");
               }
               catch(Exception ex) {
                  throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, " could not create MBean Server", ex);
               }
               this.html.start();    
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
      try {
         ObjectName objName = new ObjectName(this.NAME_PREFIX + name);
         this.mbeanServer.unregisterMBean(objName);
      }
      catch(Exception ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, " could not register '" + name + "' into MBean Server.", ex);
      }
   }
}
