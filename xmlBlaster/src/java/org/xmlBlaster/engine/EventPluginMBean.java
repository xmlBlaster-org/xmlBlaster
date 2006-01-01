/*------------------------------------------------------------------------------
Name:      EventPluginMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.admin.I_AdminService;

/**
 * JMX control for the native EventPlugin.  
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface EventPluginMBean extends I_AdminService {
   /**
    * Depending on the configuration setting <code>mail.collectMillis</code>
    * emails are collected to not send too many emails in too
    * short period of time. 
    * Here it is possible to look into such emails.
    * <p>
    * The default collection time is 12 hours.
    * @return The XML dump of the pending emails.  
    */
   public String dumpPendingEmails();

   /**
    * Depending on the configuration setting <code>mail.collectMillis</code>
    * emails are collected to not send too many emails in too
    * short period of time. 
    * Here it is possible to look into such emails.
    * <p>
    * The default collection time is 12 hours.
    * @return Number of removed emails, usually 1 as all events are collected to one mail 
    */
   public int clearPendingEmails();
   
   public int getNumOfPendingEmails();
   
   /**
    * Triggers a default email to test the configuration. 
    * @return true if send
    */
   public boolean triggerTestEmail();
   
   /**
    * Depending on the configuration setting <code>mail.collectMillis</code>
    * emails are collected to not send too many emails in too
    * short period of time. 
    * Here it is possible to send such emails now.
    * <p>
    * The default collection time is 12 hours.
    * @return Number of send emails, usually 1 as all events are collected to one mail 
    */
   public int triggerPendingEmails();
}
