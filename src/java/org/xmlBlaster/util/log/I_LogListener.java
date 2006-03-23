package org.xmlBlaster.util.log;


import java.util.logging.LogRecord;

/**
 * Get logging events of type SEVERE or WARNING
 * @see org.xmlBlaster.util.log.XbNotifyHandler#register(int, I_LogListener)
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.logging.html">The admin.logging requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface I_LogListener {
   /**
    * Event fired when a logging occurres. 
    * @param record
    */
   public void log(LogRecord record);
}
