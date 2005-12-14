/*------------------------------------------------------------------------------
Name:      ReplicationScheduler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.detector.AlertScheduler;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;

/**
 * 
 * ReplicationScheduler extends the AlertScheduler which is a poller to the database. The only purpose of this 
 * extention is to call a stored procedure which triggers a detection of changes in structure of the tables 
 * currently watched and/or replicated. The call to this stored procedure is only needed in such database
 * implementations where it is not possible to get CREATE TABLE, DROP TABLE or ALTER TABLE events by means of
 * trigger. This is for example the case of PostGres 8.0.
 *  
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

public class ReplicationScheduler extends AlertScheduler {

   
   private I_DbSpecific dbSpecific;
   private boolean isInit;
   
   /**
    * 
    */
   public ReplicationScheduler() {
      super();
   }
   
   /**
    * Create a scheduler, calls {@link #init}.  
    * @param info The configuration environment
    * @param changeDetector The class to be alerted then and again
    */
   public ReplicationScheduler(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      super(info, changeDetector);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#init(I_Info)
    */
   public synchronized void init(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      if (this.isInit)
         return;
      boolean forceCreationAndInit = true;
      this.dbSpecific = ReplicationConverter.getDbSpecific(info, forceCreationAndInit);
      super.init(info, changeDetector);
      this.isInit = true;
   }
   
   /**
    * Used by scheduler thread internally. 
    */
   public void run() {
      try {
         this.dbSpecific.forceTableChangeCheck();
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
      super.run();
   }
   
   /**
    * Stop the scheduler. 
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#shutdown
    */
   public synchronized void shutdown() throws Exception {
      if (this.dbSpecific == null)
         return;
      try {
         super.shutdown();
      }
      finally {
         this.dbSpecific.shutdown();
         this.dbSpecific = null;
      }
   }

}
