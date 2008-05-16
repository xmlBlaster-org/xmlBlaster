/*------------------------------------------------------------------------------
Name:      ClientDispatchConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.dispatch;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;

/**
 * Holding all necessary infos to establish a remote
 * connection and invoke publish(), subscribe(), connect() etc.
 * @see DispatchConnectionsHandler
 * @author xmlBlaster@marcelruff.info
 */
public final class ClientDispatchConnectionsHandler extends DispatchConnectionsHandler
{
   private static Logger log = Logger.getLogger(ClientDispatchConnectionsHandler.class.getName());
   public final String ME;
   
   /**
    * @param dispatchManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public ClientDispatchConnectionsHandler(Global glob, DispatchManager dispatchManager) throws XmlBlasterException {
      super(glob, dispatchManager);
      this.ME = "ClientDispatchConnectionsHandler-" + dispatchManager.getQueue().getStorageId();
   }
   
   public boolean isUserThread() {
      return !DispatchConnection.PING_THREAD_NAME.equals(Thread.currentThread().getName());
   }

   /**
    * @return a new ClientDispatchConnection instance which has its plugin loaded
    */
   public DispatchConnection createDispatchConnection(AddressBase address) throws XmlBlasterException {
      ClientDispatchConnection c = new ClientDispatchConnection(glob, this, address);
      c.loadPlugin();
      return c;
   }

   /**
    * If no connection is available but the message is for example save queued,
    * we can generate here valid return objects
    * @param state e.g. Constants.STATE_OK
    */
   public void createFakedReturnObjects(I_QueueEntry[] entries, String state, String stateInfo) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering createFakedReturnObjects() for " + entries.length + " entries");

      for (int ii=0; ii<entries.length; ii++) {
         MsgQueueEntry msgQueueEntry = (MsgQueueEntry)entries[ii];
         if (!msgQueueEntry.wantReturnObj())
            continue;
         StatusQosData statRetQos = new StatusQosData(glob, MethodName.UNKNOWN);
         statRetQos.setStateInfo(stateInfo);
         statRetQos.setState(state);
         if (log.isLoggable(Level.FINE)) log.fine("Creating faked return for '" + msgQueueEntry.getMethodName() + "' invocation");

         if (MethodName.PUBLISH_ONEWAY == msgQueueEntry.getMethodName()) {
            MsgQueuePublishEntry entry = (MsgQueuePublishEntry)msgQueueEntry;
            entry.setReturnObj(null);
         }

         else if (MethodName.PUBLISH == msgQueueEntry.getMethodName()) {
            MsgQueuePublishEntry entry = (MsgQueuePublishEntry)msgQueueEntry;
            if (!entry.getMsgKeyData().hasOid()) {
               entry.getMsgKeyData().setOid(entry.getMsgKeyData().generateOid(entry.getSender().getRelativeName()));
            }
            statRetQos.setKeyOid(entry.getKeyOid());
            PublishReturnQos publishReturnQos = new PublishReturnQos(glob, statRetQos);
            //TODO: How to fake the RcvTimestamp -> it must be unique for an OID in the server
            //publishReturnQos.getData().setRcvTimestamp(new org.xmlBlaster.util.RcvTimestamp());
            entry.setReturnObj(publishReturnQos);
         }

         else if (MethodName.SUBSCRIBE == msgQueueEntry.getMethodName()) {
            MsgQueueSubscribeEntry entry = (MsgQueueSubscribeEntry)msgQueueEntry;
            if (!entry.getSubscribeQosData().hasSubscriptionId()) {
               entry.getSubscribeQosData().generateSubscriptionId(glob.getXmlBlasterAccess().getSessionName(), entry.getSubscribeKeyData());
               //String subscriptionId = QueryKeyData.generateSubscriptionId(dispatchManager.getQueue().getStorageId().getPostfix());
               //entry.getSubscribeQosData().setSubscriptionId(subscriptionId);
            }
            statRetQos.setSubscriptionId(entry.getSubscribeQosData().getSubscriptionId());
            SubscribeReturnQos subscribeReturnQos = new SubscribeReturnQos(glob, statRetQos, true);
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
            ConnectReturnQos connectReturnQos = new ConnectReturnQos(glob, ((MsgQueueConnectEntry)msgQueueEntry).getConnectQosData());
            if (!connectReturnQos.getSessionName().isPubSessionIdUser()) {
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
                  "Can't find an xmlBlaster server. Try to provide the server host/port as described in " +
                  "http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.configuration.html " +
                  "or provide a public session ID to support polling for xmlBlaster without an initial connection. " +
                  "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
            }
            msgQueueEntry.setReturnObj(connectReturnQos);
         }

         else if (MethodName.DISCONNECT == msgQueueEntry.getMethodName()) {
            if (log.isLoggable(Level.FINE)) log.fine("disconnect returns void, nothing to do");
         }

         else if (MethodName.GET == msgQueueEntry.getMethodName()) {
            MsgQueueGetEntry entry = (MsgQueueGetEntry)msgQueueEntry;
            throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
               "Synchronous GET on oid='" + entry.getGetKey().getOid() + "' is not possible in offline/polling mode. " +
               "See 'http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html' for more details.");
         }

         else {
            log.severe("Internal problem, MsgQueueEntry '" + msgQueueEntry.getEmbeddedType() + "' not expected here");
         }
      }
   }

   public ArrayList filterDistributorEntries(ArrayList entries, Throwable ex) {
      return entries;
   }
   
}

