/*------------------------------------------------------------------------------
Name:      I_ConnectionStatusListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * Listen to connection states of the dispatcher framework.
 * <p>
 * Register with a call to DispatchManager.addConnectionStatusListener()
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ConnectionStatusListener
{
   void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState);
   void toAliveSync(DispatchManager dispatchManager, ConnectionStateEnum oldState);
   void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState);
   /**
    * @param dispatchManager
    * @param oldState
    * @param xmlBlasterException Can be null
    */
   void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, XmlBlasterException xmlBlasterException);
}
