package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.ClientInfo;
import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * Title: PluginManager
 * Description: Loads security plugin
 * @author W. Kleinertz (wkl)
 * @version 1.0
 */

public class PluginManager {
   private static final String                ME = "SecurityPluginManager";
   private static final String defaultPluginName = "org.xmlBlaster.authentication.plugins.simple.Manager";
   private static       PluginManager          me = null;
   private              Authenticate        auth = null;
   private              Hashtable       managers = new Hashtable(); // currently loaded plugins

   public PluginManager() {
      if (XmlBlasterProperty.get("Security.Server.allowSimpleDriver", true)) {
         // Print a warning, because the old, unsecure xmlBlaster behavior is enabled!
         Log.warn(ME, "* * * Security risk * * * : Security.Server.allowSimpleDriver=true");
         Log.warn(ME, "The Simple security plugin is available, this is not save and can be misused by untrusted clients.");
      }
   }

   /**
    * Return an instance of this singleton
    *
    * @return PluginManager
    */
   public static PluginManager getInstance() {
      if (me!=null) return me;

      me = new PluginManager();
      return me;
   }

   /**
    * Initialize the PluginManager
    * <p/>
    */
   public void init(Authenticate auth) {
      this.auth = auth;
   }

   /**
    * Return a specific SecurityManager, suitable to handle the requested
    * security mechanisms.
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Manager The security manager which is suitable to handle the request.
    * @exception XmlBlasterException Thrown if to suitable security manager has been found.
    */
   public I_Manager getManager(String type, String version) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME+".getManager()", "Loading SecurityManager type=" + type + " version=" + version);
      I_Manager securityManager = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if((pluginNameAndParam!=null) &&
         (pluginNameAndParam[0]!=null) &&
         (!pluginNameAndParam.equals("")))
      {
         securityManager = (I_Manager)managers.get(pluginNameAndParam[0]);
         if (securityManager!=null) return securityManager;

         securityManager = loadPlugin(pluginNameAndParam);
      }
      else {
         throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
      }

      return securityManager;
   }

   /**
    * Returns the security manager, responsible for given session.
    * </p>
    * @param String sessionId
    * @param I_Manager
    * @exception Thrown, if the session is unknown.
    */
   public I_Manager getManager(String sessionId) throws XmlBlasterException {
      ClientInfo clntInfo = auth.check(sessionId);
      if (clntInfo==null)
         throw new XmlBlasterException(ME+".NoAccess","Unknown session!");
      I_Session sessionSecCtx = clntInfo.getSecuritySession();

      return sessionSecCtx.getManager();
   }

   /**
    * Tries to return an instance of the default security manager, which simulates
    * the old xmlBlaster behavior.
    *
    */
   public I_Manager getDummyManager() {
      I_Manager securityManager = (I_Manager)managers.get(defaultPluginName);
      if (securityManager!=null) return securityManager;

      try {
         String[] defPlgn={defaultPluginName};
         securityManager = loadPlugin(defPlgn);
      } catch(XmlBlasterException e) {}

      return securityManager;
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
    * Resolve type and version to the plugins name
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return String The name of the requested plugin.
    */
   private String[] choosePlugin(String type, String version) throws XmlBlasterException
   {
      String[] pluginData=null;
      String rawString;

      if (isSupported(type, version)) {
         rawString = XmlBlasterProperty.get("Security.Server.Plugin["+type+"]["+version+"]", (String)null);
         if (rawString==null) {
            if (XmlBlasterProperty.get("Security.Server.allowSimpleDriver", true) == false){
               throw new XmlBlasterException(ME+".NoAccess","It's not allowed to use the standard security manager!");
            }
            else {
               rawString = defaultPluginName;
            }
         }
         if(rawString!=null) {
            Vector tmp = new Vector();
            StringTokenizer st = new StringTokenizer(rawString, ",");
            while(st.hasMoreTokens()) {
               tmp.addElement(st.nextToken());
            }
            //pluginData = (String[])tmp.toArray();
            pluginData=new String[tmp.size()];
            for(int i=0;i<tmp.size();i++) {
               pluginData[i]=(String)tmp.elementAt(i);
            }
         }
      }
      if (pluginData[0].equalsIgnoreCase("")) pluginData = null;

      return pluginData;
   }

   /**
    * Loads a security manager.
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.authentication.plugins.Manager<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_Manager
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   private I_Manager loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      // separate parameter and plugin name
      String[] param= new String[pluginNameAndParam.length-1];
      String pluginName = pluginNameAndParam[0];
      System.arraycopy(pluginNameAndParam,1,param,0,pluginNameAndParam.length-1);

      I_Manager manager = null;
      try {
         if (Log.TRACE) Log.trace(ME, "Trying Class.forName('" + pluginName + "') ...");
         Class cl = java.lang.Class.forName(pluginName);
         manager = (I_Manager)cl.newInstance();
         Log.info(ME, "Found I_Manager '" + pluginName + "'");
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
         throw new XmlBlasterException(ME+".Invalid", "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: " + e.toString());
      }

      // Initialize the plugin
      if (manager != null) {
         try {
            manager.init(param);
            Log.info(ME, "Plugin '" + pluginName + "' successfully initialized!");
         } catch (XmlBlasterException e) {
            //Log.error(ME, "Initializing of plugin " + manager.getType() + " failed:" + e.reason);
            throw new XmlBlasterException(ME+".NoInit", "Initializing of plugin " + manager.getType() + " failed:" + e.reason);
         }
      }
      managers.put(pluginName, manager);

      return manager;
   }
}
