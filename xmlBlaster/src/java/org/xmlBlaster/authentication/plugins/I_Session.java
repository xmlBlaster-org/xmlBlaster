package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * @author  W. Kleinertz
 */

public interface I_Session extends I_MsgSecurityInterceptor {

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
    * {@link org.xmlBlaster.authentication.Authenticate#connect(org.xmlBlaster.util.ConnectQos, String)})
    * cannot provide a real sessionId when this object is created. Thus, it
    * uses a temporary id first and changes it to the real in a later step.<p>
    * The purpose of this method is to enable this functionality.<p>
    *
    * @param String The new sessionId.
    * @exception XmlBlasterException Thrown if the new sessionId is already in use.
    */
    // @deprecated
   public void changeSessionId(String sessionId) throws XmlBlasterException;

   /**
    * Return the id of this session.
    * <p>
    * @param String The sessionId.
    */
   public String getSessionId();
}
