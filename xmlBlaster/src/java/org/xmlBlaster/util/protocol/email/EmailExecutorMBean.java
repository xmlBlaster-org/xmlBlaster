/**
 * 
 */
package org.xmlBlaster.util.protocol.email;

import org.xmlBlaster.util.protocol.RequestReplyExecutorMBean;

/**
 * @author xmlblast
 *
 */
public interface EmailExecutorMBean extends RequestReplyExecutorMBean {
   /**
    * @return Returns the cc.
    */
   public String getCc();

   /**
    * @param cc The cc to set.
    */
   public void setCc(String cc);

   public String getTo();

   public String getFrom();

   /**
    * @return Returns the bcc.
    */
   public String getBcc();

   /**
    * @param bcc The bcc to set.
    */
   public void setBcc(String bcc);
}
