/*------------------------------------------------------------------------------
Name:      ClientDeliveryConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.DeliveryConnection;
import org.xmlBlaster.util.dispatch.DeliveryConnectionsHandler;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.client.qos.PublishReturnQos;

/**
 * Holding all necessary infos to establish a remote
 * connection and invoke publish(), subscribe(), connect() etc.
 * @see DeliveryConnectionsHandler
 * @author xmlBlaster@marcelruff.info
 */
public final class ClientDeliveryConnectionsHandler extends DeliveryConnectionsHandler
{
   public final String ME;
   
   /**
    * @param deliveryManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public ClientDeliveryConnectionsHandler(Global glob, DeliveryManager deliveryManager, AddressBase[] addrArr) throws XmlBlasterException {
      super(glob, deliveryManager, addrArr);
      this.ME = "ClientDeliveryConnectionsHandler-" + deliveryManager.getQueue().getStorageId();
   }

   /**
    * @return a new ClientDeliveryConnection instance
    */
   public DeliveryConnection createDeliveryConnection(AddressBase address) throws XmlBlasterException {
      ClientDeliveryConnection c = new ClientDeliveryConnection(glob, this, address);
      c.initialize();
      return c;
   }

   /**
    * If no connection is available but the message is for example save queued,
    * we can generate here valid return objects
    * @param state e.g. Constants.STATE_OK
    */
   public Object createFakedReturnObjects(MsgQueueEntry[] entries, String state, String stateInfo) throws XmlBlasterException {
      Object[] returnQos = new Object[entries.length];
      for (int ii=0; ii<entries.length; ii++) {
         StatusQosData statRetQos = new StatusQosData(glob);
         statRetQos.setStateInfo(stateInfo);
         statRetQos.setState(state);
         if (MethodName.PUBLISH == entries[ii].getMethodName()) {
            log.warn(ME, "PUBLISH how to handle?");
            // TODO: See XmlBlasterConnection.getAndReplaceOid
            //statRetQos.setKeyOid(oid);
            returnQos[ii] = new PublishReturnQos(glob, statRetQos);
         }
         else if (MethodName.CONNECT == entries[ii].getMethodName()) {
            ConnectReturnQos connectReturnQos = new ConnectReturnQos(glob, ((MsgQueueConnectEntry)entries[ii]).getConnectQos().getData());
            if (connectReturnQos.getSessionName() == null) {
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
                  "Please provide a public session ID to support polling for xmlBlaster without an initial connection. " +
                  "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
            }
            return connectReturnQos;
         }
         else {
            log.error(ME, "Internal problem, MsgQueueEntry '" + entries[ii].getEmbeddedType() + "' not expected here");
            returnQos[ii] = null;
         }
      }
      return returnQos;
   }
}

