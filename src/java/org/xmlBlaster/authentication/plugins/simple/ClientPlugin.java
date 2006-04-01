package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.simple.SecurityQos;

/**
 * Helper only for Java clients. 
 * <p />
 * This class does nothing else than:
 * <p />
 * 1. Generate the xml string
 *    which is passed as the connect() or login() qos string
 *    with the help of SecurityQos.java
 * <p />
 * 2. The messages are not crypted or modified, so
 *    the importMessage() and exportMessage() methods do nothing.
 * @see org.xmlBlaster.authentication.plugins.demo.SecurityQos
 */
public class ClientPlugin implements I_ClientPlugin {
   public static final String ME = "ClientPlugin";
   private Global glob;

   public ClientPlugin() {
   }

   public void init(Global glob, PluginInfo pluginInfo) {
      this.glob = glob;
   }

   public String getType()
   {
      return "simple";
   }

   public String getVersion()
   {
      return "1.0";
   }

   /**
    * The client application can use this method to get a new I_SecurityQos instance,
    * and use it to set userId/password etc.
    */
   public I_SecurityQos createSecurityQos()
   {
      return new SecurityQos(this.glob); // "simple" "1.0"
   }

   public void setSessionData(String sessionData)
   {

   }

   // --- message handling ----------------------------------------------------
   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException
   {
      return dataHolder.getMsgUnitRaw();
   }

   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException
   {
      return dataHolder.getMsgUnitRaw();
   }
}
