/*------------------------------------------------------------------------------
Name:      SubjectMsgQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: SubjectMsgQueue.java,v 1.3 2002/03/17 07:22:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queue;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.QueueProperty;
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
   public SubjectMsgQueue(String queueName, QueueProperty prop, Global glob) throws XmlBlasterException {
      super(queueName, prop, glob);
   }

   /**
    * Allows to overwrite queue property, will be only written if prop!= null
    */
   public final void setProperty(QueueProperty  prop) throws XmlBlasterException
   {
      if (prop != null) {
         this.property = prop;
         CallbackAddress[] addr = this.property.getCallbackAddresses();
         log.info(ME, "Using for subject " + addr.length + " callback addresses");
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
         throw new XmlBlasterException(ME, "No QueueProperty - internal error");
      }
      this.property.setCallbackAddresses(addr);
      cbInfo = new CbInfo(glob, addr);
   }
}

