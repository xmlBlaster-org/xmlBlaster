package org.xmlBlaster.util;

import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * Baseclass to load plugins. 
 * <p />
 * A typical syntax in the xmlBlaster.properties file is:
 * <pre>
 *   MimeSubscribePlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,DEFAULT_MIN_LEN=20
 * </pre>
 * @author W. Kleinertz (wkl) H. Goetzger
 * @version 1.0
 */
abstract public class PluginManagerBase {

   private static String ME = "PluginManagerBase";
   protected Hashtable managers = new Hashtable(); // currently loaded plugins
   private Global glob;

   protected PluginManagerBase(org.xmlBlaster.util.Global glob) {
      this.glob = glob;
   }

   /**
    * Return a specific plugin.
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Plugin The plugin which is suitable to handle the request.
    * @exception XmlBlasterException Thrown if no suitable plugin has been found.
    */
   public I_Plugin getPluginObject(String type, String version) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME+".getPluginObject()", "Loading plugin type=" + type + " version=" + version);
      I_Plugin plug = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if((pluginNameAndParam!=null) &&
         (pluginNameAndParam[0]!=null) &&
         (!pluginNameAndParam.equals("")))
      {
         // check in hash if plugin is instanciated already
         plug = (I_Plugin)managers.get(pluginNameAndParam[0]);
         if (plug!=null) return plug;

         // not in hash, instanciat plugin
         plug = instantiatePlugin(pluginNameAndParam);
      }
      else {
         throw new XmlBlasterException(ME+".notSupported","The requested plugin [" + type + "][" + version + "] isn't supported!");
      }

      return plug;
   }

   /**
    * @param type can be null
    * @param version can be null
    * @return please return your default plugin classname or null if not specified
    */
   abstract public String getDefaultPluginName(String type, String version);


   /**
    * Tries to return an instance of the default plugin.
    *
    */
   public I_Plugin getDummyPlugin() {

      String name = getDefaultPluginName(null, null);
      if (name == null)
         return null;

      I_Plugin plug = (I_Plugin)managers.get(name);
      if (plug!=null) return plug;

      try {
         String[] defPlgn={name};
         plug = instantiatePlugin(defPlgn);
      } catch(XmlBlasterException e) {} // ???

      return plug;
   }


   /**
    * Check if the requested plugin is supported.
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return boolean true, if supported. else -> false
    */
   public boolean isSupported(String type, String version) {
      // currently just a dummy implementation
      // thus, it's impossible the switch the default security manager off

      return true;
   }


   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   abstract protected String getPluginPropertyName();

   /**
    * @return e.g. "Security.Server.Plugin[simple][1.0]"
    */
   public final String getPluginPropertyName(String type, String version) {
      StringBuffer buf = new StringBuffer(80);
      buf.append(getPluginPropertyName()).append("[").append(type).append("][").append(version).append("]");
      return buf.toString();
   }

   /**
    * Resolve type and version to the plugins name
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return String The name of the requested plugin.
    */
   protected String[] choosePlugin(String type, String version) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering choosePlugin(" + type + ", " + version + ")");
      String[] pluginData=null;
      String rawString;

      if (isSupported(type, version)) {
         rawString = glob.getProperty().get(getPluginPropertyName(type, version), (String)null);
         if (rawString==null) {
            if (type != null)
               Log.warn(ME, "Plugin type=" + type + " version=" + version + "not found, choosing default plugin");
            rawString = getDefaultPluginName(type, version);
         }
         if(rawString!=null) {
            Vector tmp = new Vector();
            StringTokenizer st = new StringTokenizer(rawString, ",=");
            while(st.hasMoreTokens()) {
               tmp.addElement(st.nextToken());
            }
            //pluginData = (String[])tmp.toArray();
            pluginData=new String[tmp.size()];
            for(int i=0;i<tmp.size();i++) {
               pluginData[i]=(String)tmp.elementAt(i);
            }
         }
         //else
         //   Log.warn(ME, "Accessing " + getPluginPropertyName(type, version) + " failed, no such entry found in xmlBlaster.properties");
      }
      if (pluginData != null && pluginData[0].equalsIgnoreCase("")) pluginData = null;

      return pluginData;
   }

   /**
    * Loads a security manager.
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.authentication.plugins.Manager<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_Plugin
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_Plugin instantiatePlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      // separate parameter and plugin name
      String[] param= new String[pluginNameAndParam.length-1];
      String pluginName = pluginNameAndParam[0];
      System.arraycopy(pluginNameAndParam,1,param,0,pluginNameAndParam.length-1);

      I_Plugin manager = null;
      try {
         if (Log.TRACE) Log.trace(ME, "Trying Class.forName('" + pluginName + "') ...");
         Class cl = java.lang.Class.forName(pluginName);
         manager = (I_Plugin)cl.newInstance();
         if (Log.TRACE) Log.trace(ME, "Found I_Plugin '" + pluginName + "'");
      }
      catch (IllegalAccessException e) {
         Log.error(ME, "The plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin");
         throw new XmlBlasterException(ME+".NoClass", "The Plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin");
      }
      catch (SecurityException e) {
         Log.error(ME, "No right to access the plugin class or initializer '" + pluginName + "'");
         throw new XmlBlasterException(ME+".NoAccess", "No right to access the plugin class or initializer '" + pluginName + "'");
      }
      catch (Throwable e) {
         Log.error(ME, "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".Invalid", "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: " + e.toString());
      }

      // Initialize the plugin
      if (manager != null) {
         try {
            manager.init(glob, param);
            Log.info(ME, "Plugin '" + pluginName + "' successfully initialized.");
         } catch (XmlBlasterException e) {
            //Log.error(ME, "Initializing of plugin " + manager.getType() + " failed:" + e.reason);
            throw new XmlBlasterException(ME+".NoInit", "Initializing of plugin " + manager.getType() + " failed:" + e.reason);
         }
      }
      managers.put(pluginName, manager);

      return manager;
   }
}
