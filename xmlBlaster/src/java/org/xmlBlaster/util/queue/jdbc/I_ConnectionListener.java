/*------------------------------------------------------------------------------
Name:      I_ConnectionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue.jdbc;


/**
 * Interface you need to implement to receive notifications from the 
 * JdbcConnectionPool that the pool has successfully reconnected
 *
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public interface I_ConnectionListener
{

  /**
   * Notification that a disconnection from the DB has occured.
   */
   public void disconnected();


  /**
   * Notification that a disconnection from the DB has occured.
   */
   public void reconnected();
}
