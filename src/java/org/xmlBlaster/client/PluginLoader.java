package org.xmlBlaster.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.util.def.Constants;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * <code>PluginLoader</code> is responsible for loading
 * and initialization of client secuirty plugins.
 *
 * Either the client application chooses an appropriate plugin, or the
 * <pre>xmlBlaster.properties</pre> file states, which plugin has to be used,
 * by using the <code>Security.Client.DefaultPlugin</code>-Option.
 *
 * Syntax:
 *   <code>Security.Client.DefaultPlugin=</code><i>PluginType</i>,<i>PluginVersion</i>
 *
 * Hint:
 *   Type and version must be the type and version of a valid and declared plugin.
 *
 * Example:
 *   <code>
 *     Security.Client.DefaultPlugin=gui,1.0
 *     Security.Client.Plugin[gui][1.0]=org.xmlBlaster.authentication.ClientSecurityHelper
 *   </code>
 *
 * If neither the application, nor the config enforce a specific plugin, the Dummy-Plugin
 * is used (old xmlBlaster behavior).
 */
public class PluginLoader {
   private  static final String  ME = "SecurityPluginLoader";
   private final Global glob;
   private static Logger log = Logger.getLogger(PluginLoader.class.getName());
   private  String pluginMechanism = null;
   private  String pluginVersion = null;
   private  I_ClientPlugin plugin = null;

   public PluginLoader(Global glob)
   {
      this.glob = glob;

   }

   /**
    * Get the type of the currently used plugin
    *
    * @return String
    */
   public String getType()
   {
      return pluginMechanism;
   }

   /**
    * Get the Version of the currently used plugin
    *
    * @return String
    */
   public String getVersion()
   {
      return pluginVersion;
   }

   /**
    * Get the currently used plugin
    *
    * @return I_ClientPlugin
    */
   public I_ClientPlugin getCurrentClientPlugin() throws XmlBlasterException
   {
      if(plugin!=null) return plugin;

      return getClientPlugin(null, null);
   }

   /**
    * Load another plugin.
    * <p/>
    * @param String The type of the plugin, e.g. "ldap"
    * @param String The version of the plugin, e.g. "1.0"
    * @return I_ClientPlugin
    * @exception XmlBlasterException Thrown if the plugin wasn't loadable or initializable
    */
   public synchronized I_ClientPlugin getClientPlugin(String mechanism, String version) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("type=" + mechanism + " version=" + version);
      if((pluginMechanism!=null) && (pluginMechanism.equals(mechanism))) {
        if (((pluginVersion==null) && (version==null)) ||
            ((version!=null) && (version.equals(pluginVersion)))) {
               // ok, the used and the desired plugins are from the same type
               return plugin;
         }
      }

      plugin = loadPlugin(fetchClassnameAndParam(mechanism, version));

      return plugin;
   }

   /**
    * Handels the process of loading a plugin.
    * <p/>
    * @param String[] The first element of this array contains the class name. Following
    *                 elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_Manager
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   private synchronized I_ClientPlugin loadPlugin(String[] param) throws XmlBlasterException
   {
      if(param==null) return null;
      if(param[0]==null) return null;

      String[] p = new String[param.length-1];
      I_ClientPlugin clntPlugin = null;

      try {
         if (log.isLoggable(Level.FINE)) log.fine("Trying Class.forName('"+param[0]+"') ...");
         Class cl = java.lang.Class.forName(param[0]);
         clntPlugin = (I_ClientPlugin)cl.newInstance();
         clntPlugin.init(glob, null);
         if (log.isLoggable(Level.FINE)) log.fine("Found I_ClientPlugin '"+param[0]+"'");
      }
      catch (IllegalAccessException e) {
         if (log.isLoggable(Level.FINE)) log.fine("The plugin class '"+param[0]+"' is not accessible\n -> check the plugin name and/or the CLASSPATH");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "The plugin class '"+param[0]+"' is not accessible\n -> check the plugin name and/or the CLASSPATH", e);
      }
      catch (SecurityException e) {
         if (log.isLoggable(Level.FINE)) log.fine("Couldn't load security plugin '"+param[0]+"'. Access Denied");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "The plugin class '"+param[0]+"' couldn't be loaded!", e);
      }
      catch (Throwable e) {
         if (log.isLoggable(Level.FINE)) log.fine("The plugin class '"+param[0]+"'is invalid!" + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "The plugin class '"+param[0]+"'is invalid!", e);
      }

      System.arraycopy(param,1,p,0,param.length-1);

      /*
      if (clntPlugin!=null) {
         try {
            clntPlugin.init(p);
            log.info(ME, "Plugin '"+param[0]+"' successfully initialized");
         }
         catch(Exception e) {
            throw new XmlBlasterException(ME+".noInit", "Couldn't initialize plugin '"+param[0]+"'. Reaseon: "+e.toString());
         }
      }
      */
      if (log.isLoggable(Level.FINE)) log.fine("Plugin '"+param[0]+"' successfully initialized");

      return clntPlugin;
   }


   /**
    * Resolve a class name of a plugin, specified by its type (mechanism) and version.
    * <p />
    * The plugin is read from xmlBlaster.properties if mechanism==version==null<br />
    * If non is specified there, we return null
    *
    * @param String The type
    * @param String The version
    * @return String[] The name of the class and some parameters
    */
   private synchronized String[] fetchClassnameAndParam(String mechanism, String version) throws XmlBlasterException
   {
      String tmp = null;
      Vector v   = new Vector();

      if((mechanism==null) || (mechanism.equals(""))) { // if the client application doesn't select the mechanism and version, we must check the configuartion
         tmp = glob.getProperty().get("Security.Client.DefaultPlugin", Constants.DEFAULT_SECURITYPLUGIN_TYPE+","+Constants.DEFAULT_SECURITYPLUGIN_VERSION);
         if (log.isLoggable(Level.FINE)) log.fine("Got Security.Client.DefaultPlugin=" + tmp);
         if (tmp!=null) {
            int i = tmp.indexOf(',');
            if (i==-1) {  // version is optional
               version = null;
            }
            else {
               version = tmp.substring(i+1);
            }
            mechanism = tmp.substring(0,i);
         }
         else {
            return (String[])null;
         }
      }
      if(version==null) version="";

      String s = glob.getProperty().get("Security.Client.Plugin["+mechanism+"]["+version+"]", (String)null);
      if(s==null) {
         if (mechanism.equals("htpasswd")) // xmlBlaster should run without xmlBlaster.properties
            s = "org.xmlBlaster.authentication.plugins.htpasswd.ClientPlugin";
         else if (mechanism.equals("simple")) // xmlBlaster should run without xmlBlaster.properties
            s = "org.xmlBlaster.authentication.plugins.simple.ClientPlugin";
         else if (mechanism.equals("ldap")) // xmlBlaster should run without xmlBlaster.properties
            s = "org.xmlBlaster.authentication.plugins.ldap.ClientPlugin";
         else
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Unknown Security.Client.Plugin["+mechanism+"]["+version+"] from ConnectQos is rejected.");
      }

      StringTokenizer st = new StringTokenizer(s,",");
      while(st.hasMoreTokens()) {
         v.addElement(st.nextToken());
      }

      String[] classnameAndParam = new String[v.size()];
      v.copyInto(classnameAndParam); // For client side JDK 1.1 support (v.toArray(classnameAndParam); is since 1.2)

      this.pluginMechanism=mechanism;
      this.pluginVersion=version;

      return classnameAndParam;
   }

}
