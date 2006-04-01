package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;

/**
 * Interface declaring methods to intercept messages in the security layer
 * to allow crypt/encrypt etc. messages before sending. 
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
public interface I_MsgSecurityInterceptor {

   /**
    * Decrypt, check, unseal etc an incomming message. 
    * <p/>
    * Use this to import (decrypt) the xmlKey or xmlQos
    * @param dataHolder A container holding the MsgUnitRaw and some additional informations
    * @return The original or modified message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #exportMessage(CryptDataHolder)
    */
   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException;

   /**
    * Encrypt, sign, seal an outgoing message. 
    * <p/>
    * Use this to export (encrypt) the xmlKey or xmlQos
    * @param dataHolder A container holding the MsgUnitRaw and some additional informations
    * @return The probably more secure string
    * @exception XmlBlasterException Thrown if the message cannot be processed
    * @see #importMessage(CryptDataHolder)
    */
   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException;
}
