package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;

/**
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.3 $ (State: $State) (Date: $Date: 2001/09/04 15:09:30 $)
 */

public class Subject implements I_Subject {
   private String ME="SimpleSecuritySubject";
   private String name = null;


   public Subject() {
   }


   public Subject(String userId) {
      this.name = userId;
   }


   public void init(String userId) {
      this.name = userId;
   }


   /**
    * Check if the user is permited (authorized) to do something
    */
   public boolean isAuthorized(String actionKey, String key) {
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
      Log.info(ME, "Access for " + getName() + " granted, without further checks.");
   }

}
