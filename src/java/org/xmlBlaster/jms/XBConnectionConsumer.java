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

   private boolean closed;
   private XBServerSessionPool serverSessionPool;
   
   public XBConnectionConsumer(XBConnection connection) {
      this.serverSessionPool = new XBServerSessionPool(connection);
   }
   
   /**
    * @see javax.jms.ConnectionConsumer#close()
    */
   public synchronized void close() throws JMSException {
      this.closed = true;
   }

   /**
    * @see javax.jms.ConnectionConsumer#getServerSessionPool()
    */
   public ServerSessionPool getServerSessionPool() throws JMSException {
      if (this.closed)
         throw new IllegalStateException("Can not create a server session pool since this connection consumer has been closed");
      return this.serverSessionPool;
   }

}
