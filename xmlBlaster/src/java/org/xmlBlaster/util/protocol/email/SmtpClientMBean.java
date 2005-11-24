/*------------------------------------------------------------------------------
Name:      SmtpClientMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import org.xmlBlaster.util.admin.I_AdminSmtpClient;

/**
 * JMX control for Pop3Driver. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface SmtpClientMBean extends I_AdminSmtpClient {

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
}
