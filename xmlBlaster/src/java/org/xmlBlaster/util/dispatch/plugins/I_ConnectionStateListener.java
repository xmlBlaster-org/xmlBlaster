/*------------------------------------------------------------------------------
Name:      I_ConnectionStateListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins;

import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * Listen to connection states of the dispatcher framework. 
 * <p>
 * @author ruff@swand.lake.de
 */
public interface I_ConnectionStateListener
{
   void toAlive(DeliveryManager deliveryManager, ConnectionStateEnum oldState);
   void toPolling(DeliveryManager deliveryManager, ConnectionStateEnum oldState);
   void toDead(DeliveryManager deliveryManager, ConnectionStateEnum oldState, String errorText);
}
