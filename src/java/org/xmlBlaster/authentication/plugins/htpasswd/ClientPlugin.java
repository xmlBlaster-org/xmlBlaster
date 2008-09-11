package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.htpasswd.SecurityQos;


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
 *
 * @see org.xmlBlaster.authentication.plugins.htpasswd.SecurityQos
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a> 16/11/01 09:06
 */
public class ClientPlugin implements I_ClientPlugin {
   public static final String ME = "ClientPlugin";
   private Global glob;
   private PluginInfo pluginInfo;

   public ClientPlugin() {
   }

   public void init(Global glob_, PluginInfo pluginInfo_) {
      this.glob = glob_;
      this.pluginInfo = pluginInfo_;
   }

   public String getType()
   {
      return (this.pluginInfo == null) ? "htpasswd" : this.pluginInfo.getType();
   }

   public String getVersion()
   {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * The client application can use this method to get a new I_SecurityQos instance,
    * and use it to set userId/password etc.
    */
   public I_SecurityQos createSecurityQos()
   {
      return new SecurityQos(this.glob, this); // "htpasswd" "1.0"
   }

   public void setSessionData(String sessionData)
   {

   }

   // --- message handling ----------------------------------------------------

   /**
    * decrypt, check, unseal an incomming message. 
    * <p/>
    * @param MsgUnitRaw The the received message
    * @param MethodName The name of the method which is intercepted
    * @return MsgUnitRaw The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException
   {
      return dataHolder.getMsgUnitRaw();
   }

   /**
    * encrypt, sign, seal an outgoing message. 
    * <p/>
    * We do noting in this simple case.
    * @param MsgUnitRaw The source message
    * @return MsgUnitRaw
    * @exception XmlBlasterException Thrown if the message cannot be processed
    */
   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException
   {
      return dataHolder.getMsgUnitRaw();
   }
}
