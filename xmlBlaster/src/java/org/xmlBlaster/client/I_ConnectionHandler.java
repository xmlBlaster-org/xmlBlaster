/*------------------------------------------------------------------------------
Name:      I_ConnectionHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;


/**
 * Access the remote connection object to access information or manipulate it. 
 * <p>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ConnectionHandler
{
   /**
    * Access the environment settings of this connection. 
    * @return The global handle (like a stack with local variables for this connection)
    */
   public Global getGlobal();

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster. 
    * <p>
    * This method blocks until all entries in the queue have been sent.
    * </p>
    * @return The number of sent messages. <br />
    *         If the queue is empty or NULL, then 0 is returned.<br />
    *         If the state is in POLLING or DEAD, then -1 is returned.<br />
    */
   long flushQueue() throws XmlBlasterException;

   /**
    * @return The queue used to store tailback messages. 
    */
   I_Queue getQueue();

   /**
    * You can activate this mode by invoking initFailsafe
    * in XmlBlasterAccess.
    * @return Returns true if the connection is in failsafe mode
    */
   boolean isFailSafe();

   /**
    * @return true if the connection to xmlBlaster is operational
    */
   boolean isAlive();

   /**
    * @return true if we are polling for the server
    */
   boolean isPolling();

   /**
    * @return true if we have definitely lost the connection to xmlBlaster and gave up
    */
   boolean isDead();

   /**
    * Access the returned QoS of a connect() call. 
    */
   ConnectReturnQos getConnectReturnQos();

   /**
    * Access the current ConnectQos
    */
   ConnectQos getConnectQos();
}

