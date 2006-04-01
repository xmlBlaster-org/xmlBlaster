package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * A client helper.
 * Class for java clients, decrypting messages which
 * came from the corresponding security plugin. 
 * <p />
 * If for example the server security plugin crypts
 * messages with rot13, we need to decrypt it on the
 * client side with the same algorithm. This is done here.
 * <p />
 * The reason is for clients to access xmlBlaster
 * transparently from the authentication method
 * <p />
 * For every plugin type you need, you need on instance of this class.
 */
public interface I_ClientPlugin extends I_MsgSecurityInterceptor {

   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException;

   /**
    * @return The plugin type, e.g. "htpasswd"
    */
   public String getType();

   /**
    * @return The plugin version, e.g. "1.0"
    */
   public String getVersion();

   /**
    * Access a helper class to generate the xml string for authentication.
    * This string is used as part of the qos on connect.
    * <p />
    * Note that with each invocation a new instance is returned
    * which you have to initialize with your credentials (e.g. your 
    * login name and password).
    */
   public I_SecurityQos createSecurityQos();
   
   public void setSessionData(String sessionData);
}
