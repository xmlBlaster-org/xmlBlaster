package org.xmlBlaster.authentication.plugins.ldap;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Hashtable;

/**
 * This security manager just implements the necessary interfaces
 * and allows everything - everybody may login, and everybody
 * may do anything with the messages (publish, subscribe ...)
 *
 * @author Wolfgang Kleinertz
 */

public class Manager implements I_Manager{
   private static final String ME = "Manager";
   private static final String TYPE = "ldap";
   private static final String VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();


   public Manager() {
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


   public I_Session reserveSession(String sessionId) throws XmlBlasterException {
      Log.trace(ME+".reserveSession(String sessionId="+sessionId+")", "-------START--------\n");
      Session session = new Session(this, sessionId);
      synchronized(sessions) {
         sessions.put(sessionId, session);
      }
      Log.trace(ME+".reserveSession(...))", "-------END--------\n");

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
}
