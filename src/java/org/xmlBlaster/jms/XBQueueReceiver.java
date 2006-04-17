/*------------------------------------------------------------------------------
Name:      XBQueueReceiver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;

/**
 * XBQueueReceiver
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class XBQueueReceiver extends XBMessageConsumer implements QueueReceiver {

   XBQueueReceiver(XBQueueSession session, Queue destination, String msgSelector, boolean noLocal) throws JMSException {
      super(session, destination, msgSelector, noLocal);
   }

   /**
    * @see javax.jms.QueueReceiver#getQueue()
    */
   public Queue getQueue() throws JMSException {
      return (Queue)this.destination;
   }

}
