package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;

/**
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
    *    publish, subscribe, get, erase, ...
    */
   public boolean isAuthorized(MethodName actionKey, String key);

   /**
    * Get the subjects login-name.
    * <p/>
    *
    * @return String name
    */
   public String getName();
}
