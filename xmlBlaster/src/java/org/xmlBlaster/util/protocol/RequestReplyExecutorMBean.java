/**
 * 
 */
package org.xmlBlaster.util.protocol;

import org.xmlBlaster.util.admin.I_AdminPlugin;

/**
 * JMX manage Request/Reply pattern. 
 * @author xmlblast@marcelruff.info
 */
public interface RequestReplyExecutorMBean extends I_AdminPlugin {
   public void setResponseTimeout(long millis);
   
   /**
    * Set the given millis to protect against blocking client for ping invocations. 
    * @param millis If <= 0 it is set to the default (one minute).
    * An argument less than or equal to zero means not to wait at all
    * and is not supported
    */
   public void setPingResponseTimeout(long millis);

   /**
    * Set the given millis to protect against blocking client for update() invocations. 
    * @param millis If <= 0 it is set to the default (one minute).
    * An argument less than or equal to zero means not to wait at all
    * and is not supported
    */
   public void setUpdateResponseTimeout(long millis);
   
   /**
    * @return Returns the responseTimeout.
    */
   public long getResponseTimeout(String methodName);
   
   public long getPingResponseTimeout();

   /**
    * The invocation timeout for all remaining method calls like "publish", "connect", "subscribe"
    * but NOT for "ping" and "update" 
    * @return Returns the responseTimeout.
    */
   public long getResponseTimeout();
   
   
   /**
    * Interrupts a blocking request with a not returning reply. 
    * The pending message is handled as not delivered and will be queued
    */
   public int interruptInvocation();


   public boolean isCompressZlib();
   
   /**
    * Compressing too small messages won't reduce the size
    * @return The number of bytes, only compress if bigger
    */
   public int getMinSizeForCompression();
   
   public void setMinSizeForCompression(int minSizeForCompression);

   public boolean isCompressZlibStream();

   /**
    * @return Returns the updateResponseTimeout.
    */
   public long getUpdateResponseTimeout();

   /**
    * @return Returns the useEmailExpiryTimestamp.
    */
   public boolean isUseEmailExpiryTimestamp();

   /**
    * @param useEmailExpiryTimestamp The useEmailExpiryTimestamp to set.
    */
   public void setUseEmailExpiryTimestamp(boolean useEmailExpiryTimestamp);
}
