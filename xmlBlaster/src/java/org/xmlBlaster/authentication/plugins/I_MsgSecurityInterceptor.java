package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.MethodName;

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
    * decrypt, check, unseal etc an incomming message. 
    * <p/>
    * Use this to import (decrypt) the xmlKey or xmlQos
    * @param str The the received message (which is probably crypted)
    * @return The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #exportMessage(String)
    */
   public String importMessage(String str) throws XmlBlasterException;
   /** Use this to import (decrypt) the content */
   public byte[] importMessage(byte[] content) throws XmlBlasterException;
   /** Use this to import (decrypt) separately the xmlKey,content,qos of MsgUnitRaw */
   /** @param MethodName The name of the method which is intercepted */
   public MsgUnitRaw importMessage(MsgUnitRaw msg, MethodName method) throws XmlBlasterException;

   /**
    * encrypt, sign, seal an outgoing message. 
    * <p/>
    * Use this to export (encrypt) the xmlKey or xmlQos
    * @param str The source message
    * @return The probably more secure string
    * @exception XmlBlasterException Thrown if the message cannot be processed
    * @see #importMessage(String)
    */
   public String exportMessage(String str) throws XmlBlasterException;
   /** Use this to export (encrypt) the content */
   public byte[] exportMessage(byte[] content) throws XmlBlasterException;
   /** Use this to export (encrypt) separately the xmlKey,content,qos of MsgUnitRaw */
   public MsgUnitRaw exportMessage(MsgUnitRaw msg, MethodName method) throws XmlBlasterException;
}
