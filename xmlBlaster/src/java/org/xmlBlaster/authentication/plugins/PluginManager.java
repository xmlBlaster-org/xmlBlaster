package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import java.util.logging.Logger;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SessionInfo;

/**
 * Title: PluginManager
 * Description: Loads security plugin
 * @author W. Kleinertz (wkl)
 * @version 1.0
 */
public class PluginManager extends PluginManagerBase {
   private final String ME;
   private static final String defaultPluginName = "org.xmlBlaster.authentication.plugins.htpasswd.Manager"; //"org.xmlBlaster.authentication.plugins.simple.Manager";
   private static final String defaultPluginType = "htpasswd";
   private static final String defaultPluginVersion = "1.0";
   private              Authenticate        auth = null;
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(PluginManager.class.getName());

   public PluginManager(ServerScope glob) {
      super(glob);
      this.glob = glob;

      this.ME =  "SecurityPluginManager" + this.glob.getLogPrefixDashed();
      if (glob.getProperty().get("Security.Server.allowSimpleDriver", false)) {
         // Print a warning, because the old, unsecure xmlBlaster behavior is enabled!
         log.warning("* * * Security risk * * * : Security.Server.allowSimpleDriver=true");
         log.warning("The Simple security plugin is available, this is not save and can be misused by untrusted clients.");
      }
      
      String key = createPluginPropertyKey(defaultPluginType, defaultPluginVersion);
      if (glob.getProperty().get(key, (String)null) == null) {
         try { glob.getProperty().set(key, defaultPluginName); } catch(Exception e) { log.warning(e.toString()); }
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
    * <p>
    * Only one instance is created, further requests return the cached instance
    * </p>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Manager The security manager which is suitable to handle the request.
    * @exception XmlBlasterException Thrown if to suitable security manager has been found.
    */
   public I_Manager getManager(String type, String version) throws XmlBlasterException {
      if ("simple".equals(type)) {
         if (glob.getProperty().get("Security.Server.allowSimpleDriver", false) == false){
            throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "It's not allowed to use the simple security manager!");
         }
      }
      return (I_Manager)getPluginObject(type, version);
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
         log.severe("Authentication internal error, access denied");
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "Unknown session!");
      }
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      return sessionSecCtx.getManager();
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {}

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
      return defaultPluginName;
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
   public I_Manager getDummyManager() throws XmlBlasterException {
      return (I_Manager)super.getDummyPlugin();
   }
}
