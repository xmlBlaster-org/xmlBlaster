/*------------------------------------------------------------------------------
Name:      PluginManagerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Baseclass to load plugins.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.plugin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.PluginClassLoader;

import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;
import java.net.URL;

/**
 * Base class to load plugins.
 * <p />
 * A typical syntax in the xmlBlaster.properties file is:
 * <pre>
 *   MimeSubscribePlugin[ContentLenFilter][1.0]=\
 *       org.xmlBlaster.engine.mime.demo.ContentLenFilter,\
 *       DEFAULT_MAX_LEN=200,DEFAULT_MIN_LEN=20,classpath=mime.jar
 * </pre>
 * @author W. Kleinertz (wkl) H. Goetzger
 * @author <a href="mailto:Konrad.Krafft@doubleslash.de">Konrad Krafft</a>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
abstract public class PluginManagerBase implements I_PluginManager{

   private static String ME = "PluginManagerBase";
   protected Hashtable managers = new Hashtable(); // currently loaded plugins
   private final Global glob;
   private final LogChannel log;

   protected PluginManagerBase(org.xmlBlaster.util.Global glob) {
      this.glob = glob;
      this.log = glob.getLog("classloader");
   }

   /**
    * Return a specific plugin, if one is loaded already it is taken from cache. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Plugin The plugin which is suitable to handle the request.
    * @exception XmlBlasterException Thrown if no suitable plugin has been found.
    */
   public I_Plugin getPluginObject(String type, String version) throws XmlBlasterException {
      if (log.CALL) log.call(ME+".getPluginObject()", "Loading plugin type=" + type + " version=" + version + " property=" + createPluginPropertyKey(type, version));
      I_Plugin plug = null;

      PluginInfo pluginInfo = new PluginInfo(glob, this, type, version);

      // check in hash if plugin is instanciated already
      plug = (I_Plugin)managers.get(pluginInfo.getClassName());
      if (plug!=null) return plug;

      // not in hash, instanciat plugin
      plug = instantiatePlugin(pluginInfo);

      return plug;
   }

   /**
    * Is called after a plugin in instantiated, allows the base class to do specific actions.
    * Is NOT called when plugin got from cache.
    */
   abstract public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) throws XmlBlasterException;

   /**
    * @param type can be null
    * @param version can be null
    * @return please return your default plugin classname or null if not specified
    */
   abstract public String getDefaultPluginName(String type, String version);

   /**
    * Tries to return an instance of the default plugin.
    */
   public I_Plugin getDummyPlugin() throws XmlBlasterException {
      return getPluginObject(null, null);
   }

   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   abstract protected String getPluginPropertyName();

   public String getName() {
      return getPluginPropertyName();
   }

   /**
    * @return e.g. "Security.Server.Plugin[simple][1.0]"
    */
   public final String createPluginPropertyKey(String type, String version) {
      StringBuffer buf = new StringBuffer(80);
      buf.append(getPluginPropertyName());
      if (type != null)
         buf.append("[").append(type).append("]");
      if (version != null)
         buf.append("[").append(version).append("]");
      return buf.toString();
   }

   /**
    * Loads a plugin.
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.authentication.plugins.Manager<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_Plugin
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_Plugin instantiatePlugin(PluginInfo pluginInfo) throws XmlBlasterException
   {
      // separate parameter and plugin name

      I_Plugin plugin = null;
      String pluginName = pluginInfo.getClassName();
      try {
         ClassLoaderFactory factory = glob.getClassLoaderFactory();
         if (factory != null) {
            if (log.TRACE) log.trace(ME, "useXmlBlasterClassloader=true: Trying Class.forName('" + pluginName + "') ...");

            PluginClassLoader myLoader = factory.getPluginClassLoader(pluginInfo);
            if (log.TRACE) log.trace(ME, "Found " + myLoader.getURLs().length + " plugin specific jar files for '" + pluginName + "' preferenced by xmlBlaster classLoader");

            plugin = (I_Plugin)myLoader.loadClass(pluginName).newInstance();
            if (log.TRACE) log.trace(ME, "Found I_Plugin '" + pluginName + "', loaded by PluginClassLoader");
         }
         else { // Use JVM default class loader:
           Class cl = java.lang.Class.forName(pluginName);
           plugin = (I_Plugin)cl.newInstance();
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (IllegalAccessException e) {
         log.error(ME, "The plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin");
         throw new XmlBlasterException(ME+".NoClass", "The Plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin");
      }
      catch (SecurityException e) {
         log.error(ME, "No right to access the plugin class or initializer '" + pluginName + "'");
         throw new XmlBlasterException(ME+".NoAccess", "No right to access the plugin class or initializer '" + pluginName + "'");
      }
      catch (Throwable e) {
         log.error(ME, "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: \n'" + e.toString() + "'");
         e.printStackTrace();
         throw new XmlBlasterException(ME+".Invalid", "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: " + e.toString());
      }

      // Initialize the plugin
      if (plugin != null) {
         try {
            plugin.init(glob, pluginInfo);
            postInstantiate(plugin, pluginInfo);
            log.info(ME, "Plugin '" + pluginName + "' successfully initialized.");
         } catch (XmlBlasterException e) {
            //log.error(ME, "Initializing of plugin " + plugin.getType() + " failed:" + e.reason);
            throw new XmlBlasterException(ME+".NoInit", "Initializing of plugin " + plugin.getType() + " failed:" + e.reason);
         }
      }
      managers.put(pluginName, plugin);

      return plugin;
   }
}
