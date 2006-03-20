/*------------------------------------------------------------------------------
Name:      I_Timeout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Interface you need to implement to receive timeout notifications
 *
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Timeout
{

   /**
   * You will be notified about the timeout through this method.
   * @param userData You get bounced back your userData which you passed
   *                 with Timeout.addTimeoutListener()
   */
   public void timeout(Object userData);
}
