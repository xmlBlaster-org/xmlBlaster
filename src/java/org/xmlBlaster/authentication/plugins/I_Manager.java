package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author  kleinerz
 * @version $Revision: 1.7 $ (State: $State) (Date: $Date$)
 */

public interface I_Manager extends I_Plugin
{
   // --- session handling ----------------------------------------------------

   // querySubjects(String query, String syntax)

   /**
    * The session handling. 
    * <code>org.xmlBlaster.authentication.authenticate.init(...)</code>
    * and <code>login(...)</code> calls this method to get a new I_Session
    * and bind it to the session.
    * <p/>
    * @param String sessionId
    */
   public I_Session reserveSession(String sessionId) throws XmlBlasterException;

   /**
    * Releases a reserved I_Session.
    * <p/>
    * @param String The id of the session, which has to be released.
    * @param String This qos literal could contain a proof of authenticity, etc.
    */
   public void releaseSession(String sessionId, String qos_literal) throws XmlBlasterException;

   /**
    * Get the I_Session which corresponds to the given sessionId. 
    * <p/>
    * @param String The sessionId
    * @return I_Session
    */
   public I_Session getSessionById(String id) throws XmlBlasterException;

}
