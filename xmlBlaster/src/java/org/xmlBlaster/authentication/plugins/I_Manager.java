package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.3 $ (State: $State) (Date: $Date: 2001/09/08 22:23:11 $)
 */

public interface I_Manager
{
   /**
    * This method is called by the PluginManager.
    * <p/>
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(String[] options) throws XmlBlasterException;

   public String getType();
   public String getVersion();

   // --- session handling ----------------------------------------------------

   // querySubjects(String query, String syntax)

   /**
    * The session handling (<code>org.xmlBlaster.authentication.authenticate.init(...)</code>
    * and <code>login(...)</code>) calls this method to get a new I_Session
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
    * Get the I_Session which corresponds to the given sessionId
    * <p/>
    * @param String The sessionId
    * @return I_Session
    */
   public I_Session getSessionById(String id) throws XmlBlasterException;

}
