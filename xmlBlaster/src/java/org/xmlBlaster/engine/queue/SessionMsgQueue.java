/*------------------------------------------------------------------------------
Name:      SessionMsgQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: SessionMsgQueue.java,v 1.3 2002/05/03 13:46:09 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queue;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.Global;


/**
 * Queueing messages to send back to a client. 
 */
public class SessionMsgQueue extends MsgQueue
{
   private String ME = "SessionMsgQueue";
   private SessionInfo sessionInfo;

   /**
    * @param queueName "session:c0xfrt"
    * @param prop The behavior of the queue
    */
   public SessionMsgQueue(String queueName, SessionInfo sessionInfo, CbQueueProperty prop, Global glob) throws XmlBlasterException {
      super(queueName, prop, glob);
      this.sessionInfo = sessionInfo;
   }

   public String getSessionId()
   {
      return this.sessionInfo.getSessionId();
   }

   public SessionInfo getSessionInfo()
   {
      return this.sessionInfo;
   }
}

