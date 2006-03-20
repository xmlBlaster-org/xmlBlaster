/*------------------------------------------------------------------------------
Name:      I_SignalListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * @author xmlBlaster@marcelruff.info
 * @see SignalCatcher
 */
public interface I_SignalListener {
   /**
   * You will be notified when the runtime exits. 
   */
   public void shutdownHook();
}
