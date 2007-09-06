/*------------------------------------------------------------------------------
Name:      XBServerSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.ServerSession;
import javax.jms.Session;

/**
 * XBServerSession
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class XBServerSession implements ServerSession {
 
   private Session session;
   private XBConnection connection;
   private boolean started;
   
   public XBServerSession(XBConnection connection) {
      this.connection = connection;
   }
   
   /**
    * @see javax.jms.ServerSession#getSession()
    */
   public synchronized Session getSession() throws JMSException {
      if (!this.started)
         throw new IllegalStateException("This server session has not been started yet, can not get the session");
      if (this.session == null) {
         boolean transacted = false;
         int ackMode = Session.AUTO_ACKNOWLEDGE;
         this.session = new XBSession(this.connection, ackMode, transacted);
      }
      return this.session;
   }

   /**
    * @see javax.jms.ServerSession#start()
    */
   public synchronized void start() throws JMSException {
      this.started = true;
   }

}
