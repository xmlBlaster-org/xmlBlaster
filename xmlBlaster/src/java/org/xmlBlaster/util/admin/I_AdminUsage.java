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
public interface I_AdminUsage {
   
   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage();
   
   /**
    * @return A link on javadoc for JMX usage
    */
   public java.lang.String getUsageUrl();

   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url);
}
