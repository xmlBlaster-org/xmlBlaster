package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import java.util.logging.Logger;
import java.util.logging.Level;
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
 */

public class Manager implements I_Manager {

   private Global glob = null;
   private static Logger log = Logger.getLogger(Manager.class.getName());

   private static final String ME = "PasswdManager";
   private static final String TYPE = "htpasswd";
   private static final String VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   protected Hashtable sessions = new Hashtable();

   public Manager() {
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.glob = (glob == null) ? Global.instance() : glob;

   }

   public final Global getGlobal() {
      return this.glob;
   }

   public String getType() {
      return TYPE;
   }

   public String getVersion() {
      return VERSION;
   }


   public I_Session reserveSession(String sessionId) throws XmlBlasterException {
      if (log != null && log.isLoggable(Level.FINER)) log.finer("reserveSession(sessionId="+sessionId+")");
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

   public void changeSecretSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
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

   public void shutdown() throws XmlBlasterException {
   }


}//class Manager

