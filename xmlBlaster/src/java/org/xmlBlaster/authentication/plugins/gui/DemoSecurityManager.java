package org.xmlBlaster.authentication.plugins.gui;

import org.xmlBlaster.authentication.plugins.I_SecurityManager;
import org.xmlBlaster.authentication.plugins.I_SessionSecurityContext;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Hashtable;
import java.awt.*;

/**
 * This example pops up a GUI, and a user has to confirm
 * all logins and authorize messages manually.
 */
public class DemoSecurityManager implements I_SecurityManager{
   private static final String          ME = "DemoSecurityManager";

   private static final String        TYPE = "gui";
   private static final String     VERSION = "1.0";

   // this is the simplest, but not the best way to handle sessions
   // --> a pool would be a good idea at this point :)
   private              Hashtable sessions = new Hashtable();

   private boolean packFrame = false;
   private DemoPluginGUI frame;

   public DemoSecurityManager() {
      Log.call(ME+"."+ME+"()", "-------START--------\n");
      Log.info(ME+"."+ME+"()", "Starting GUI ...");
      frame = new DemoPluginGUI();
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
      Log.info(ME+"."+ME+"()", "... GUI started.");
      Log.call(ME+"."+ME+"()", "-------END----------\n");
   }

   public void init(String[] options) throws org.xmlBlaster.util.XmlBlasterException {
      Log.call(ME+".init()", "-------START--------\n");
      if (options.length>0) {
         Log.warn(ME+".init()", "Got unexpected options! Check xmlBlasters configuration!");
      }
      Log.call(ME+".init()", "-------END--------\n");
   }

   public String getType() {
      return TYPE;
   }

   public String getVersion() {
      return VERSION;
   }


   public I_SessionSecurityContext reserveSessionSecurityContext(String sessionId) {
      Log.trace(ME+".reserveSessionSecurityContext(String sessionId="+sessionId+")", "-------START--------\n");
      DemoSession session = new DemoSession(this, sessionId);
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
         DemoSession session = (DemoSession)sessions.get(oldSessionId);
         if (session == null) throw new XmlBlasterException(ME+".unknownSessionId", "Unknown sessionId!");
         if (sessions.get(newSessionId) != null) throw new XmlBlasterException(ME+".invalidSessionId", "This sessionId is already in use!");
         sessions.put(session, newSessionId);
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


   DemoSubject getSubject(String name) throws XmlBlasterException {
      // throw new XmlBlasterException(ME + ".unknownSubject", "There is no user called " + name);
      return new DemoSubject(frame, name); // dummy implementation
   }

   DemoPluginGUI getGUI() {
      return frame;
   }
}
