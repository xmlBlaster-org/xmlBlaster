/*------------------------------------------------------------------------------
Name:      ReplSlaveMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

public interface ReplSlaveMBean {
   String getTopic();
   long getMinReplKey();
   long getMaxReplKey();
   long getTransactionSeq();
   String getStatus();
   String getSqlResponse();
   boolean toggleActive() throws Exception;
   void cancelInitialUpdate() throws Exception;
   void clearQueue() throws Exception;
   long removeQueueEntries(long entries) throws Exception;
   void kill() throws Exception;
   String reInitiateReplication() throws Exception;
   String getReplPrefix();
   String getVersion();

   /** These go to the backend (are invoked asynchronously to avoid blocking) */
   boolean isActive();
   long getQueueEntries();
   boolean isConnected();
   String getSessionName();
   String getLastMessage();
   
   String dumpEntries(int maxNum, long maxSize, String fileName);
   String dumpFirstEntry();
   
   /**
    * Returns a string telling in which state the connection is. It can be stalled, connected or disconnected.
    * @return
    */
   public String getConnection();
   
   // these are for the associated replication (if any)
   boolean isCascading();
   String getCascadedSessionName();
   long getCascadedQueueEntries();
   long getCascadedTransactionSeq();
   String getCascadedStatus();
   boolean isCascadedActive();
   boolean isCascadedConnected();
   String getCascadedVersion();
   
}