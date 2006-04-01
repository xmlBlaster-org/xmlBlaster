package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;

public class Subject implements I_Subject {
   private String    name = null;
   private PluginGUI gui;

   public Subject(PluginGUI gui) {
      this.gui=gui;
   }


   public Subject(PluginGUI gui, String userId) {
      this.gui  = gui;
      this.name = userId;
   }


   public void init(String userId) {
      this.name = userId;
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
      gui.printAction(MethodName.CONNECT);
      gui.printKey("");
      gui.printName(name);
      if(!gui.getAccessDecision()) throw new XmlBlasterException(Global.instance(),
            ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, "AccessDenied!", "Login Failed");
   }


   /**
    * @return Returns the gui.
    */
   public PluginGUI getGui() {
      return this.gui;
   }

}
