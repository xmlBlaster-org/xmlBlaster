package org.xmlBlaster.authentication.plugins;

import java.util.Map;

import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author  W. Kleinertz
 */

public interface I_Session extends I_MsgSecurityInterceptor {

   /*
    * Initialize a new session. 
    * <br \>
    * E.g.: An implementation could include authentication etc.
    * <p/>
    * @param String A qos-literal. The meaning will be defined by the real implementation.
    * @return String Like the securityQos param, but the other direction.
    * @exception XmlBlasterException The initialization failed (key exchange, authentication ... failed)
    * @deprecated This is never called, now #init(I_SecurityQos) is called
    */
   //public String init(String securityQos) throws XmlBlasterException;
	
	/**
	 * Initialize the session with useful information. 
	 * <p> 
	 * Is called before {@link #init(I_SecurityQos)} which does the authentication
	 * @param connectQos The current login information
	 * @param map Additional information, is currently null
	 * @return the connectQos we got, can be manipulated
	 */
	public ConnectQosServer init(ConnectQosServer connectQos, Map map) throws XmlBlasterException;

   /**
    * Initialize a new session and do the credential check. 
    * <br \>
    * E.g.: An implementation could include authentication etc.
    * <p/>
    * @param String The already parsed QoS. The meaning will be defined by the real implementation.
    * @return String Like the securityQos param, but the other direction.
    * @exception XmlBlasterException The initialization failed (key exchange, authentication ... failed)
    * @see #init(String)
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException;

   /**
    * Allows to check the given securityQos again.
    * <p>
    * Note:
    * </p>
    * <ul>
    *   <li>This call does not modify anything in the I_Session implementation.</li>
    *   <li>The init() method must have been invoked before, otherwise we return false</li>
    * </ul>
    * @param String The already parsed QoS. The meaning will be defined by the real implementation.
    * @return true If the credentials are OK<br />
    *         false If access is denied
    */
   public boolean verify(I_SecurityQos securityQos) throws XmlBlasterException;

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
    * {@link org.xmlBlaster.authentication.Authenticate#connect(org.xmlBlaster.engine.qos.ConnectQosServer, String)})
    * cannot provide a real sessionId when this object is created. Thus, it
    * uses a temporary id first and changes it to the real in a later step.<p>
    * The purpose of this method is to enable this functionality.<p>
    *
    * @param String The new sessionId.
    * @exception XmlBlasterException Thrown if the new sessionId is already in use.
    */
    // @deprecated
   public void changeSecretSessionId(String sessionId) throws XmlBlasterException;

   /**
    * Return the id of this session.
    * <p>
    * @param String The sessionId.
    */
   public String getSecretSessionId();

   /**
    * Check if this subject instance is permitted to do something
    * <p/>
    * @param sessionHolder Holding information about the subject which requires rights
    * @param dataHolder Holding information about the data which shall be accessed
    *
    * EXAMPLE:
    *    isAuthorized("publish", "thisIsAMessageKey");
    *
    * The above line checks if this subject is permitted to >>publish<<
    * a message under the key >>thisIsAMessageKey<<
    *
    * Known action keys:
    *    publish, subscribe, get, erase, ...
    */
   public boolean isAuthorized(SessionHolder sessionHolder, DataHolder dataHolder);
}
