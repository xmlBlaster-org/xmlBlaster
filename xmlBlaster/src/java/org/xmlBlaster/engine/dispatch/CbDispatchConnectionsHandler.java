/*------------------------------------------------------------------------------
Name:      CbDispatchConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.dispatch;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;

/**
 * Holding all necessary infos to establish a remote
 * connection and invoke update()/updateOneway()/ping(). 
 * @see DispatchConnectionsHandler
 * @author xmlBlaster@marcelruff.info
 */
public final class CbDispatchConnectionsHandler extends DispatchConnectionsHandler
{
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
         if (MethodName.UPDATE == msgQueueEntry.getMethodName()) {
            UpdateReturnQosServer ret = new UpdateReturnQosServer(glob, statRetQos);
            msgQueueEntry.setReturnObj(ret);
         }
         else {
            log.error(ME, "Internal problem, MsgQueueEntry '" + msgQueueEntry.getEmbeddedType() + "' not expected here");
         }
      }
   }
}
