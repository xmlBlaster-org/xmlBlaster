/*------------------------------------------------------------------------------
Name:      CbDispatchConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.dispatch;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;

/**
 * Holding all necessary infos to establish a remote
 * connection and invoke update()/updateOneway()/ping(). 
 * @see DispatchConnectionsHandler
 * @author xmlBlaster@marcelruff.info
 */
public final class CbDispatchConnectionsHandler extends DispatchConnectionsHandler
{
   private static Logger log = Logger.getLogger(CbDispatchConnectionsHandler.class.getName());
   
   public final String ME;
   
   /**
    * @param dispatchManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public CbDispatchConnectionsHandler(Global glob, DispatchManager dispatchManager) throws XmlBlasterException {
      super(glob, dispatchManager);
      this.ME = "CbDispatchConnectionsHandler-" + dispatchManager.getQueue().getStorageId();
   }

   /**
    * @return a new CbDispatchConnection instance which has its plugin loaded
    */
   public DispatchConnection createDispatchConnection(AddressBase address) throws XmlBlasterException {
      CbDispatchConnection c = new CbDispatchConnection(glob, this, address);
      if (log.isLoggable(Level.FINE)) this.log.fine("createDispatchConnection for address='" + address.toXml() + "'");
      c.loadPlugin();
      return c;
   }

   /**
    * If no connection is available but the message is for example save queued,
    * we can generate here valid return objects
    * @param state e.g. Constants.STATE_OK
    */
   public void createFakedReturnObjects(I_QueueEntry[] entries, String state, String stateInfo) {
      for (int ii=0; ii<entries.length; ii++) {
         MsgQueueEntry msgQueueEntry = (MsgQueueEntry)entries[ii];
         if (!msgQueueEntry.wantReturnObj())
            continue;
         StatusQosData statRetQos = new StatusQosData(glob, MethodName.UPDATE);
         statRetQos.setStateInfo(stateInfo);
         statRetQos.setState(state);
         // TODO check this: it is probably wrong since here comes UPDATE_REF and not UPDATE (Michele 2003-11-05)
         // if (MethodName.UPDATE == msgQueueEntry.getMethodName()) {
         // if ( MethodName.UPDATE.getMethodName().equalsIgnoreCase(msgQueueEntry.getEmbeddedType())) {
         // !!! HACK !!!
         if ( "update_ref".equalsIgnoreCase(msgQueueEntry.getEmbeddedType())) {
            UpdateReturnQosServer ret = new UpdateReturnQosServer(glob, statRetQos);
            msgQueueEntry.setReturnObj(ret);
         }
         else {
            log.severe("Internal problem, MsgQueueEntry '" + msgQueueEntry.getEmbeddedType() + "' not expected here");
         }
      }
   }
   
   /**
    * Scans through the entries array for such messages which want an async
    * notification and sends such a notification.
    * @param entries
    * @return The MsgQueueEntry objects (as an ArrayList) which did not
    * want such an async notification. This is needed to allow the core process
    * such messages the normal way.
    */
   public ArrayList filterDistributorEntries(ArrayList entries, Throwable ex) {
      ArrayList entriesWithNoDistributor = new ArrayList();
      for (int i=0; i < entries.size(); i++) {
         Object obj = entries.get(i); 
         if (!(obj instanceof MsgQueueUpdateEntry)) return entries;
         MsgQueueUpdateEntry entry = (MsgQueueUpdateEntry)obj;
         MsgUnitWrapper wrapper = entry.getMsgUnitWrapper();
         boolean hasMsgDistributor = wrapper.getServerScope().getTopicAccessor().hasMsgDistributorPluginDirtyRead(wrapper.getKeyOid());
         
         if (hasMsgDistributor) {
            if (ex != null) { // in this case it is possible that retObj is not set yet
               UpdateReturnQosServer retQos = (UpdateReturnQosServer)entry.getReturnObj();               
               try {
                  if (retQos == null) {
                     retQos = new UpdateReturnQosServer(this.glob, "<qos/>");
                     entry.setReturnObj(retQos);
                  }    
                  retQos.setException(ex);
               }
               catch (XmlBlasterException ee) {
                  log.severe("filterDistributorEntries: " + ee.getMessage());
               }
            } 
            // msgDistributor.responseEvent((String)wrapper.getMsgQosData().getClientProperties().get("asyncAckCorrId"), entry.getReturnObj());
         }
         else {
            entriesWithNoDistributor.add(entry);
         }
      }
      return entriesWithNoDistributor;
   }

   
}
