package org.xmlBlaster.authentication.plugins.gui;

import org.xmlBlaster.authentication.plugins.I_SubjectSecurityContext;
import org.xmlBlaster.util.XmlBlasterException;

public class DemoSubject implements I_SubjectSecurityContext {
   private String       name = null;
   private DemoPluginGUI gui;

   public DemoSubject(DemoPluginGUI gui) {
      this.gui=gui;
   }


   public DemoSubject(DemoPluginGUI gui, String userId) {
      this.gui  = gui;
      this.name = userId;
   }


   public void init(String userId) {
      this.name = userId;
   }


   /**
    * Check if the user is permited (authorized) to do something
    */
   public boolean isAuthorized(String actionKey, String key) {
      gui.printAction(actionKey);
      gui.printKey(key);
      gui.printName(name);
      return gui.getAccessDecision(); // dummy implementation;
//      return true;
   }

   public String getName() {
      return name;
   }


   /**
    * Authenticate the user
    * <p/>
    * @param String Password
    * @exception XmlBlasterExceotion Thrown if the user has no valid proof of his identity
    */
   void authenticate(String passwd) throws XmlBlasterException {
      // throw new XmlBlasterException(ME + ".authenticationFailed", "Wrong identity!");
      // dummy implementation
      gui.printAction("LOGIN");
      gui.printKey("");
      gui.printName(name);
      if(!gui.getAccessDecision()) throw new XmlBlasterException("AccessDenied!", "Login Failed");
   }

}
