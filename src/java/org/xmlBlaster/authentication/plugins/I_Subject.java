package org.xmlBlaster.authentication.plugins;

/**
 * Represents a person or a company or a remote server process.
 * <p> 
 * This subject may login multiple times, having like this
 * multiple I_Session instances. 
 */
public interface I_Subject {

   /**
    * Get the subjects login-name.
    * <p/>
    *
    * @return String name
    */
   public String getName();
}
