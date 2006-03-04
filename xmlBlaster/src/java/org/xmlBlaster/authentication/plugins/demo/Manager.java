package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import java.util.Hashtable;
import java.awt.*;

/**
 * This example pops up a GUI, and a user has to confirm
 * all logins and authorize messages manually.
 */
public class Manager implements I_Manager{
   private static final String          ME = "Manager";
   private Global glob;
   private static Logger log = Logger.getLogger(Manager.class.getName());

   private static final String        TYPE = "gui";
   private static final String     VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();

   private boolean packFrame = false;
   private PluginGUI frame;

   public Manager() {
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.glob = glob;

      log.finer("-------START--------\n");
      log.info("Starting GUI ...");
      frame = new PluginGUI();
      //Validate frames that have preset sizes
      //Pack frames that have useful preferred size info, e.g. from their layout
      if (packFrame) {
         frame.pack();
      }
      else {
         frame.validate();
      }
      //Center the window
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension frameSize = frame.getSize();
      if (frameSize.height > screenSize.height) {
         frameSize.height = screenSize.height;
      }
      if (frameSize.width > screenSize.width) {
         frameSize.width = screenSize.width;
      }
      frame.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
      frame.setVisible(true);
      log.info("... GUI started.");
      log.finer("-------END----------\n");
   }

   public String getType() {
      return TYPE;
   }

   public String getVersion() {
      return VERSION;
   }

   public final Global getGlobal() {
      return this.glob;
   }

   public I_Session reserveSession(String sessionId) {
      log.fine("-------START--------\n");
      Session session = new Session(this, sessionId);
      synchronized(sessions) {
         sessions.put(sessionId, session);
      }
      log.fine("-------END--------\n");

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
         if (session == null) throw new XmlBlasterException(ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(ME+".invalidSessionId", "This sessionId is already in use!");
         sessions.put(session, newSessionId);
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
      return new Subject(frame, name); // dummy implementation
   }

   PluginGUI getGUI() {
      return frame;
   }

   public void shutdown() throws XmlBlasterException {
   }

}
