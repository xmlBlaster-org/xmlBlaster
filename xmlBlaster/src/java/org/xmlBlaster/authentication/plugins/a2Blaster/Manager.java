package org.xmlBlaster.authentication.plugins.a2Blaster;

import java.util.Hashtable;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.a2Blaster.client.api.CorbaConnection;
import org.a2Blaster.engine.A2BlasterException;
import org.a2Blaster.Environment;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.6 $ (State: $State) (Date: $Date: 2002/09/13 23:17:50 $)
 * Last Changes:
 *    ($Log: Manager.java,v $
 *    (Revision 1.6  2002/09/13 23:17:50  ruff
 *    (Ported completely to LogChannel (org.xmlBlaster.util.Log is deprecated)
 *    (
 *    (Revision 1.5  2002/08/26 11:04:16  ruff
 *    (INCOMPATIBLE plugin interface change!
 *    (This is of importance only if you use your own plugins.
 *    (The I_Plugin interface method changed from
 *    (  public void init(org.xmlBlaster.util.Global glob, String[] options)
 *    (to
 *    (  public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo)
 *    (
 *    (Revision 1.4  2002/08/26 09:10:05  ruff
 *    (Ported to redesigned plugin framework
 *    (
 *    (Revision 1.3  2002/04/19 11:05:45  ruff
 *    (Changed plugin framework to pass util.Global to plugins
 *    (Changed manager to be no singleton any more
 *    (
 *    (Revision 1.2  2001/08/30 17:14:49  ruff
 *    (Renamed security stuff
 *    (
 *    (Revision 1.1.2.2  2001/08/22 11:35:42  ruff
 *    (Changed names
 *    (
 *    (Revision 1.1.2.1  2001/08/22 11:18:42  ruff
 *    (changed naming schema
 *    (
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 10:48:53  kleinertz
 *    (wkl: a2Blaster-plugin added
 *    ()
 */

public class Manager implements I_Manager{
   private static final String          ME = "Manager";
   private              Global        glob;
   private              LogChannel     log;

   public  static final String        TYPE = "a2Blaster";
   public  static final String     VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();

   private              CorbaConnection a2Blaster = null;
   private              Environment           env = new Environment();
   private              String                LCN = "xmlBlaster";

   public Manager() {
   }

   /**
    * Initialize the Manager.
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws org.xmlBlaster.util.XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("a2Blaster");
   }

   final Global getGlobal() {
      return this.glob;
   }

   /**
    * Which kind of security service does this package support?
    * In this case: a2Blaster - style authentication and authorisation
    * <p/>
    * @return String The type.
    */
   public String getType() {
      return TYPE;
   }

   /**
    * Which version is supported?
    * <p/>
    * @return String. The version.
    */
   public String getVersion() {
      return VERSION;
   }

   /**
    * Create a new user session.
    * <p/>
    * @param String The session id.
    * @return I_Session
    */
   public I_Session reserveSession(String sessionId){
      if (log.CALL) log.call(ME+".reserveSession(String sessionId="+sessionId+")", "-------START--------\n");
      Session session = new Session(this, sessionId);
      sessions.put(sessionId, session);
      if (log.CALL) log.call(ME+".reserveSession(String sessionId="+sessionId+")", "-------END----------\n");

      return session;
   }

   /**
    * Release a no longer used session.
    * <p/>
    * @author W. Kleinertz
    * @param String Specifies the session. (sessionId)
    */
   public void releaseSession(String sessionId, String qos_literal){
      if (log.CALL) log.call(ME+".releaseSession(String sessionId="
                            +sessionId+", String qos_literal="+qos_literal+")",
                            "-------START--------\n");
      I_Session sessionSecCtx = getSessionById(sessionId);
      sessions.remove(sessionId);
      ((Session)sessionSecCtx).destroy(qos_literal);
      if (log.CALL) log.call(ME+".releaseSession(...)", "-------END---------\n");
   }

   /**
    * Get the I_Session which corresponds to the given sessionId
    * <p/>
    * @param String The sessionId
    * @return I_Session
    */
   public I_Session getSessionById(String id) {
      if (log.CALL) log.call(ME+".getSessionById(String id="+id+")", "-------CALLED-------\n");
      return (I_Session)sessions.get(id);
   }

   CorbaConnection getA2Blaster() throws XmlBlasterException{
      if (log.CALL) log.call(ME+".getA2Blaster()", "-------START------\n");

      if(a2Blaster == null) {
         try {
            a2Blaster = new CorbaConnection();
         }
         catch (A2BlasterException e) {
            log.error(ME+".getA2Blaster()", "Connecting a2Blaster failed!!!");
            throw new XmlBlasterException(ME+".getA2Blaster()", "Connecting a2Blaster failed!!!");
         }
      }

      if (log.CALL) log.call(ME+".getA2Blaster()", "-------END-------\n");

      return a2Blaster;
   }

   void changeSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
      if (log.CALL) log.call(ME + ".changeSessionId(String oldSessionId=" + oldSessionId +
                             ", String newSessionID=" +newSessionId+")", "-------START------\n");
      synchronized(sessions) {
         Session session = (Session)sessions.get(oldSessionId);
         if (session == null) throw new XmlBlasterException(ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(ME+".invalidSessionId", "This sessionId is already in use!");
         sessions.put(newSessionId, session);
         sessions.remove(oldSessionId);
      }
      if (log.CALL) log.call(ME+".changeSessionId(...)", "-------END-------\n");
   }

}
