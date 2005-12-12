/*------------------------------------------------------------------------------
Name:      ReplManagerPluginMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;


public interface ReplManagerPluginMBean {
   
   /**
    * Never returns null. It returns a list of keys identifying the ongoing replications.
    * @return
    */
   String[] getReplications();
   
   /**
    * Never returns null. It returns a list of keys identifying the slaves using the replication 
    * manager.
    * @return
    */
   String[] getSlaves();

   /**
    * Intiates the replication for the given slave.
    * 
    * @param slaveSessionName
    * @param replPrefix this is the same as specified in the configuration as 'replication.prefix' and it 
    * identifies  a replication source.
    * @throws Exception
    */
   String initiateReplication(String slaveSessionName, String replPrefix) throws Exception;
   
   /**
    * Recreates the triggers for the given replication source.
    * 
    * @param replPrefix this is the same as specified in the configuration as 'replication.prefix' and it 
    * identifies  a replication source.
    * @throws Exception
    */
   String recreateTriggers(String replPrefix) throws Exception;

   /**
    * Executes either a query or an update. Responses will come back asynchronously.
    * 
    * @param repl The replication to which to send the request.
    * @param sql The sql statement to perform (can either be a query or an update).
    * @param highPrio if true, then the message is sent as a high priority message, i.e.
    * it will bypass other messages of the queue with the exception of ongoing initial
    * updates.
    * @throws Exception if either the repl or sql was null.
    */
   void broadcastSql(String repl, String sql, boolean highPrio) throws Exception;
   
}
