/*------------------------------------------------------------------------------
Name:      I_ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.ArrayList;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.queue.I_Queue;

/**
 * I_ReplSlave
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_ReplSlave extends I_ContribPlugin {
   
   public final static int STATUS_INITIAL = 1;
   public final static int STATUS_TRANSITION = 2;
   public final static int STATUS_NORMAL = 3;
   public final static int STATUS_INCONSISTENT = 4;
   
   public final static String DBWATCHER_SESSION_NAME = "_dbWatcherSessionName";
   public final static String CASCADED_REPL_SLAVE = "_cascadedReplSlave";
   public final static String CASCADED_REPL_PREFIX = "_cascadedReplPrefix";

   /**
    * Starts a replication
    * 
    * @param individualInfo
    * @param dbWatcherSessionId
    * @param cascadeReplPrefix can be null
    * @param cascadeSlaveSessionName can be null
    * @return
    * @throws Exception
    */
   boolean run(I_Info individualInfo, String dbWatcherSessionId, String cascadeReplPrefix, String cascadeSlaveSessionName) throws Exception;
   /**
    * 3
    *
    */
   void prepareForRequest(I_Info individualInfo) throws Exception;
   
   String getStatus();
   
   /**
    * 4
    *
    */
   void requestInitialData(String dbWatcherSessionId) throws Exception;
   
   
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

   
   /**
    * @param sqlResponse The sqlResponse to set.
    */
   void setSqlResponse(String sqlResponse);

   /**
    * Pauses the dispatcher. 
    * @param doPersist true if you want to persist the information that it was stopped.
    * @throws Exception
    */
   void doPause(boolean doPersist) throws Exception;
   
}
