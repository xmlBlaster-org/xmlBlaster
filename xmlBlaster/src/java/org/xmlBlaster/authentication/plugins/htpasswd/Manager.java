package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Hashtable;

/**
 * This security manager just implements the necessary interfaces
 * and delegates login checks (authentication of a client) to LDAP.
 * <p />
 * Authorization is not implemented, please read javadoc of LdapGateway
 * if you need LDAP authorization.
 *
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a> 16/11/01 09:06
 * @see org.xmlBlaster.authentication.plugins.htpasswd.LdapGateway
 */

public class Manager implements I_Manager {

   private Global glob = null;

   private static final String ME = "PasswdManager";
   private static final String TYPE = "htpasswd";
   private static final String VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private Hashtable sessions = new Hashtable();

   public Manager() {
      if (Log.CALL) Log.call(ME, "Constructor");
   }

   /**
    * @see org.xmlBlaster.util.I_Plugin#init(org.xmlBlaster.util.Global glob, String[] options)
    */
   public void init(Global glob, String[] options) throws org.xmlBlaster.util.XmlBlasterException {
      this.glob = glob;
      if (Log.TRACE) Log.trace(ME+".init()", "Entering init");
      if (options.length>0) {
         Log.warn(ME+".init()", "Got unexpected options! Check xmlBlasters configuration!");
      }
      if (Log.TRACE) Log.trace(ME+".init()", "Leaving init");
   }

   final Global getGlobal() {
      return this.glob;
   }

   public String getType() {
      return TYPE;
   }

   public String getVersion() {
      return VERSION;
   }


   public I_Session reserveSession(String sessionId) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME, "reserveSession(sessionId="+sessionId+")");
      Session session = new Session(this, sessionId);
      synchronized(sessions) {
         sessions.put(sessionId, session);
      }

      return session;
   }

   public void releaseSession(String sessionId, String qos_literal){
      synchronized(sessions) {
         sessions.remove(sessionId);
      }
   }

   void changeSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
      synchronized(sessions) {
         Session session = (Session)sessions.get(oldSessionId);
         if (session == null) throw new XmlBlasterException(ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(ME+".invalidSessionId", "This sessionId is already in use!");
         sessions.put(newSessionId, session);
         sessions.remove(oldSessionId);
      }
   }

   /**
    * Get the I_Session which corresponds to the given sessionId
    * <p/>
    * @param String The sessionId
    * @return I_Session
    */
   public I_Session getSessionById(String id) {
      synchronized(sessions) {
         return (I_Session)sessions.get(id);
      }
   }

   Subject getSubject(String name) throws XmlBlasterException {
      // throw new XmlBlasterException(ME + ".unknownSubject", "There is no user called " + name);
      return new Subject(name); // dummy implementation
   }

}//class Manager
