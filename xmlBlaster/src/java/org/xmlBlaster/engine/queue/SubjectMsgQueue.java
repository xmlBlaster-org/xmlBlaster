/*------------------------------------------------------------------------------
Name:      SubjectMsgQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: SubjectMsgQueue.java,v 1.7 2002/05/30 16:31:30 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queue;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.authentication.SubjectInfo;


/**
 * Queueing messages specific to a loginName to send back to a client. 
 */
public class SubjectMsgQueue extends MsgQueue
{
   private String ME = "SubjectMsgQueue";
   private final SubjectInfo subjectInfo;

   /**
    * @param queueName "subject:joe"
    * @param prop The behavior of the queue
    */
   public SubjectMsgQueue(SubjectInfo subjectInfo, String queueName, CbQueueProperty prop, Global glob) throws XmlBlasterException {
      super(queueName, prop, glob);
      this.subjectInfo = subjectInfo;
   }

   /**
    * Allows to overwrite queue property, will be only written if prop!= null
    */
   public final void setProperty(CbQueueProperty  prop) throws XmlBlasterException
   {
      super.setProperty(prop);
      if (prop != null) {
         if (this.property.getCallbackAddresses().length > 0)
            log.error(ME, "Using for subject " + this.property.getCallbackAddresses().length + " callback addresses is not tested");
      }
   }

   public final SubjectInfo getSubjectInfo()
   {
      return this.subjectInfo;
   }
}

