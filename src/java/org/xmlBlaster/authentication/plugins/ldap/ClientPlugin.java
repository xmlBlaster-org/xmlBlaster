package org.xmlBlaster.authentication.plugins.ldap;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.ldap.SecurityQos;
import org.xmlBlaster.util.def.MethodName;

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
 * @see org.xmlBlaster.authentication.plugins.ldap.SecurityQos
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
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
      return "ldap";
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
      return new SecurityQos(this.glob); // "ldap" "1.0"
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
   public MsgUnitRaw importMessage(MsgUnitRaw msg, MethodName action) throws XmlBlasterException
   {
      return msg;
   }

   /**
    * decrypt, check, unseal an incomming message. 
    * <p/>
    * We do noting in this simple case.
    * @param String The the received message
    * @return String The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public String importMessage(String xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }

   public byte[] importMessage(byte[] xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }

   /**
    * encrypt, sign, seal an outgoing message. 
    * <p/>
    * We do noting in this simple case.
    * @param MsgUnitRaw The source message
    * @return MsgUnitRaw
    * @exception XmlBlasterException Thrown if the message cannot be processed
    */
   public MsgUnitRaw exportMessage(MsgUnitRaw msg, MethodName action) throws XmlBlasterException
   {
      return msg;
   }

   public String exportMessage(String xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }

   public byte[] exportMessage(byte[] xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }
}
