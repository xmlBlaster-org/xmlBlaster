/*------------------------------------------------------------------------------
Name:      CbDeliveryConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.dispatch;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.dispatch.DeliveryConnection;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.DeliveryConnectionsHandler;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;

/**
 * Holding all necessary infos to establish a remote
 * connection and invoke update()/updateOneway()/ping(). 
 * @see DeliveryConnectionsHandler
 * @author xmlBlaster@marcelruff.info
 */
public final class CbDeliveryConnectionsHandler extends DeliveryConnectionsHandler
{
   public final String ME;
   
   /**
    * @param deliveryManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public CbDeliveryConnectionsHandler(Global glob, DeliveryManager deliveryManager, AddressBase[] addrArr) throws XmlBlasterException {
      super(glob, deliveryManager, addrArr);
      this.ME = "CbDeliveryConnectionsHandler-" + deliveryManager.getQueue().getStorageId();
   }

   /**
    * @return an instance of CbDeliveryConnection
    */
   public DeliveryConnection createDeliveryConnection(AddressBase address) throws XmlBlasterException {
      CbDeliveryConnection c = new CbDeliveryConnection(glob, this, address);
      c.initialize();
      return c;
   }

   /**
    * If no connection is available but the message is for example save queued,
    * we can generate here valid return objects
    * @param state e.g. Constants.STATE_OK
    */
   public Object createFakedReturnObjects(MsgQueueEntry[] entries, String state, String stateInfo) {
      Object[] returnQos = new Object[entries.length];
      for (int ii=0; ii<entries.length; ii++) {
         StatusQosData statRetQos = new StatusQosData(glob);
         statRetQos.setStateInfo(stateInfo);
         statRetQos.setState(state);
         if (MethodName.UPDATE == entries[ii].getMethodName()) {
            returnQos[ii] = new UpdateReturnQosServer(glob, statRetQos);
         }
         else {
            log.error(ME, "Internal problem, MsgQueueEntry '" + entries[ii].getEmbeddedType() + "' not expected here");
            returnQos[ii] = null;
         }
      }
      return returnQos;
   }
}
