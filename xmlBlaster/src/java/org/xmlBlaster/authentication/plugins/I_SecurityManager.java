package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: I_SecurityManager.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.2  2001/08/19 22:24:46  ruff
 *    (Ported to security framework, first step
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

public interface I_SecurityManager {
   // --- - - - - - - - - -----------------------------------------------------

   /**
    * This method is called by the PluginLoader.
    * <p/>
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(String[] options) throws org.xmlBlaster.util.XmlBlasterException;

   public String getType();
   public String getVersion();

   // --- session handling ----------------------------------------------------

   // querySubjects(String query, String syntax)

   /**
    * The session handling (<code>org.xmlBlaster.authentication.authenticate.init(...)</code>
    * and <code>login(...)</code>) calls this method to get a new I_SessionSecurityContext
    * and bind it to the session.
    * <p/>
    * @param String sessionId
    */
   public I_SessionSecurityContext reserveSessionSecurityContext(String sessionId);

   /**
    * Releases a reserved I_SessionSecurityContext.
    * <p/>
    * @param String The id of the session, which has to be released.
    * @param String This qos literal could contain a proof of authenticity, etc.
    */
   public void releaseSessionSecurityContext(String sessionId, String qos_literal);

   /**
    * Get the I_SessionSecurityContext which corresponds to the given sessionId
    * <p/>
    * @param String The sessionId
    * @return I_SessionSecurityContext
    */
   public I_SessionSecurityContext getSessionById(String id);

}
