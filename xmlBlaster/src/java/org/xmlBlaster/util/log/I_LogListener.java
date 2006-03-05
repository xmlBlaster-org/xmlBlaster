package org.xmlBlaster.util.log;


import java.util.logging.LogRecord;

/**
 * Get logging events of type SEVERE or WARNING
 * @author marcel
 * @see org.xmlBlaster.util.log.XbNotifyHandler#register(int, I_LogListener)
 */
public interface I_LogListener {
   /**
    * Event fired when a logging occurres. 
    * @param record
    */
   public void log(LogRecord record);
}
