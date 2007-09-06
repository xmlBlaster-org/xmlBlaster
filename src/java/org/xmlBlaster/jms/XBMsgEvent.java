/*------------------------------------------------------------------------------
Name:      XBMsgEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import javax.jms.Message;
import javax.jms.MessageListener;


/**
 * XBMsgEvent is a placeholder used to be passed from the update method of a specific
 * MessageConsumer to the thread of control for asynchronous calls, which
 * is the thread of the associated session (its run() method).
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
final class XBMsgEvent {

   private final MessageListener msgListener;
   private final Message msg;
   
   public XBMsgEvent(MessageListener msgListener, Message msg) {
      this.msgListener = msgListener;
      this.msg = msg;
   }
   
   final MessageListener getListener() {
      return this.msgListener;
   }
   
   final Message getMessage() {
      return this.msg;
   }
   
}
