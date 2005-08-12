/*------------------------------------------------------------------------------
Name:      JmxWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterSecurityManager;

import javax.management.ObjectName;
import javax.management.ObjectInstance;
import javax.management.QueryExp;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

//since JDK 1.5 or with additional jmxremote.jar
//import javax.management.remote.JMXServiceURL;
//import javax.management.remote.JMXConnectorServerFactory;
//import javax.management.remote.JMXConnectorServer;
//import javax.management.remote.JMXPrincipal;

import java.rmi.RemoteException;
import java.rmi.Naming;

import com.sun.jdmk.comm.AuthInfo;
import com.sun.jdmk.comm.HtmlAdaptorServer;

import javax.security.auth.Subject;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Iterator;

// since JDK 1.5 (instead of javax.management.MBeanServerFactory from separate jmxri.jar from http://java.sun.com/products/JavaManagement/download.html)
//import java.lang.management.ManagementFactory;


/**
 * JmxWrapper wraps the MBeanServer instance. 
 * Current supported adaptors are a HTTPAdaptor, the XmlBlasterAdaptor and the JDK1.5 jconsole adaptor.
 * <table>
 * <tr>
 * <td>1.</td>
 * <td>xmlBlaster/jmx/HtmlAdaptor</td>
 * <td>true: Start the adaptor for html-browser access</td>
 * </tr>
 * <tr>
 * <td>2.</td>
 * <td>xmlBlaster/jmx/XmlBlasterAdaptor</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>3a.</td>
 * <td>java -Dcom.sun.management.jmxremote ...</td>
 * <td>Start the JDK 1.5 jconsole adaptor by the java virtual machine</td>
 * </tr>
 * <tr>
 * <td>3b.</td>
 * <td>xmlBlaster/jmx/rmi</td>
 * <td>true: Start the JDK 1.5 jconsole adaptor by our own coding</td>
 * </tr>
 * </table>
 * <p />
 * Note for 3b.:<br />
 * A rmiregistry server is created automatically. If there is running already one, that is used.<br />
 * You can specify another port or host to create/use a rmiregistry server:
 * <pre>
 *     -xmlBlaster/jmx/rmiregistry/port   Specify a port number where rmiregistry listens.
 *                         Default is port 1099
 *     -xmlBlaster/jmx/rmiregistry/hostname Specify a hostname where rmiregistry runs.
 *                         Default is the dns name of the running host.
 * </pre>
 * @see http://java.sun.com/developer/technicalArticles/J2SE/jmx.html
 * @see org.xmlBlaster.util.XmlBlasterSecurityManager
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.jmx.html">The admin.jmx requirement</a>
 */
public class JmxWrapper
{
   private final Global glob;
   private final LogChannel log;
   private final String ME;
   private MBeanServer mbeanServer;
   private HtmlAdaptorServer html;
   private int useJmx;
   /**
    * We hold an own map for mbeans registered to support renaming. 
    * The key is the objectName.toString() and the value is the JmxMBeanHandle instance.
    */
   private Map mbeanMap = new HashMap();
   /** Export Global.getProperty() to JMX */
   private JmxProperties jmxProperties;
   private JmxMBeanHandle jmxPropertiesHandle;
   private JmxLogLevel jmxLogLevel;
   private JmxMBeanHandle jmxLogLevelHandle;
   /** XmlBlaster RMI registry listen port is 1099, to access for bootstrapping */
   public static final int DEFAULT_REGISTRY_PORT = 1099;
   private static JmxWrapper theJmxWrapper;

   /**
    * Singleton to avoid that different Global instances create more than one JmxWrapper. 
    */
   public static JmxWrapper getInstance(Global glob) throws XmlBlasterException {
      if (theJmxWrapper == null) {
         synchronized (JmxWrapper.class) {
            if (theJmxWrapper == null) {
               theJmxWrapper = new JmxWrapper(glob);
            }
         }
      }
      return theJmxWrapper;
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
         ContextNode propNode = new ContextNode(this.glob, ContextNode.SYSPROP_MARKER_TAG, null, this.glob.getContextNode());
         this.jmxPropertiesHandle = registerMBean(propNode, jmxProperties); // "sysprop"

         this.jmxLogLevel = new JmxLogLevel(this.glob);
         ContextNode logNode = new ContextNode(this.glob, ContextNode.LOGGING_MARKER_TAG, null, this.glob.getContextNode());
         this.jmxLogLevelHandle = registerMBean(logNode, jmxLogLevel); // "logging"
      }
   }

   /**
    * Check if JMX is activated. 
    * @return true if JMX is in use
    */
   public boolean isActivated() {
      return (this.mbeanServer != null) && (this.useJmx != 0);
   }
   
   /**
    * Create the unique MBeanServer instance. 
    */
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
    * JMX property values may not contain a comma ','. 
    * Here we replace commas with an underscore.
    * Even if we use quoted ObjectName values the comma is not allowed.
    * @param value The value to verify
    * @return The beautified value to be usable as a value for JMX properties
    */
   public static String validateJmxValue(String value) {
      if (value == null) return value;
      while (true) {
         int index = value.indexOf(",");
         if (index >= 0)
            value = value.substring(0,index) + "_" + value.substring(index+1);
         else
            break;
      }
      return value;
   }

   /**
    * Initialize the MBean server and adaptors. 
    */
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

      // In a cluster environment set for example jndiPath=="/"+${cluster.node.id}
      String jndiPath = glob.getProperty().get("xmlBlaster/jmx/rmiregistry/jndiPath", "/jmxrmi"); // "/jmxconnector";

      String ssl = System.getProperty("com.sun.management.jmxremote.ssl");
      //String aut = System.getProperty("com.sun.management.jmxremote.authenticate");
      String pwd = System.getProperty("com.sun.management.jmxremote.password.file");
      //boolean isAuthenticated = !((ssl==null||"false".equals(ssl)) && (aut==null||"false".equals(aut)));
      boolean isAuthenticated = !(ssl==null||"false".equals(ssl)) || pwd!=null;
      
      if (System.getProperty("com.sun.management.jmxremote.port") != null) {
         // For localhost or remote access with specific port
         // You have to configure authentication!
         // JDK >= 1.5 automatically creates an RMI connector and start it for us
         int port = new Integer(System.getProperty("com.sun.management.jmxremote.port")).intValue();
         if (supportsJconsole) {
            String loc = "service:jmx:rmi:///jndi/rmi://"+glob.getLocalIP()+":"+port+jndiPath;
            log.info(ME, "'java -Dcom.sun.management.jmxremote.port=" + port +
                         "' is specified, JMX is switched on, try to start 'jconsole " + loc + "'");
            if (!isAuthenticated)
               log.warn(ME, "Caution: Your JMX access is not protected with SSL or password, see http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.jmx.html#jconsole");
            useJmx++;
         }
         else {
            log.info(ME, "'java -Dcom.sun.management.jmxremote.port="+port+"' is specified "+
                         " but is not supported in this runtime " + System.getProperty("java.runtime.version"));
         }
      }
      else if (System.getProperty("com.sun.management.jmxremote") != null) {
         // For localhost or remote access with default port 1099
         // You have to configure authentication!
         // JDK >= 1.5 automatically creates an RMI connector and start it for us
         String loc = "service:jmx:rmi:///jndi/rmi://"+glob.getLocalIP()+":"+DEFAULT_REGISTRY_PORT+jndiPath;
         if (supportsJconsole) {
            log.info(ME, "'java -Dcom.sun.management.jmxremote' is specified, JMX is switched on, try to start 'jconsole' or 'jconsole "+loc+"'");
            if (!isAuthenticated)
               log.warn(ME, "Caution: Your JMX access is not protected with SSL or password, see http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.jmx.html#jconsole");
            useJmx++;
         }
         else {
            log.info(ME, "'java -Dcom.sun.management.jmxremote' is specified "+
                         " but is not supported in this runtime " + System.getProperty("java.runtime.version"));
         }
      }
      else if (glob.getProperty().get("xmlBlaster/jmx/rmi", false)) {
         // JDK >= 1.5: Create manually an RMI connector and start it
         // If there is no rmiregistry around we start one
         // url ::= service:jmx:protocol:sap
         // sap ::= //[host[:port]][url-path]
         if (!supportsJconsole) {
            log.warn(ME, "JMX setting '-xmlBlaster/jmx/rmi true' ignored in this JVM runtime, you need JDK 1.5 or higher.");
         }
         else {
            try {
               int rmiRegistryPort = glob.getProperty().get("xmlBlaster/jmx/rmiregistry/port", DEFAULT_REGISTRY_PORT); // 1099
               String rmiRegistryHost = glob.getProperty().get("xmlBlaster/jmx/rmiregistry/hostname", glob.getLocalIP());
               startRmiRegistry(rmiRegistryHost, rmiRegistryPort);

               String bindName = "rmi://"+rmiRegistryHost+":"+rmiRegistryPort+jndiPath;

               try {  // If an external rmiregistry is running cleanup old entries:
                  Naming.unbind(bindName);
               } catch (Exception e) {
                  log.trace(ME, "Can't unbind '" + bindName + "': " + e.toString());
               }

               //String loc = "service:jmx:rmi://"+rmiRegistryHost+":"+rmiRegistryPort+"/jndi/" + bindName;
               String loc = "service:jmx:rmi:///jndi/" + bindName;

               //since JDK 1.5 or with including jmxremote.jar for JDK 1.3/1.4
               final String storedUser = glob.getProperty().get("xmlBlaster/jmx/rmi/user", (String)null);
               final String storedPassword = glob.getProperty().get("xmlBlaster/jmx/rmi/password", (String)null);
               Map props = new TreeMap();
               if (storedUser != null) {
                  javax.management.remote.JMXAuthenticator auth = new javax.management.remote.JMXAuthenticator() {
                     public javax.security.auth.Subject authenticate(Object credentials) {
                        if (log.CALL) log.call(ME, "Calling authenticate(" + ((credentials==null)?"null":credentials.toString()) + ")");
                        if (!(credentials instanceof String[])) throw new SecurityException("xmlBlaster responds: Bad credentials, please pass user name and password");
                        String[] creds = (String[])credentials;
                        if (creds.length != 2) throw new SecurityException("xmlBlaster responds: Bad credentials, please pass user name and password");

                        String user = creds[0];
                        String password = creds[1];
                        if (log.TRACE) log.trace(ME, "Calling authenticate(user=" + user + ", password=" + password + ")");

                        if (password == null) throw new SecurityException("xmlBlaster responds: Missing password");
                        if (!storedUser.equals(user)) throw new SecurityException("xmlBlaster responds: Unknown user " + user + ",  please try with user '" + storedUser + "'");
                        if (!storedPassword.equals(password)) throw new SecurityException("xmlBlaster responds: Bad password, please try again");

                        Set principals = new HashSet();
                        principals.add(new javax.management.remote.JMXPrincipal(user));
                        return new Subject(true, principals, Collections.EMPTY_SET, Collections.EMPTY_SET);
                     }
                  };
                  props.put("jmx.remote.authenticator", auth); // JMXConnectorServer.AUTHENTICATOR
               }
               else {
                  log.warn(ME, "You should switch on authentication with '-xmlBlaster/jmx/rmi/user' and '-xmlBlaster/jmx/rmi/password'");
               }

               //since JDK 1.5 or with including jmxremote.jar for JDK 1.3/1.4
               //javax.management.remote.JMXServiceURL url = new javax.management.remote.JMXServiceURL(loc);
               //javax.management.remote.JMXConnectorServer cs = javax.management.remote.JMXConnectorServerFactory.newJMXConnectorServer(url, props, getMBeanServer());
               //cs.start();

               // JDK 1.3 and 1.4: Not available so we need to use reflection
               Class clazz = java.lang.Class.forName("javax.management.remote.JMXServiceURL");
               Class[] paramCls =  new Class[] { java.lang.String.class }; // url location
               Object[] params = new Object[] { loc };
               java.lang.reflect.Constructor ctor = clazz.getConstructor(paramCls);
               Object jMXServiceURL = ctor.newInstance(params);

               clazz = java.lang.Class.forName("javax.management.remote.JMXConnectorServerFactory");
               paramCls = new Class[] {
                  java.lang.Class.forName("javax.management.remote.JMXServiceURL"),
                  java.util.Map.class,                   // properties
                  javax.management.MBeanServer.class
               };
               params = new Object[] {
                  jMXServiceURL,
                  props,
                  getMBeanServer()
               };
               java.lang.reflect.Method method = clazz.getMethod("newJMXConnectorServer", paramCls);
               Object jMXConnectorServer = method.invoke(clazz, params); // Returns "JMXConnectorServer"

               clazz = java.lang.Class.forName("javax.management.remote.JMXConnectorServer");
               paramCls = new Class[0];
               params = new Object[0];
               method = clazz.getMethod("start", paramCls);
               method.invoke(jMXConnectorServer, params); // finally ... starts JMX

               log.info(ME, "JMX is switched on because of 'xmlBlaster/jmx/rmi true' is set, try to start 'jconsole " + loc + "' and user=" + storedUser);
               useJmx++;
            }
            catch(Throwable ex) {
               ex.printStackTrace();
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, " could not create JMXConnectorServer", ex);
            }
         }
      }
      else {
         if (supportsJconsole) {
            log.info(ME, "JMX over RMI is switched off, for details see http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.jmx.html#jconsole");
         }
      }

      if (glob.getProperty().get("xmlBlaster/jmx/HtmlAdaptor", false)) {
         try {
            String hostname = glob.getProperty().get("xmlBlaster/jmx/HtmlAdaptor/hostname", glob.getLocalIP());
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

            ObjectInstance objectInstance = this.mbeanServer.registerMBean(this.html, html_name);
            JmxMBeanHandle handle = new JmxMBeanHandle(objectInstance, null, this.html);
            this.mbeanMap.put(html_name.toString(), handle);
            
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
            log.error(ME, " Could not create HtmlAdaptorServer: " + ex.toString());
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
    * Start xmlBlaster security manager and RMI registry. 
    * @param registryPort xmlBlaster/jmx/rmiregistry/port=1099
    * @param registryHost xmlBlaster/jmx/rmiregistry/hostname=loclhost
    */
   private synchronized void startRmiRegistry(String registryHost, int registryPort) throws XmlBlasterException {

      XmlBlasterSecurityManager.createSecurityManager(glob);

      try {
         if (registryPort > 0) {
            // Start a 'rmiregistry' if desired
            try {
               java.rmi.registry.LocateRegistry.createRegistry(registryPort);
               log.info(ME, "Started RMI registry on port " + registryPort);
            } catch (java.rmi.server.ExportException e) {
               // Try to bind to an already running registry:
               try {
                  java.rmi.registry.LocateRegistry.getRegistry(registryHost, registryPort);
                  log.info(ME, "Another rmiregistry is running on port " + DEFAULT_REGISTRY_PORT +
                               " we will use this one. You could change the port with e.g. '-xmlBlaster/jmx/rmiregistry/port 1122' to run your own rmiregistry.");
               }
               catch (RemoteException e2) {
                  String text = "Port " + DEFAULT_REGISTRY_PORT + " is already in use, but does not seem to be a rmiregistry. Please can change the port with e.g. -xmlBlaster/jmx/rmiregistry/port 1122 : " + e.toString();
                  throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, text, e2);
               }
            }
         }
      } catch (Exception e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, " could not initialize RMI registry", e);
      }

      if (log.TRACE) log.trace(ME, "Initialized RMI registry");
   }

   /**
    * Reorganize the registration for a new parent node. 
    * @param oldName The existing registry, is used to lookup all matching sub-entries.
    *        For example "org.xmlBlaster:nodeClass=node,node=clientSUB1"
    * @param newNodeClass The new parent node like
    *        "org.xmlBlaster:nodeClass=node,node=heron"
    *        Note: Instance names may not contain commas "," for example "joe,Smith" is not valid
    * @return The resulting registration like
    *        "org.xmlBlaster:nodeClass=node,node=heron,clientClass=connection,connection=jack,queueClass=queue,queue=connection-99"
    */
   public synchronized int renameMBean(String oldName, String classNameToChange, String instanceName) {
      if (this.mbeanServer == null) return 0;
      if (useJmx == 0) return 0;
      if (oldName == null || classNameToChange == null) return 0;
      int count = 0;
      if (log.CALL) log.call(ME, "JMX rename registration from '" + oldName + "' to '" + classNameToChange + "=" + instanceName + "'");
      try {
         // e.g. "org.xmlBlaster:nodeClass=node,node=clientSUB1,*"
         ObjectName query = new ObjectName(oldName+",*");
         QueryExp queryExp = null;
         Set mbeanSet = this.mbeanServer.queryMBeans(query, queryExp);
         if (mbeanSet.size() == 0) {
            if (log.TRACE) log.trace(ME, "JMX rename registration from '" + oldName + "' to '" + classNameToChange + "=" + instanceName + "', nothing found for '" + query + "'");
            return count;
         }
         Iterator it = mbeanSet.iterator();
         while (it.hasNext()) {
            ObjectInstance instance = (ObjectInstance)it.next();
            ObjectName tmp = instance.getObjectName();
            JmxMBeanHandle mbeanHandle = (JmxMBeanHandle)this.mbeanMap.get(tmp.toString());
            if (mbeanHandle == null) {
               log.error(ME, "Internal problem: Can't find registration of MBean '" + tmp.toString() + "'");
               continue;
            }
            this.mbeanServer.unregisterMBean(tmp);
            this.mbeanMap.remove(tmp.toString());
            ContextNode renamed = ContextNode.valueOf(this.glob, tmp.toString());
            renamed.changeParentName(classNameToChange, instanceName);
            if (log.TRACE) log.trace(ME, "Renamed '" + oldName + "' to '" + renamed.getAbsoluteName(ContextNode.SCHEMA_JMX) + "'");
            registerMBean(renamed, mbeanHandle.getMBean(), mbeanHandle);
            this.mbeanMap.put(mbeanHandle.getObjectInstance().getObjectName().toString(), mbeanHandle);
            count++;
         }
         return count;
      }
      catch (Exception e) {
         log.error(ME, "JMX rename registration problem from '" + oldName + "' to '" + classNameToChange + "=" + instanceName + "': " + e.toString());
         e.printStackTrace();
         return count;
      }
   }

   /**
    * Registers the specified mbean into the mbean server.
    * A typical registration string is
    *   <tt>org.xmlBlaster:nodeClass=node,node="heron",clientClass=client,client="joe",queueClass=queue,queue="subject665",entryClass=entry,entry="1002"</tt>
    * which doesn't conform to the ObjectName recommendation as it does not
    * contains the 'type=...' property.
    * We have chosen this as it creates a nice hierarchy in the jconsole GUI tool.
    * @param contextNode The unique name for JMX observation
    *        Note: Instance names may not contain commas "," for example "joe,Smith" is not valid
    * @param mbean the MBean object instance 
    *        If mbean implements MBeanRegistration:preRegister() we don't need the type, name
    * @return The JmxMBeanHandle with object name used to register or null on error
    *         Note: You may not take a clone of it as we may change attributes of this instance
    *         during renaming operations.
    */
   public synchronized JmxMBeanHandle registerMBean(ContextNode contextNode, Object mbean) {
           return registerMBean(contextNode, mbean, null);
   }
   
   private synchronized JmxMBeanHandle registerMBean(ContextNode contextNode, Object mbean, JmxMBeanHandle mbeanHandle) {
      if (this.mbeanServer == null) return null;
      if (useJmx == 0) return null;
      if (contextNode == null) return null;
      if (log.CALL) log.call(ME, "registerMBean(" + contextNode.getAbsoluteName(ContextNode.SCHEMA_JMX) + ")");
      //Thread.dumpStack();

      String hierarchy = (contextNode == null) ? 
                            ("org.xmlBlaster:type=" + this.glob.getId()) :
                            contextNode.getAbsoluteName(ContextNode.SCHEMA_JMX);

      ObjectName objectName = null;
      try {
         objectName = new ObjectName(hierarchy);
         ObjectInstance objectInstance = this.mbeanServer.registerMBean(mbean, objectName);
         if (mbeanHandle == null) {
            mbeanHandle = new JmxMBeanHandle(objectInstance, contextNode, mbean);
            this.mbeanMap.put(objectName.toString(), mbeanHandle);
         }
         else {
             // Update the mbeanHandle of the registrar wit the new ObjectName:
                 mbeanHandle.setObjectInstance(objectInstance);
         }
         if (log.TRACE) log.trace(ME, "Registered MBean '" + objectName.toString() +
                                      "' for JMX monitoring and control");
         return mbeanHandle;
      }
      catch (javax.management.InstanceAlreadyExistsException e) {
         log.warn(ME, "Ignoring JMX registration problem for '" + ((objectName==null)?hierarchy:objectName.toString()) + "': " + e.toString());
         if (objectName != null) {
            this.mbeanMap.remove(objectName.toString());
            unregisterMBean(objectName);
            return registerMBean(contextNode, mbean);
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
   public synchronized void unregisterMBean(ObjectName objectName)// throws XmlBlasterException
   {
      if (objectName == null) return;
      if (this.mbeanServer == null) return;
      if (useJmx == 0) return;
      if (log.CALL) log.call(ME, "Unregister MBean '" + objectName.toString() + "'");
      try {
         Object removed = this.mbeanMap.remove(objectName.toString());
         this.mbeanServer.unregisterMBean(objectName);
         if (removed == null)
            log.error(ME, "No JMX MBean instance of " + objectName.toString() + " removed");
      }
      catch (Exception e) {
         log.error(ME, "JMX unregistration problems: " + e.toString());
      }
   }

   public void unregisterMBean(JmxMBeanHandle jmxMBeanHandle) {
      unregisterMBean(jmxMBeanHandle.getObjectInstance().getObjectName());
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

   public void shutdown() {
      if (this.mbeanServer == null) return;

      if (this.jmxPropertiesHandle != null) {
         unregisterMBean(this.jmxPropertiesHandle);
         this.jmxPropertiesHandle = null;
      }
      if (this.jmxLogLevelHandle != null) {
         unregisterMBean(this.jmxLogLevelHandle);
         this.jmxLogLevelHandle = null;
      }
      // TODO: Implement complete shutdown
      //javax.management.MBeanServerFactory.releaseMBeanServer(this.mbeanServer);
   }
}
