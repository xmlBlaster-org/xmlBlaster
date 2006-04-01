package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import java.util.Hashtable;

/**
 * This security manager just implements the necessary interfaces
 * and allows everything - everybody may login, and everybody
 * may do anything with the messages (publish, subscribe ...)
 *
 * @author Wolfgang Kleinertz
 */

public class Manager implements I_Manager{
   private static final String          ME = "SimpleManager";
   private static final String        TYPE = "simple";
   private static final String     VERSION = "1.0";
   private Global glob = null;

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();

   public Manager() {
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.glob = glob;
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

   public I_Session reserveSession(String sessionId) {
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

   void changeSecretSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
      synchronized(sessions) {
         Session session = (Session)sessions.get(oldSessionId);
         if (session == null) throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_CONNECTIONFAILURE, ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_CONNECTIONFAILURE, ME+".invalidSessionId", "This sessionId is already in use!");
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
      return new Subject(glob, name); // dummy implementation
   }

   public void shutdown() throws XmlBlasterException {
   }
}
