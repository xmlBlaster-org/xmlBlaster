/*------------------------------------------------------------------------------
Name:      I_AdminSmtpClient.java
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
 * @see org.xmlBlaster.util.protocol.email.SmtpClient
 */
public interface I_AdminSmtpClient extends I_AdminUsage {
   /**
    * Access the SMTP host name
    * @return The SMTP server host name
    */
   public String getHost();

   /**
    * @param hostname the host name of the SMTP server
    */
   public void setHost(String host);

   /**
    * @return The SMTP server port
    */
   public int getPort();

   /**
    * Set the SMTP server port
    */
   public void setPort(int port);
   
   /**
    * Access the SMTP user name
    * @return The SMTP server user name
    */
   public String getUser();

   /**
    * @param user the user name to login to the SMTP server
    */
   public void setUser(String user);

   /**
    * Access the SMTP password name
    * @return The SMTP server password name
    */
   public String getPassword();

   /**
    * @param password the password name to login to the SMTP server
    */
   public void setPassword(String password);
}
