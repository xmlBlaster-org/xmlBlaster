/*------------------------------------------------------------------------------
Name:      XBServerSessionPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.ServerSession;
import javax.jms.ServerSessionPool;

/**
 * XBServerSessionPool
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class XBServerSessionPool implements ServerSessionPool {

   private XBConnection connection;
   private ServerSession serverSession;
   
   public XBServerSessionPool(XBConnection connection) {
      this.connection = connection;
   }
   
   /**
    * @see javax.jms.ServerSessionPool#getServerSession()
    */
   public synchronized ServerSession getServerSession() throws JMSException {
      // TODO Implement the real pooling.
      if (this.serverSession == null)
         this.serverSession = new XBServerSession(this.connection);
      return this.serverSession;
   }

}
