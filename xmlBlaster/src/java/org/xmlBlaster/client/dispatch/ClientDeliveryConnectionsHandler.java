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
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;

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
   public void createFakedReturnObjects(I_QueueEntry[] entries, String state, String stateInfo) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering createFakedReturnObjects() for " + entries.length + " entries");

      for (int ii=0; ii<entries.length; ii++) {
         MsgQueueEntry msgQueueEntry = (MsgQueueEntry)entries[ii];
         if (!msgQueueEntry.wantReturnObj())
            continue;
         StatusQosData statRetQos = new StatusQosData(glob);
         statRetQos.setStateInfo(stateInfo);
         statRetQos.setState(state);
         if (log.TRACE) log.trace(ME, "Creating faked return for '" + msgQueueEntry.getMethodName() + "' invocation");
         if (MethodName.PUBLISH == msgQueueEntry.getMethodName()) {
            MsgQueuePublishEntry entry = (MsgQueuePublishEntry)msgQueueEntry;
            if (!entry.getMsgKeyData().hasOid()) {
               entry.getMsgKeyData().setOid(entry.getMsgKeyData().generateOid(entry.getSender().getRelativeName()));
            }
            statRetQos.setKeyOid(entry.getKeyOid());
            PublishReturnQos publishReturnQos = new PublishReturnQos(glob, statRetQos);
            //TODO: How to fake the RcvTimestamp -> it must be unique for an OID in the server
            //publishReturnQos.getData().setRcvTimestamp(new org.xmlBlaster.util.RcvTimestamp());
            entry.setReturnObj(new PublishReturnQos(glob, statRetQos));
         }

         else if (MethodName.SUBSCRIBE == msgQueueEntry.getMethodName()) {
            MsgQueueSubscribeEntry entry = (MsgQueueSubscribeEntry)msgQueueEntry;
            if (entry.getSubscribeQos().getData().getSubscriptionId() == null) {
               String subscriptionId = QueryKeyData.generateSubscriptionId(deliveryManager.getQueue().getStorageId().getPostfix());
               entry.getSubscribeQos().setSubscriptionId(subscriptionId);
            }
            statRetQos.setSubscriptionId(entry.getSubscribeQos().getData().getSubscriptionId());
            SubscribeReturnQos subscribeReturnQos = new SubscribeReturnQos(glob, statRetQos);
            entry.setReturnObj(subscribeReturnQos);
         }

         else if (MethodName.UNSUBSCRIBE == msgQueueEntry.getMethodName()) {
            MsgQueueUnSubscribeEntry entry = (MsgQueueUnSubscribeEntry)msgQueueEntry;
            String id = entry.getUnSubscribeKey().getOid();
            if (id != null && id.startsWith(Constants.SUBSCRIPTIONID_PREFIX)) {
               statRetQos.setSubscriptionId(id);
               UnSubscribeReturnQos[] unSubscribeReturnQosArr = new UnSubscribeReturnQos[] { new UnSubscribeReturnQos(glob, statRetQos) };
               entry.setReturnObj(unSubscribeReturnQosArr);
            }
            else {
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
                  "UnSubscribe on oid='" + id + "' is not possible in offline/polling mode without an exact subscription ID given. " +
                  "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
            }
         }

         else if (MethodName.ERASE == msgQueueEntry.getMethodName()) {
            MsgQueueEraseEntry entry = (MsgQueueEraseEntry)msgQueueEntry;
            if (entry.getEraseKey().isExact()) {
               statRetQos.setKeyOid(entry.getEraseKey().getOid());
               EraseReturnQos[] eraseReturnQosArr = new EraseReturnQos[] { new EraseReturnQos(glob, statRetQos) };
               entry.setReturnObj(eraseReturnQosArr);
            }
            else {
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
                  "Erase on oid='" + entry.getEraseKey().getOid() + "' is not possible in offline/polling mode without an exact topic oid given. " +
                  "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
            }
         }

         else if (MethodName.CONNECT == msgQueueEntry.getMethodName()) {
            ConnectReturnQos connectReturnQos = new ConnectReturnQos(glob, ((MsgQueueConnectEntry)msgQueueEntry).getConnectQos().getData());
            if (!connectReturnQos.getSessionName().isPubSessionIdUser()) {
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
                  "Please provide a public session ID to support polling for xmlBlaster without an initial connection. " +
                  "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
            }
            msgQueueEntry.setReturnObj(connectReturnQos);
         }

         else if (MethodName.DISCONNECT == msgQueueEntry.getMethodName()) {
            if (log.TRACE) log.trace(ME, "disconnect returns void, nothing to do");
         }

         else if (MethodName.GET == msgQueueEntry.getMethodName()) {
            MsgQueueGetEntry entry = (MsgQueueGetEntry)msgQueueEntry;
            throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
               "Synchronous GET on oid='" + entry.getGetKey().getOid() + "' is not possible in offline/polling mode. " +
               "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
         }

         else {
            log.error(ME, "Internal problem, MsgQueueEntry '" + msgQueueEntry.getEmbeddedType() + "' not expected here");
         }
      }
   }
}

