package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 *
 *
 * @author  W. Kleinertz
 * @version $Revision: 1.5 $ (State: $State) (Date: $Date: 2001/12/20 22:04:41 $)
 */

public interface I_Session {

   /**
    * Initialize a new session.<br\>
    * E.g.: An implementation could include authentication etc.
    * <p/>
    * @param String A qos-literal. The meaning will be defined by the real implementation.
    * @return String Like the securityQos param, but the other direction.
    * @exception XmlBlasterException The initialization failed (key exchange, authentication ... failed)
    */
   public String init(String securityQos) throws XmlBlasterException;


   /**
    * Initialize a new session.<br\>
    * E.g.: An implementation could include authentication etc.
    * <p/>
    * @param String The already parsed QoS. The meaning will be defined by the real implementation.
    * @return String Like the securityQos param, but the other direction.
    * @exception XmlBlasterException The initialization failed (key exchange, authentication ... failed)
    * @see #init(String)
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException;


   /**
    * Get the owner of this session.
    * <p/>
    * @param I_Subject The owner.
    */
   public I_Subject getSubject();

   /**
    * How controls this session?
    * <p/>
    * @return I_Manager
    */
   public I_Manager getManager();

   /**
    * The current implementation of the user session handling (especially
    * {@link org.xmlBlaster.Authenticate#connect(String, String)})
    * cannot provide a real sessionId when this object is created. Thus, it
    * uses a temporary id first and changes it to the real in a later step.<p>
    * The purpose of this method is to enable this functionality.<p>
    *
    * @param String The new sessionId.
    * @exception XmlBlasterException Thrown if the new sessionId is already in use.
    * @deprecated
    */
   public void changeSessionId(String sessionId) throws XmlBlasterException;

   /**
    * Return the id of this session.
    * <p>
    * @param String The sessionId.
    */
   public String getSessionId();

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
