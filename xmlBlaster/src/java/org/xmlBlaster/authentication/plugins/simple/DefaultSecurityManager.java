package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_SecurityManager;
import org.xmlBlaster.authentication.plugins.I_SessionSecurityContext;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Hashtable;

/**
 * This security manager just implements the necessary interfaces
 * and allows everything - everybody may login, and everybody
 * may do anything with the messages (publish, subscribe ...)
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: DefaultSecurityManager.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 09:13:48  ruff
 *    (Changed locations for security stuff, added RMI support
 *    (
 *    (Revision 1.1.2.4  2001/08/18 22:30:26  ruff
 *    (Compiles and runs - temporary saving
 *    (
 *    (Revision 1.1.2.3  2001/08/13 12:19:50  kleinertz
 *    (wkl: minor fixes
 *    (
 *    (Revision 1.1.2.2  2001/05/21 07:37:28  kleinertz
 *    (wkl: some javadoc tags removed
 *    (
 *    (Revision 1.1.2.1  2001/05/17 13:54:29  kleinertz
 *    (wkl: the first version with security framework
 *    ()
 */

public class DefaultSecurityManager implements I_SecurityManager{
   private static final String          ME = "DefaultSecurityManager";

   private static final String        TYPE = "simple";
   private static final String     VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();


   public DefaultSecurityManager() {
      Log.trace(ME+"."+ME+"()", "-------START--------\n");
      Log.trace(ME+"."+ME+"()", "-------END----------\n");
   }

   public void init(String[] options) throws org.xmlBlaster.util.XmlBlasterException {
      Log.trace(ME+".init()", "-------START--------\n");
      if (options.length>0) {
         Log.warn(ME+".init()", "Got unexpected options! Check xmlBlasters configuration!");
      }
      Log.trace(ME+".init()", "-------END--------\n");
   }

   public String getType() {
      return TYPE;
   }

   public String getVersion() {
      return VERSION;
   }


   public I_SessionSecurityContext reserveSessionSecurityContext(String sessionId) {
      Log.trace(ME+".reserveSessionSecurityContext(String sessionId="+sessionId+")", "-------START--------\n");
      DefaultSessionSecurityContext session = new DefaultSessionSecurityContext(this, sessionId);
      synchronized(sessions) {
         sessions.put(sessionId, session);
      }
      Log.trace(ME+".reserveSessionSecurityContext(...))", "-------END--------\n");

      return session;
   }

   public void releaseSessionSecurityContext(String sessionId, String qos_literal){
      synchronized(sessions) {
         sessions.remove(sessionId);
      }
   }

   void changeSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
      synchronized(sessions) {
         DefaultSessionSecurityContext session = (DefaultSessionSecurityContext)sessions.get(oldSessionId);
         if (session == null) throw new XmlBlasterException(ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(ME+".invalidSessionId", "This sessionId is already in use!");
         sessions.put(newSessionId, session);
         sessions.remove(oldSessionId);
      }
   }

   /**
    * Get the I_SessionSecurityContext which corresponds to the given sessionId
    * <p/>
    * @param String The sessionId
    * @return I_SessionSecurityContext
    */
   public I_SessionSecurityContext getSessionById(String id) {
      synchronized(sessions) {
         return (I_SessionSecurityContext)sessions.get(id);
      }
   }


   DefaultSubjectSecurityContext getSubject(String name) throws XmlBlasterException {
      // throw new XmlBlasterException(ME + ".unknownSubject", "There is no user called " + name);
      return new DefaultSubjectSecurityContext(name); // dummy implementation
   }

}
