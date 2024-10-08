/*------------------------------------------------------------------------------
Name:      I_AdminPop3Driver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin;

/**
 * Declares available methods to control the POP3 email poller.  
 *
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.8
 * @see org.xmlBlaster.util.protocol.email.Pop3Driver
 */
public interface I_AdminPop3Driver extends I_AdminService {
   /**
    * Access the polling interval
    * @return The pollingInterval in milli seconds
    */
   public long getPollingInterval();

   /**
    * Set the polling interval
    * @param pollingInterval [milli seconds]
    */
   public void setPollingInterval(long pollingInterval);
   
   /**
    * Access the POP3 MTA access configuration
    * @return For example "pop3://user:password@host:port/INBOX"
    */
   public String getPop3Url();

   /**
    * @param pop3Url Syntax is "pop3://user:password@host:port/INBOX"
    */
   public void setPop3Url(String pop3Url);
   
   /**
    * Access a list of registered java listeners. 
    * @return Keys of interested party for incoming POP3 mails
    */
   public String getListeners();

   /**
    * @return Returns the holdbackExpireTimeout.
    */
   public long getHoldbackExpireTimeout();

   /**
    * Configure how long a not yet dispatched message shall live in the Pop3Driver map.
    * Note that the map is transient, after restart such mails are lost (but resend by the server). 
    * @param holdbackExpireTimeout In milli seconds, set to zero to switch holback off
    */
   public void setHoldbackExpireTimeout(long holdbackExpireTimeout);
   
   /**
    * Get the number of received emails for which no java bean is interested. 
    * @return Number of emails
    */
   public int getNumberOfHoldbackEmails();
}
