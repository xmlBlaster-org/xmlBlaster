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
   
   // public final static int STATUS_UNUSED = 0;
   public final static int STATUS_INITIAL = 1;
   public final static int STATUS_TRANSITION = 2;
   public final static int STATUS_NORMAL = 3;

   /**
    * Starts the whole initial update
    * @throws Exception
    */
   void run(I_Info individualInfo, String dbWatcherSessionId) throws Exception;
   /**
    * 3
    *
    */
   void prepareForRequest(I_Info individualInfo) throws Exception;
   
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
   
}
