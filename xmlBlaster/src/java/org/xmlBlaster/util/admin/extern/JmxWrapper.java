/*------------------------------------------------------------------------------
Name:      JmxWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;

import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;

import com.sun.jdmk.comm.AuthInfo;
import com.sun.jdmk.comm.HtmlAdaptorServer;

import com.sun.jmx.trace.Trace;

// since JDK 1.5 (instead of javax.management.MBeanServerFactory from separate jmxri.jar from http://java.sun.com/products/JavaManagement/download.html)
//import java.lang.management.ManagementFactory;


/**
 * JmxWrapper wraps the MBeanServer instance. 
 * Current supported adaptors are a HTTPAdaptor, the XmlBlasterAdaptor and the JDK1.5 jconsole adaptor.
 * <table>
 * <tr>
 * <td>xmlBlaster/jmx/HtmlAdaptor</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>xmlBlaster/jmx/XmlBlasterAdaptor</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>java -Dcom.sun.management.jmxremote ...</td>
 * <td>Start the JDK 1.5 jconsole adaptor</td>
 * </tr>
 * </table>
 * @see http://java.sun.com/developer/technicalArticles/J2SE/jmx.html
 */
public class JmxWrapper
{
   private final Global glob;
   private final LogChannel log;
   private final String ME;
   private static MBeanServer mbeanServer;
   private static HtmlAdaptorServer html;
   private int useJmx;
   /** Export Global.getProperty() to JMX */
   private JmxProperties jmxProperties;
   private JmxLogLevel jmxLogLevel;

   private MBeanServer getMBeanServer() {
      if (this.mbeanServer == null) {
         synchronized (this) {
            if (this.mbeanServer == null) {
               try {
                  Class clazz = java.lang.Class.forName("java.lang.management.ManagementFactory");
                  if (clazz != null) {
                     // this.mbeanServer = ManagementFactory.getPlatformMBeanServer(); // new for JDK 1.5
                     java.lang.reflect.Method method = clazz.getMethod("getPlatformMBeanServer", new Class[0]);
                     this.mbeanServer = (javax.management.MBeanServer)method.invoke(clazz, new Class[0]);
                  }
               }
               catch (Exception e) {
                  log.trace(ME, "java.lang.management.ManagementFactory is not available for JMX monitoring");
               }
               if (this.mbeanServer == null) {
                  // For JDK < 1.5 fall back to 
                  //  JMX Remote API 1.0.1_03 Reference Implementation
                  //  JMX 1.2.1 Reference Implementation
                  //  Download from http://java.sun.com/products/JavaManagement/download.html
                  String hostname = glob.getProperty().get("xmlBlaster/jmx/hostname", glob.getLocalIP());
                  this.mbeanServer = MBeanServerFactory.createMBeanServer(hostname);
               }
            }
         }
      }
      return this.mbeanServer;
   }

   /**
    * Constructs an initial JmxWrapper object.
    */
   public JmxWrapper(Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("jmx");
      this.ME = "JmxWrapper" + this.glob.getLogPrefixDashed();
      getMBeanServer(); // Initialize this.mbeanServer
      init();
      if (useJmx > 0) {
         // Export Global.getProperty() to JMX
         this.jmxProperties = new JmxProperties(this.glob);
         registerMBean("syspropList", jmxProperties);

         this.jmxLogLevel = new JmxLogLevel(this.glob);
         registerMBean("logging", jmxLogLevel);
      }
   }

   public synchronized void init() throws XmlBlasterException {
      
      if (this.mbeanServer == null) return;

      boolean supportsJconsole = true;
      try {
         if (new Float(System.getProperty("java.runtime.version").substring(0,3)).floatValue() < 1.5)
            supportsJconsole = false;
      }
      catch (Throwable e) {
         log.trace(ME, e.toString());
      }


      if (System.getProperty("com.sun.management.jmxremote") != null) {

         // Create an RMI connector and start it
         //JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:9999/server");
         //JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
         //cs.start();

         if (supportsJconsole) {
            log.info(ME, "'java -Dcom.sun.management.jmxremote' is specified, JMX is switched on, try to start 'jconsole'");
            useJmx++;
         }
         else {
            log.info(ME, "'java -Dcom.sun.management.jmxremote' is specified "+
                         " but is not supported in this runtime " + System.getProperty("java.runtime.version"));
         }
      }
      else {
         if (supportsJconsole) {
            log.info(ME, "No 'java -Dcom.sun.management.jmxremote' specified, JMX is switched off");
         }
      }

      if (glob.getProperty().get("xmlBlaster/jmx/HtmlAdaptor", false)) {
         try {
            String hostname = glob.getProperty().get("xmlBlaster/jmx/hostname", glob.getLocalIP());
            int port = glob.getProperty().get("xmlBlaster/jmx/HtmlAdaptor/port", 8082);
            String loginName = glob.getProperty().get("xmlBlaster/jmx/HtmlAdaptor/loginName", (String)null);
            String password = glob.getProperty().get("xmlBlaster/jmx/HtmlAdaptor/password", "secret");

            if (loginName != null) {
               AuthInfo authInfos[] = { new AuthInfo(loginName, password) };
               this.html = new HtmlAdaptorServer(port, authInfos);
            }
            else {
               this.html = new HtmlAdaptorServer(port);
            }
            
            ObjectName html_name = new ObjectName("Adaptor:name=html,port="+port);
            this.mbeanServer.registerMBean(this.html, html_name);
            if (loginName == null) {
               log.info(ME, "Registered JMX HTML adaptor on http://"+hostname+":"+port +
                        ". No authentication is configured with 'xmlBlaster/jmx/HtmlAdaptor/loginName=...'");
            }
            else {
               log.info(ME, "Registered JMX HTML adaptor on http://"+hostname+":"+port + " with login name '" + loginName + "'");
            }
            useJmx++;
         }
         catch(Exception ex) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, " could not create HtmlAdaptorServer", ex);
         }
         this.html.start();
      }

      if (glob.getProperty().get("xmlBlaster/jmx/XmlBlasterAdaptor", false)) {
         try {
            startXmlBlasterConnector(this.mbeanServer);
            useJmx++;
         }
         catch (XmlBlasterException ex) {
            log.error(ME,"Error when starting xmlBlasterConnector " + ex.toString());
            ex.printStackTrace();
         }
      }
   }


   /**
    * registers the specified mbean into the mbean server.
    * the name you specify here is of the kind '/node/heron/client/joe'. What will be registered
    * into the mbean server will then be 'xmlBlaster:name=/node/heron/client/joe'.
    * @param name the instance for example "joe"
    * @param mbean the MBean object instance 
    *        If mbean implements MBeanRegistration:preRegister() we don't need the type, name
    * @return The object name used to register or null on error
    */
   public ObjectName registerMBean(String name, Object mbean)// throws XmlBlasterException
   {
      if (this.mbeanServer == null) return null;
      if (log.CALL) log.call(ME, "registerMBean(" + name + ")");

      String hierarchy = "org.xmlBlaster:type=" + this.glob.getId();
      /*
      String hierarchy = getId();// + ":";
      if (type != null)
         hierarchy += ":type="+type;
      */
      if (name != null)
         hierarchy += ",name="+name;

      ObjectName objectName = null;
      try {
         objectName = new ObjectName(hierarchy);
         this.mbeanServer.registerMBean(mbean, objectName);
         if (log.TRACE) log.trace(ME, "Registered MBean '" + objectName.toString() +
                                      "' for JMX monitoring and control");
         return objectName;
      }
      catch (javax.management.InstanceAlreadyExistsException e) {
         log.warn(ME, "JMX registration problem for '" + ((objectName==null)?hierarchy:objectName.toString()) + "': " + e.toString());
         if (objectName != null) {
            unregisterMBean(objectName);
            return registerMBean(name, mbean);
         }
         return null;
      }
      catch (Exception e) {
         log.error(ME, "JMX registration problem for '" + ((objectName==null)?hierarchy:objectName.toString()) + "': " + e.toString());
         e.printStackTrace();
         return null;
      }
   }

   /**
    * Unregisters the specified mbean from the mbean server. 
    * @param objectName The object you got from registerMBean() of type ObjectName,
    *                   if null nothing happens
    */
   public void unregisterMBean(Object objectName)// throws XmlBlasterException
   {
      if (objectName == null) return;
      if (this.mbeanServer == null) return;
      if (log.CALL) log.call(ME, "Unregister MBean '" + objectName.toString() + "'");
      try {
         this.mbeanServer.unregisterMBean((ObjectName)objectName);
      }
      catch (Exception e) {
         log.error(ME, "JMX unregistration problems: " + e.toString());
      }
   }

   /**
    * Starts XmlBlasterConnector on mbeanServer-Instance
    * This is a small embedded xmlBlaster server instance which is started.
    * You can than access this adaptor using the SWING GUI "java org.xmlBlaster.jmxgui.Main"
    */
   public void startXmlBlasterConnector(MBeanServer mbeanServer) throws XmlBlasterException {
      try {
         if (log.CALL) log.call(ME, "Registering embedded xmlBlasterConnector for JMX...");

         int port = glob.getProperty().get("xmlBlaster/jmx/XmlBlasterAdaptor/port", 3424);
         ObjectName xmlBlasterConnector_name = new ObjectName("Adaptor:transport=xmlBlaster,port="+port);
         mbeanServer.createMBean("org.xmlBlaster.util.admin.extern.XmlBlasterConnector", xmlBlasterConnector_name, null);
         log.info(ME,"Registered JMX xmlBlaster adaptor on port " + port + ", try to start swing GUI 'org.xmlBlaster.jmxgui.Main'");

         // Start the adaptor:
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
