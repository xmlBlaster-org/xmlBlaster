/*------------------------------------------------------------------------------
Name:      XBQueueBrowser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.Enumeration;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueBrowser;

/**
 * XBQueueBrowser
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBQueueBrowser implements QueueBrowser {

   private Queue queue;
   private String msgSelector;

   protected XBQueueBrowser(Queue queue, String msgSelector) {
      this.queue = queue;
      this.msgSelector = msgSelector;
   }
   
   /* (non-Javadoc)
    * @see javax.jms.QueueBrowser#getQueue()
    */
   public Queue getQueue() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueBrowser#getMessageSelector()
    */
   public String getMessageSelector() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueBrowser#getEnumeration()
    */
   public Enumeration getEnumeration() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueBrowser#close()
    */
   public void close() throws JMSException {
      // TODO Auto-generated method stub

   }

   public static void main(String[] args) {
   }
}
