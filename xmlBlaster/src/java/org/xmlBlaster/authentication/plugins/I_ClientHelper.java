package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * A client helper.
 *
 * The reason is for clients to access xmlBlaster
 * transparently from the authentication method
 */
public interface I_ClientHelper {
   public void init(String[] param) throws XmlBlasterException;

   public I_InitQos getInitQoSWrapper();
   public void setSessionData(String sessionData);

   // --- message handling ----------------------------------------------------

   /**
    * decrypt, check, unseal ... an incomming message
    * <p/>
    * @param MessageUnit The the received message
    * @return MessageUnit The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #importMessage(MessageUnit)
    */
   public MessageUnit importMessage(MessageUnit msg) throws XmlBlasterException;

   public String importMessage(String xmlMsg) throws XmlBlasterException;

   /**
    * encrypt, sign, seal ... an outgoing message
    * <p/>
    * @param MessageUnit The source message
    * @return MessageUnit
    * @exception XmlBlasterException Thrown if the message cannot be processed
    * @see #importMessage(MessageUnit)
    */
   public MessageUnit exportMessage(MessageUnit msg) throws XmlBlasterException;

   public String exportMessage(String xmlMsg) throws XmlBlasterException;

}
