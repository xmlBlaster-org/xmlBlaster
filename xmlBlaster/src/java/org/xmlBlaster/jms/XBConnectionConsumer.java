/*------------------------------------------------------------------------------
Name:      XBConnectionConsumer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;

/**
 * XBConnectionConsumer
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnectionConsumer implements ConnectionConsumer {

   /* (non-Javadoc)
    * @see javax.jms.ConnectionConsumer#close()
    */
   public void close() throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.ConnectionConsumer#getServerSessionPool()
    */
   public ServerSessionPool getServerSessionPool() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   public static void main(String[] args) {
   }
}
