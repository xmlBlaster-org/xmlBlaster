package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 *
 *
 * @author  W. Kleinertz
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: I_SessionSecurityContext.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 09:13:47  ruff
 *    (Changed locations for security stuff, added RMI support
 *    (
 *    (Revision 1.1.2.3  2001/08/13 12:19:50  kleinertz
 *    (wkl: minor fixes
 *    (
 *    (Revision 1.1.2.2  2001/05/21 07:37:28  kleinertz
 *    (wkl: some javadoc tags removed
 *    (
 *    (Revision 1.1.2.1  2001/05/17 13:54:30  kleinertz
 *    (wkl: the first version with security framework
 *    ()
 */

public interface I_SessionSecurityContext {

   /**
    * Initialize a new session.<br\>
    * E.g.: An implementation could include authentication etc.
    * <p/>
    * @param String A qos-literal. The meaning will be defined by the real implementation.
    * @return String Like the securityQoS param, but the other direcrion.
    * @exception XmlBlasterException The initialization failed (key exchange, authentication ... failed)
    */
   public String init(String securityQoS) throws XmlBlasterException;

   /**
    * Get the owner of this session.
    * <p/>
    * @param I_SubjectSecurityContext The owner.
    */
   public I_SubjectSecurityContext getSubject();

   /**
    * How controls this session?
    * <p/>
    * @return I_SecurityManager
    */
   public I_SecurityManager getSecurityManager();

   /**
    * The current implementation of the user session handling (especially
    * {@link org.xmlBlaster.Authenticate#init(String, String)})
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
