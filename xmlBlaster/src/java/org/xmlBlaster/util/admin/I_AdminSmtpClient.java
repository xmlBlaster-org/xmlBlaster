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
    * Force the MsgUnit attachment to always be base64 encoded.
    * @return Force base64
    */
   public boolean isContentForceBase64();
   
   /** 
    * Setting this to true we can force the MsgUnit attachment to
    * always be base64 encoded.
    * <br />
    * Javamail does base64 encoding automatically if need so
    * the default of this variable is false.
    * @param contentForceBase64
    */
   public void setContentForceBase64(boolean contentForceBase64);
   
   /**
    * @return Returns the messageIdForceBase64.
    */
   public boolean isMessageIdForceBase64();
   
   /** 
    * Setting this to true we can force the messageId attachment to
    * always be base64 encoded.
    * <br />
    * Javamail does base64 encoding automatically if need so
    * the default of this variable is false.
    */
   public void setMessageIdForceBase64(boolean messageIdForceBase64);

   /**
    * Access the SMTP access uri,
    * for example "smtp://aUser:mypassword@mySmtpHost.org:25"
    * @return The SMTP server uri
    */
   public String getSmtpUrl();

   /**
    * @param uri For example "smtp://aUser:mypassword@mySmtpHost.org:25"
    */
   public void setSmtpUrl(String uri);
}
