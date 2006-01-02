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
    * Send a test email. 
    * @param to For example "demo@localhost"
    * @param from For example "xmlBlaster@localhost"
    * @return A success description
    */
   public String sendTestEmail(String to, String from);
}
