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
    * @return A comment about the send mail
    */
   public String sendTestEmail();
   
   /**
    * Triggers a log.severe to test the configuration. 
    * @return A comment about the test
    */
   public String triggerTestLogSevere();

   /**
    * Triggers a log.warning to test the configuration. 
    * @return A comment about the test
    */
   public String triggerTestLogWarning();

   /**
    * Depending on the configuration setting <code>mail.collectMillis</code>
    * emails are collected to not send too many emails in too
    * short period of time. 
    * Here it is possible to send such emails now.
    * <p>
    * The default collection time is 12 hours.
    * @return Comment about send emails, usually all events are collected to one mail 
    */
   public String sendPendingEmails();
   
   /**
    * How long to collect outgoing emails?
    * @return Returns the mailCollectMillis or -1 if no email sink is configured
    */
   public long getMailCollectMillis();

   /**
    * @param mailCollectMillis The mailCollectMillis to set.
    */
   public void setMailCollectMillis(long mailCollectMillis);
   
   /**
    * The comma separated list of active events.  
    * @return Returns the eventTypes.
    */
   public String getEventTypes();

   /**
    * A comma separated list of active events. 
    * @param eventTypes A comma separated list of active events 
    */
   public void setEventTypes(String eventTypes);

   /**
    * Configuration properties of the email sink. 
    * @return Returns the comma separated properties
    */
   public String getSmtpDestinationConfiguration();

   /**
    * Configuration properties of the email sink. 
    * @param smtpDestinationConfiguration The comma separated properties to set.
    */
   public void setSmtpDestinationConfiguration(String smtpDestinationConfiguration);
   
   /**
    * The JMX configuration setup from xmlBlasterPlugins.xml
    * @return
    */
   public String getJmxDestinationConfiguration();

   /**
    * The publish-message configuration setup from xmlBlasterPlugins.xml
    * @return Returns the publishDestinationConfiguration.
    */
   public String getPublishDestinationConfiguration();
   
   /**
    * Manually trigger a heart beat message. 
    * @return Success text
    */
   public String triggerHeartbeatNotification();
}
