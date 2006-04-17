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

import org.xmlBlaster.util.def.ErrorCode;

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
   
   /**
    * @see javax.jms.QueueBrowser#getQueue()
    */
   public Queue getQueue() throws JMSException {
      return this.queue;
   }

   /**
    * @see javax.jms.QueueBrowser#getMessageSelector()
    */
   public String getMessageSelector() throws JMSException {
      return this.msgSelector;
   }

   /**
    * TODO implement
    * @see javax.jms.QueueBrowser#getEnumeration()
    */
   public Enumeration getEnumeration() throws JMSException {
      throw new XBException(ErrorCode.INTERNAL_NOTIMPLEMENTED.getErrorCode(), "method XBQueueBrowser.getEnumeration() not implemented");
   }

   /**
    * TODO implement
    * @see javax.jms.QueueBrowser#close()
    */
   public void close() throws JMSException {
      throw new XBException(ErrorCode.INTERNAL_NOTIMPLEMENTED.getErrorCode(), "method XBQueueBrowser.close() not implemented");
   }

}
