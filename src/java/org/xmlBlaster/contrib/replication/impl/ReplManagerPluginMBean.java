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
    */
   String initiateReplication(String slaveSessionName, String replPrefix, String cascadeSlaveSessionName, String cascadeReplPrefix, String realInitialFilesLocation);

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
    * @throws Exception if either the repl or sql was null.
    */
   void broadcastSql(String repl, String sql/*, boolean highPrio*/) throws Exception;
   
   
   void removeSqlStatement(String statementId);
   
   /**
    * Performs a version transformation from one version to the other.
    * @param replPrefix The replication prefix of the source to be used.
    * @param destVersion The desidered version on the destination
    * @param destination The session name of the destination.
    * @param srcData The content of the data to be transformed
    * @return the transformed string.
    * 
    * @throws Exception
    */
   String transformVersion(String replPrefix, String destVersion, String destination, String srcData) throws Exception;

   /**
    * 
    *
    */
   void clearVersionCache();

   /**
    * Returns the (default) initialFileLocation which is the directory where the initial data is stored in case you want
    * to transfer it per file.
    * 
    * @return
    */
   String getInitialFilesLocation();

   /**
    * This method returns the polling interval for retrieval of the status data of the slaves which
    * has to be retrieved asynchronously in order not to block the monitor. 
    * @return
    */
   long getStatusPollerInterval();

   /**
    * This method sets the polling interval for retrieval of the status data of the slaves which
    * has to be retrieved asynchronously in order not to block the monitor. If you set it to zero or 
    * a negative value it will not refresh. 
    * 
    * @param statusPollerInterval
    */
   void setStatusPollerInterval(long statusPollerInterval);

   /**
    * Returns the time in milliseconds it took to retrieve all the status information for all the slaves.
    * @return
    */
   long getStatusProcessingTime();

   /**
    * Returns the number of refreshes occured (to get the status of the slaves).
    * @return
    */
   long getNumOfRefreshes();
   
   /**
    * This will start an initial update for all slaves replicating from the specified replPrefix.
    * Note that this operation only can be used if the slaves have already been initiated before.
    * The registration for initial updates will be done automatically but you still have to manually
    * start the batch update for all these slaves manually with the startBatchUpdate command to the
    * same replPrefix.
    * 
    * @param replPrefix
    * @return
    */
   String reInitiate(String replPrefix);
   
   /**
    * Sends a message to the initial updater for the replPrefix that it can start the initial update
    * for all collected (registered) slaves. It will only use one single source per version, so if
    * you have 10 Slaves interested in version 1.0 and 5 interested in version 2.0 only two sources
    * (two exports) will be done.
    * 
    * @param replPrefix
    * @return
    */
   String startBatchUpdate(String replPrefix);
   
   /**
    * Sends a message to the initial updates to collect all requests of initial updates for this
    * replPrefix.
    * 
    * @param replicationPrefix
    * @return
    */
   String collectInitialUpdates(String replicationPrefix);
   
   String activateSlaveDispatchers(String replPrefix);
   String stopSlaveDispatchers(String replPrefix);

   void setMaxNumOfEntries(int maxNumOfEntries);
   int getMaxNumOfEntries();
}
