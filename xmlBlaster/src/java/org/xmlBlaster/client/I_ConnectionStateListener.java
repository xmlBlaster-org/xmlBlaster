/*------------------------------------------------------------------------------
Name:      I_ConnectionStateListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * Callback the client from XmlBlasterAccess if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ConnectionStateListener
{
   /**
    * This is the callback method invoked from XmlBlasterAccess
    * notifying the client that a connection has been established and that its status is now ALIVE.
    *
    * <p>
    * Note that this method is invoked also when the connection has been 
    * established the first time.
    * </p>
    *
    * <p>
    * You can erase all entries of the queue manually or add others before you return and in
    * this way control the behavior.
    * </p>
    *
    * <p>
    * This method is invoked by the login polling thread from I_XmlBlasterAccess.
    * </p>
    * @param oldState The previous state of the connection.
    * @param connectionHandler An interface which allows you to control the queue and the connection
    */
   void reachedAlive(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler);

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client that the connection state has changed to POLLING.
    *
    * @param oldState The previous state of the connection.
    * @param connectionHandler An interface which allows you to control the queue and the connection
    */
   void reachedPolling(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler);

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client that the connection was lost (i.e. when the state of the
    * connection has gone to DEAD).
    *
    * @param oldState The previous state of the connection.
    * @param connectionHandler An interface which allows you to control the queue and the connection
    */
   void reachedDead(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler);
}

