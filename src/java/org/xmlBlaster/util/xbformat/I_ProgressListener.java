/*------------------------------------------------------------------------------
Name:      I_ProgressListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

/**
 * This interface is used to inform listeners about incoming data. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_ProgressListener
{
      /**
       * Notification about the current data receive progress. 
       * <p />
       * The interval of notification is arbitrary and not guaranteed,
       * each protocol driver may choose other strategies.
       * @param name A qualifying name about the incoming request, can be empty.
       * @param currBytesRead The number of bytes received up to now
       * @param numBytes The overall number of bytes
       */
      void progressRead(String name, long currBytesRead, long numBytes);
      
      /**
       * Notification about the current data send progress. 
       * <p />
       * The interval of notification is arbitrary and not guaranteed,
       * each protocol driver may choose other strategies.
       * @param name A qualifying name about the incoming request, can be empty.
       * @param currBytesWritten The number of bytes send up to now
       * @param numBytes The overall number of bytes
       */
      void progressWrite(String name, long currBytesWritten, long numBytes);
}

