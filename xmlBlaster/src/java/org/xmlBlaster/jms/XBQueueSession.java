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

import org.xmlBlaster.client.qos.ConnectQos;

/**
 * XBQueueSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBQueueSession extends XBSession implements QueueSession {

   private final static String ME = "XBQueueSession";

   XBQueueSession(ConnectQos connectQos, int ackMode, boolean transacted) {
      super(connectQos, ackMode, transacted);
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createReceiver(javax.jms.Queue)
    */
   public QueueReceiver createReceiver(Queue arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createReceiver(javax.jms.Queue, java.lang.String)
    */
   public QueueReceiver createReceiver(Queue arg0, String arg1)
      throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createSender(javax.jms.Queue)
    */
   public QueueSender createSender(Queue arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

}
