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
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBTemporaryQueue extends XBDestination implements TemporaryQueue {

   /**
    * 
    */
   private static final long serialVersionUID = 1L;

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
