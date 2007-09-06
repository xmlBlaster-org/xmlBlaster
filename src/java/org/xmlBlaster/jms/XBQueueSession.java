/*
 * Created on Sep 29, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;

/**
 * XBQueueSession
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBQueueSession extends XBSession implements QueueSession {

   XBQueueSession(XBConnection connection, int ackMode, boolean transacted) {
      super(connection, ackMode, transacted);
   }

   /**
    * @see javax.jms.QueueSession#createReceiver(javax.jms.Queue)
    */
   public QueueReceiver createReceiver(Queue queue) throws JMSException {
      return createReceiver(queue, null);
   }

   /**
    * @see javax.jms.QueueSession#createReceiver(javax.jms.Queue, java.lang.String)
    */
   public QueueReceiver createReceiver(Queue queue, String msgSelector)
      throws JMSException {
      boolean noLocal = false;
      // the msgSelector is the MIME Plugin
      return new XBQueueReceiver(this, queue, msgSelector, noLocal);
   }

   /**
    * @see javax.jms.QueueSession#createSender(javax.jms.Queue)
    */
   public QueueSender createSender(Queue queue) throws JMSException {
      return new XBQueueSender(this, queue);
   }

}
