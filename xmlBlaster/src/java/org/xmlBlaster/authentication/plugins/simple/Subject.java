package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import java.util.logging.Logger;

/**
 * @author  $Author$ ($Name:  $)
 * @version $Revision: 1.6 $ (State: $State) (Date: $Date$)
 */

public class Subject implements I_Subject {
   private static Logger log = Logger.getLogger(Subject.class.getName());
   private String name = null;


   public Subject(Global glob) {
      this(glob, null);
   }

   public Subject(Global glob, String userId) {
      this.name = userId;
   }


   public void init(String userId) {
      this.name = userId;
   }


   /**
    * Check if the user is permited (authorized) to do something
    */
   public boolean isAuthorized(MethodName actionKey, String key) {
//System.out.println("### User: "+getName()+" is permitted to "+actionKey+" "+key+" ###");
      return true; // dummy implementation;
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
      log.info("Access for " + getName() + " granted, without further checks.");
   }

}
