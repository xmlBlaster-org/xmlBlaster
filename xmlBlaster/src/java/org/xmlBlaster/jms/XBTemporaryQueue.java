/*------------------------------------------------------------------------------
Name:      XBTemporaryQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.TemporaryQueue;

/**
 * XBTemporaryQueue
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTemporaryQueue extends XBQueue implements TemporaryQueue {

   XBTemporaryQueue() {
      super();
   }

   /* (non-Javadoc)
    * @see javax.jms.TemporaryQueue#delete()
    */
   public void delete() throws JMSException {
      // TODO Auto-generated method stub

   }
}
