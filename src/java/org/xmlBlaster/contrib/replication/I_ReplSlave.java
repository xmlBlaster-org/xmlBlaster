/*------------------------------------------------------------------------------
Name:      I_ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.ArrayList;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queue.I_Queue;

/**
 * I_ReplSlave
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface I_ReplSlave extends I_ContribPlugin {
   
   public final static int STATUS_UNCONFIGURED = 0;
   public final static int STATUS_INITIAL = 1;
   public final static int STATUS_TRANSITION = 2;
   public final static int STATUS_NORMAL = 3;
   public final static int STATUS_INCONSISTENT = 4;
   
   public final static String DBWATCHER_SESSION_NAME = ".dbWatcherSessionName";
   public final static String CASCADED_REPL_SLAVE = ".cascadedReplSlave";
   public final static String CASCADED_REPL_PREFIX = ".cascadedReplPrefix";
   
   /**
    * Starts a replication
    * 
    * @param individualInfo
    * @param dbWatcherSessionId
    * @param cascadeReplPrefix can be null
    * @param cascadeSlaveSessionName can be null
    * @param onlyRegister true, then it only registers the initial update (it will wait until an start comes)
    * @return
    * @throws Exception
    */
   boolean run(I_Info individualInfo, String dbWatcherSessionId, String cascadeReplPrefix, String cascadeSlaveSessionName, boolean onlyRegister) throws Exception;
   /**
    * 3
    *
    */
   void prepareForRequest(I_Info individualInfo) throws Exception;
   
   String getStatus();
   
   String getReplPrefix();

   boolean reInitiate(I_Info info) throws Exception;
   
   /**
    *
    *
    */
   void requestInitialData(String dbWatcherSessionId, boolean onlyRegister) throws Exception;
   
   
   /**
    * 6
    * This is received by the DbWatcher jvm. If the maxReplKey != (minReplKey+1),
    * then it means somebody has written into the database while operating,
    * This could result in inconsistencies in cases transaction isolation can not
    * be assured.
    * 
    * @param minReplKey the replication key taken before initiating the db side
    * operation on the master.
    * @param maxReplKey the replication key taken after initiating the db side
    * operation on the master.
    */
   void reactivateDestination(long minReplKey, long maxReplKey) throws Exception;
   

   ArrayList check(ArrayList pushEntries, I_Queue queue) throws Exception;

   void postCheck(MsgUnit[] processedEntries) throws Exception;
   
   /**
    * @param sqlResponse The sqlResponse to set.
    */
   void setSqlResponse(String sqlResponse);

   /**
    * Pauses the dispatcher. and sets the message. 
    */
   void handleException(Throwable ex);
   
   /**
    * Checks on the backend data which has to be displayed on the MBean.
    *
    */
   void checkStatus();
   
   int getStatusAsInt();

   String getSessionName();

   boolean setDispatcher(boolean status);
   
   boolean setDispatcher(boolean status, boolean doPersist) throws Exception;
   
   /**
    * Increments the amount of entries in the ptp counter queue
    * @param numOfTransactions
    */
   void incrementPtPEntries(long numOfTransactions);

}
