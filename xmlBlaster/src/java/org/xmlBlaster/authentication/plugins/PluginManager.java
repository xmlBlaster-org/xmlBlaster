package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SessionInfo;
import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * Title: PluginManager
 * Description: Loads security plugin
 * @author W. Kleinertz (wkl)
 * @version 1.0
 */
public class PluginManager extends PluginManagerBase {
   private static final String                ME = "SecurityPluginManager";
   private static final String defaultPluginName = "org.xmlBlaster.authentication.plugins.simple.Manager";
   private              Authenticate        auth = null;
   private final Global glob;

   public PluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      if (glob.getProperty().get("Security.Server.allowSimpleDriver", true)) {
         // Print a warning, because the old, unsecure xmlBlaster behavior is enabled!
         Log.warn(ME, "* * * Security risk * * * : Security.Server.allowSimpleDriver=true");
         Log.warn(ME, "The Simple security plugin is available, this is not save and can be misused by untrusted clients.");
      }
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
    * NOTE: This method authenticates when getting the SessionInfo object with the sessionId
    * @param String sessionId
    * @param I_Manager
    * @exception Thrown, if the session is unknown.
    */
   public I_Manager getManager(String sessionId) throws XmlBlasterException {
      SessionInfo sessionInfo = auth.check(sessionId);
      if (sessionInfo==null) { // Should never be null, if access is denied an XmlBlasterException is thrown
         Log.error(ME, "Authentication internal error, access denied");
         throw new XmlBlasterException(ME+".NoAccess","Unknown session!");
      }
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      return sessionSecCtx.getManager();
   }

   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   protected String getPluginPropertyName() {
      return "Security.Server.Plugin";
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return "org.xmlBlaster.authentication.plugins.simple.Manager";
   }

   /**
    * Returns the security manager, responsible for given session.
    * </p>
    * @param String sessionId
    * @param I_Manager
    * @exception Thrown, if the session is unknown.
    */
   public I_Manager getManager(SessionInfo sessionInfo) throws XmlBlasterException {
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      return sessionSecCtx.getManager();
   }


   /**
    * Tries to return an instance of the default security manager, which simulates
    * the old xmlBlaster behavior.
    *
    */
   public I_Manager getDummyManager() {
      return (I_Manager)super.getDummyPlugin();
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
      if (type == null || type.equals("simple")) {
         if (glob.getProperty().get("Security.Server.allowSimpleDriver", true) == false){
            throw new XmlBlasterException(ME+".NoAccess","It's not allowed to use the standard security manager!");
         }
      }

      return super.choosePlugin(type, version);
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
   protected I_Manager loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      return (I_Manager)super.instantiatePlugin(pluginNameAndParam);
   }
}
