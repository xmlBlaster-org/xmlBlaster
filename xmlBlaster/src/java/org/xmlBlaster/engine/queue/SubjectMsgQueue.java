/*------------------------------------------------------------------------------
Name:      SubjectMsgQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: SubjectMsgQueue.java,v 1.5 2002/05/03 13:46:09 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queue;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.callback.CbInfo;
import org.xmlBlaster.engine.callback.CbWorkerPool;


/**
 * Queueing messages specific to a loginName to send back to a client. 
 */
public class SubjectMsgQueue extends MsgQueue
{
   private String ME = "SubjectMsgQueue";

   /**
    * @param queueName "subject:joe"
    * @param prop The behavior of the queue
    */
   public SubjectMsgQueue(String queueName, CbQueueProperty prop, Global glob) throws XmlBlasterException {
      super(queueName, prop, glob);
   }

   /**
    * Allows to overwrite queue property, will be only written if prop!= null
    */
   public final void setProperty(CbQueueProperty  prop) throws XmlBlasterException
   {
      if (prop != null) {
         this.property = prop;
         CallbackAddress[] addr = this.property.getCallbackAddresses();
         if (addr.length > 0)
            log.error(ME, "Using for subject " + addr.length + " callback addresses");
         cbInfo = new CbInfo(glob, addr);
      }
   }

   /**
    * Set new callback addresses, typically after a session login/logout
    */
   public final void setCallbackAddresses(CallbackAddress[] addr) throws XmlBlasterException
   {
      if (this.property == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "No CbQueueProperty - internal error");
      }
      this.property.setCallbackAddresses(addr);
      cbInfo = new CbInfo(glob, addr);
   }
}

