/*------------------------------------------------------------------------------
Name:      I_ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import org.xmlBlaster.contrib.I_Update;

/**
 * I_ReplSlave
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_ReplSlave extends I_Update {
   
   /**
    * Starts the whole initial update
    * @throws Exception
    */
   void run() throws Exception;
   /**
    * 3
    *
    */
   void prepareForRequest() throws Exception;
   
   /**
    * 4
    *
    */
   void requestInitialData() throws Exception;
   
   
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
   
   
   boolean checkForDestroy(String replKey) throws Exception;
   
}
