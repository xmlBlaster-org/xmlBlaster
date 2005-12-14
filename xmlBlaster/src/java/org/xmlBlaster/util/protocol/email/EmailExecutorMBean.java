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
    * A comma separated list of email addresses, e.g. "joe@localhost,jack@localhost"
    * @param cc The carbon copy addresses
    */
   public void setCc(String cc);

   public String getTo();

   public void setTo(String to);
   
   public String getFrom();

   public void setFrom(String from);

   /**
    * @return Returns the bcc.
    */
   public String getBcc();

   /**
    * A comma separated list of email addresses, e.g. "joe@localhost,jack@localhost"
    * @param bcc The blind cc to set.
    */
   public void setBcc(String bcc);
}
