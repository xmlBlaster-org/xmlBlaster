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
   String getStatus();
   String getSqlResponse();
   boolean toggleActive() throws Exception;
   void cancelInitialUpdate() throws Exception;
   boolean isActive();
   long getQueueEntries() throws Exception;
   boolean isConnected();
   void clearQueue() throws Exception;
   long removeQueueEntries(long entries) throws Exception;
   void kill() throws Exception;
   String getSessionName() throws Exception;
   String reInitiateReplication() throws Exception;
   String getReplPrefix();
}