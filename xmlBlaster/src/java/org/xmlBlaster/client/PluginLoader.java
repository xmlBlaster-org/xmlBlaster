package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.jutils.io.FileUtil;
import org.jutils.JUtilsException;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.authentication.plugins.I_ClientHelper;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * <code>PluginLoader</code> is a singleton, which is responsible for loading
 * and initialization of client secuirty plugins.
 *
 * Either the client application chooses an appropriate plugin, or the
 * <pre>xmlBlaster.properties</pre> file states, which plugin has to be used,
 * by using the <code>Security.Client.ForcePlugin</code>-Option.
 *
 * Syntax:
 *   <code>Security.Client.ForcePlugin=</code><i>PluginType<\i>,<i>PluginVersion</i>
 *
 * Hint:
 *   Type and version must be the type and version of a valid and declared plugin.
 *
 * Example:
 *   <code>
 *     Security.Client.ForcePlugin=gui,1.0
 *     Security.Client.Plugin[gui][1.0]=org.xmlBlaster.authentication.ClientSecurityHelper
 *   </code>
 *
 * If neither the application, nor the config enforce a specific plugin, the Dummy-Plugin
 * is used (old xmlBlaster behavior).
 */
public class PluginLoader {
   private  static final String  ME = "SecurityPluginLoader";
   private  String pluginMechanism = null;
   private  String pluginVersion = null;
   private  I_ClientHelper plugin = null;
   private  static final PluginLoader instance = new PluginLoader();

   private PluginLoader()
   {
   }

   /**
    * Get the instance of the PluginLoader
    *
    * @return PluginLoader
    */
   public static PluginLoader getInstance()
   {
      return instance;
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
    * @return I_ClientHelper
    */
   public I_ClientHelper getCurrentClientPlugin() throws XmlBlasterException
   {
      if(plugin!=null) return plugin;

      return getClientPlugin(null, null);
   }

   /**
    * Load another plugin.
    * <p/>
    * @param String The type of the plugin, e.g. "a2Blaster"
    * @param String The version of the plugin, e.g. "1.0"
    * @return I_ClientHelper
    * @exception Exception Thrown if the plugin wasn't loadable or initializable
    */
   public synchronized I_ClientHelper getClientPlugin(String mechanism, String version) throws XmlBlasterException
   {
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
   private synchronized I_ClientHelper loadPlugin(String[] param) throws XmlBlasterException
   {
      if(param==null) return null;
      if(param[0]==null) return null;

      String[] p = new String[param.length-1];
      I_ClientHelper clntPlugin = null;

      try {
         if (Log.TRACE) Log.trace(ME, "Trying Class.forName('"+param[0]+"') ...");
         Class cl = java.lang.Class.forName(param[0]);
         clntPlugin = (I_ClientHelper)cl.newInstance();
         Log.info(ME, "Found I_ClientHelper '"+param[0]+"'");
      }
      catch (IllegalAccessException e) {
         Log.error(ME, "The plugin class '"+param[0]+"' is not accessible\n -> check the plugin name and/or the CLASSPATH");
         throw new XmlBlasterException(ME+".NoClass", "The plugin class '"+param[0]+"' is not accessible\n -> check the plugin name and/or the CLASSPATH");
      }
      catch (SecurityException e) {
         Log.error(ME, "Couldn't load security plugin '"+param[0]+"'. Access Denied");
         throw new XmlBlasterException(ME+".AccessDenied", "The plugin class '"+param[0]+"' couldn't be loaded!");
      }
      catch (Throwable e) {
         Log.error(ME, "The plugin class '"+param[0]+"'is invalid!" + e.toString());
         throw new XmlBlasterException(ME+".InvalidClassOrInitializer", "The plugin class '"+param[0]+"'is invalid!" + e.toString());
      }

      System.arraycopy(param,1,p,0,param.length-1);

      if (clntPlugin!=null) {
         try {
            clntPlugin.init(p);
            Log.info(ME, "Plugin '"+param[0]+"' successfully initialized!");
         }
         catch(Exception e) {
            throw new XmlBlasterException(ME+".noInit", "Couldn't initialize plugin '"+param[0]+"'. Reaseon: "+e.toString());
         }
      }

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
         tmp = XmlBlasterProperty.get("Security.Client.ForcePlugin", (String)null);
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

      String s = XmlBlasterProperty.get("Security.Client.Plugin["+mechanism+"]["+version+"]", (String)null);
      if(s==null) throw new XmlBlasterException(ME+".Unknown Plugin", "Unknown Plugin '" + mechanism + "' with version '" + version + "'.");

      StringTokenizer st = new StringTokenizer(s,",");
      while(st.hasMoreTokens()) {
         v.addElement(st.nextToken());
      }

      String[] classnameAndParam = new String[v.size()];
      v.toArray(classnameAndParam);

      this.pluginMechanism=mechanism;
      this.pluginVersion=version;

      return classnameAndParam;
   }

}
