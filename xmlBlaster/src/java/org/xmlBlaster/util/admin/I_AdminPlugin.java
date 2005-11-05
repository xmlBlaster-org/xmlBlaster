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
public interface I_AdminPlugin {
   
   public java.lang.String getType();
   
   public java.lang.String getVersion();

   /**
    * Shutdown the plugin, free resources.
    */
   public void shutdown();

   /**
    * Check status 
    * @return true if down
    */
   public boolean isShutdown();

   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage();
}
