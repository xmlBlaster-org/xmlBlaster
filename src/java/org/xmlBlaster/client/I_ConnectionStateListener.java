/*------------------------------------------------------------------------------
Name:      I_ConnectionStateListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * Callback the client from XmlBlasterAccess if the connection to xmlBlaster is lost
 * or was reestablished (failsafe mode).
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
    * established the first time. In this case the connection is fully operational
    * but your connect() call has not yet returned. You can access the
    * returned connect QoS in this case with <i>connect.getConnectReturnQos()</i>.
    * </p>
    *
    * <p>
    * You can erase all entries of the queue manually or add others before you return and in
    * this way control the behavior.
    * During you have control in <i>reachedAlive()</i> the client side
    * queue is blocked and does not accept publish or request messages from other threads.
    * So you can do peacefully your work (your thread is allowed to modify the queue exclusively).
    * </p>
    *
    * <p>
    * If you send messages during this method invocation they are queued only and
    * are sent as soon as this method returns.
    * </p>
    *
    * <p>
    * This method is invoked by the login polling thread from I_XmlBlasterAccess in which case it is a
    * physical Alive, and by the connect method on successful login in which case it is a logical Alive.
    * 
    * </p>
    * @param oldState The previous state of the connection.
    * @param connectionHandler An interface which allows you to control the queue and the connection
    */
   void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection);

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client that the connection state has changed to POLLING.
    *
    * @param oldState The previous state of the connection.
    * @param connectionHandler An interface which allows you to control the queue and the connection
    */
   void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection);

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client that the connection was lost (i.e. when the state of the
    * connection has gone to DEAD).
    *
    * @param oldState The previous state of the connection.
    * @param connectionHandler An interface which allows you to control the queue and the connection
    */
   void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection);
}

