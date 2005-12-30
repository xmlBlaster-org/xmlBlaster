/*------------------------------------------------------------------------------
Name:      ReplManagerPluginMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;


public interface ReplManagerPluginMBean {
   
   /**
    * Never returns null. It returns a comma separated list of keys identifying the ongoing replications.
    * @return
    */
   String getReplications();
   
   /**
    * Never returns null. It returns a comma separated list of keys identifying the slaves using the replication 
    * manager.
    * @return
    */
   String getSlaves();

   /**
    * Intiates the replication for the given slave.
    * TODO Specify that the replicationKey (dbmasterid) must be short and DB conform.
    * Usually called by Human being via JMX Console.
    * 
    * The cascaded replication is the replication which will be automatically started once the initial update of the first replication is finished. This is 
    * used to concatenate replications. A typical usecase is in two way replication, then the initial update of the back replication can be automatically triggered
    * once the initial update of the main replication is finished.
    * 
    * @param slaveSessionName
    * @param replicationKey This is the dbWatcher replication.prefix attribute.
    * @param cascadeSlaveSessionName The Name of the session of the dbWriter to be used for the cascaded replication. Can be null.
    * @param cascadedReplicationPrefix the prefix identifing the DbWatcher for the cascaded replication. Can be null.  
    * @throws Exception
    */
   String initiateReplication(String slaveSessionName, String replPrefix, String cascadeSlaveSessionName, String cascadeReplPrefix) throws Exception;

   /**
    * @deprecated you should use the four arguments alternative.
    * 
    * @param slaveSessionName
    * @param replPrefix
    * @return
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
