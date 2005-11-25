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
    * Access the SMTP access uri,
    * for example "smtp://aUser:mypassword@mySmtpHost.org:25"
    * @return The SMTP server uri
    */
   public String getUri();

   /**
    * @param uri For example "smtp://aUser:mypassword@mySmtpHost.org:25"
    */
   public void setUri(String uri);
}
