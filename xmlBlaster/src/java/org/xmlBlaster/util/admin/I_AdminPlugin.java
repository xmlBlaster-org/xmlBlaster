/*------------------------------------------------------------------------------
Name:      I_AdminPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin;

/**
 * Declares available methods to control arbitrary plugins.   
 *
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.8
 */
public interface I_AdminPlugin extends I_AdminUsage {

   /**
    * The unique name of the plugin (together with the version). 
    * @return For example "IOR"
    */
   public java.lang.String getType();
   
   /**
    * The version of the plugin
    * @return For example "1.0"
    */
   public java.lang.String getVersion();

   /**
    * Shutdown the plugin, free resources.
    */
   public void shutdown() throws Exception;

   /**
    * Check status 
    * @return true if down
    */
   public boolean isShutdown();
}
