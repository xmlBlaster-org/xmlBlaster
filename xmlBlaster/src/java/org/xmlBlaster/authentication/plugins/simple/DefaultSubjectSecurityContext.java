package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_SubjectSecurityContext;
import org.xmlBlaster.util.XmlBlasterException;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: DefaultSubjectSecurityContext.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 09:13:48  ruff
 *    (Changed locations for security stuff, added RMI support
 *    (
 *    (Revision 1.1.2.1  2001/05/17 13:54:30  kleinertz
 *    (wkl: the first version with security framework
 *    ()
 */

public class DefaultSubjectSecurityContext implements I_SubjectSecurityContext {
   private String           name = null;


   public DefaultSubjectSecurityContext() {
   }


   public DefaultSubjectSecurityContext(String userId) {
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

   }

}
