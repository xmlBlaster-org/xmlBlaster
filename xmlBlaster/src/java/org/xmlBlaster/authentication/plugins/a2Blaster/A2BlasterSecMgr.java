package org.xmlBlaster.authentication.plugins.a2Blaster;

import java.util.Hashtable;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.I_SecurityManager;
import org.xmlBlaster.authentication.plugins.I_SessionSecurityContext;
import org.a2Blaster.client.api.CorbaConnection;
import org.a2Blaster.engine.A2BlasterException;
import org.a2Blaster.Environment;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: A2BlasterSecMgr.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 10:48:53  kleinertz
 *    (wkl: a2Blaster-plugin added
 *    ()
 */

public class A2BlasterSecMgr implements I_SecurityManager{
   private static final String          ME = "A2BlasterSecMgr";

   public  static final String        TYPE = "a2Blaster";
   public  static final String     VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();

   private              CorbaConnection a2Blaster = null;
   private              Environment           env = new Environment();
   private              String                LCN = "xmlBlaster";

   public A2BlasterSecMgr() {
      if (Log.CALL) Log.call(ME+"."+ME+"()", "-------START--------\n");
      if (Log.CALL) Log.call(ME+"."+ME+"()", "-------END----------\n");
   }

   /**
    * Initialize the Manager.
    * <p/>
    * @param XmlBlasterException
    */
   public void init(String[] options) throws org.xmlBlaster.util.XmlBlasterException {
      if (Log.CALL) Log.call(ME+".init()", "-------START--------\n");
      if (options.length>0){
         Log.warn(ME+".init()", "Got unexpected options! Check xmlBlasters configuration!");
      }
      if (Log.CALL) Log.call(ME+".init()", "-------END--------\n");
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
    * @return I_SessionSecurityContext
    */
   public I_SessionSecurityContext reserveSessionSecurityContext(String sessionId){
      if (Log.CALL) Log.call(ME+".reserveSessionSecurityContext(String sessionId="+sessionId+")", "-------START--------\n");
      A2BlasterSessionSecCtx session = new A2BlasterSessionSecCtx(this, sessionId);
      sessions.put(sessionId, session);
      if (Log.CALL) Log.call(ME+".reserveSessionSecurityContext(String sessionId="+sessionId+")", "-------END----------\n");

      return session;
   }

   /**
    * Release a no longer used session.
    * <p/>
    * @author W. Kleinertz
    * @param String Specifies the session. (sessionId)
    */
   public void releaseSessionSecurityContext(String sessionId, String qos_literal){
      if (Log.CALL) Log.call(ME+".releaseSessionSecurityContext(String sessionId="
                            +sessionId+", String qos_literal="+qos_literal+")",
                            "-------START--------\n");
      I_SessionSecurityContext sessionSecCtx = getSessionById(sessionId);
      sessions.remove(sessionId);
      ((A2BlasterSessionSecCtx)sessionSecCtx).destroy(qos_literal);
      if (Log.CALL) Log.call(ME+".releaseSessionSecurityContext(...)", "-------END---------\n");
   }

   /**
    * Get the I_SessionSecurityContext which corresponds to the given sessionId
    * <p/>
    * @param String The sessionId
    * @return I_SessionSecurityContext
    */
   public I_SessionSecurityContext getSessionById(String id) {
      if (Log.CALL) Log.call(ME+".getSessionById(String id="+id+")", "-------CALLED-------\n");
      return (I_SessionSecurityContext)sessions.get(id);
   }

   CorbaConnection getA2Blaster() throws XmlBlasterException{
      if (Log.CALL) Log.call(ME+".getA2Blaster()", "-------START------\n");

      if(a2Blaster == null) {
         try {
            a2Blaster = new CorbaConnection();
         }
         catch (A2BlasterException e) {
            Log.error(ME+".getA2Blaster()", "Connecting a2Blaster failed!!!");
            throw new XmlBlasterException(ME+".getA2Blaster()", "Connecting a2Blaster failed!!!");
         }
      }

      if (Log.CALL) Log.call(ME+".getA2Blaster()", "-------END-------\n");

      return a2Blaster;
   }

   void changeSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME + ".changeSessionId(String oldSessionId=" + oldSessionId +
                             ", String newSessionID=" +newSessionId+")", "-------START------\n");
      synchronized(sessions) {
         A2BlasterSessionSecCtx session = (A2BlasterSessionSecCtx)sessions.get(oldSessionId);
         if (session == null) throw new XmlBlasterException(ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(ME+".invalidSessionId", "This sessionId is already in use!");
         sessions.put(newSessionId, session);
         sessions.remove(oldSessionId);
      }
      if (Log.CALL) Log.call(ME+".changeSessionId(...)", "-------END-------\n");
   }

}
