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
 * Access the connection handler to access connection status information or manipulate queued messages. 
 * <p>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ConnectionHandler
{
   /**
    * @return The queue used to store tailback messages. 
    */
   I_Queue getQueue();

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
}

