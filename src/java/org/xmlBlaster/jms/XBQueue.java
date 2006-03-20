/*------------------------------------------------------------------------------
Name:      XBQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Queue;

/**
 * XBQueue
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBQueue implements Queue {


   private String name;
   
   XBQueue() {
   }

   XBQueue(String name) {
      this.name = name;
   }

   /* (non-Javadoc)
    * @see javax.jms.Queue#getQueueName()
    */
   public String getQueueName() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   public static void main(String[] args) {
   }
}
