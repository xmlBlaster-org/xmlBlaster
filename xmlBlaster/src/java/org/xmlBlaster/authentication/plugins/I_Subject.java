package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.3 $ (State: $State) (Date: $Date: 2002/02/12 21:52:39 $)
 */

public interface I_Subject {

   /**
    * Check if this subject is permitted to do something
    * <p/>
    * @param String The action the user tries to perfrom
    * @param String whereon the user tries to perform the action
    *
    * EXAMPLE:
    *    isAuthorized("publish", "thisIsAMessageKey");
    *
    * The above line checks if this subject is permitted to >>publish<<
    * a message under the key >>thisIsAMessageKey<<
    *
    * Known action keys:
    *    publish, subscribe, get, erase, ... see XmlBlasterImpl.PUBLISH etc.
    */
   public boolean isAuthorized(String actionKey, String key);

   /**
    * Get the subjects login-name.
    * <p/>
    *
    * @return String name
    */
   public String getName();
}
