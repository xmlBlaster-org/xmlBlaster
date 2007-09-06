package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Set;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;

public class RsToSqlInfo implements I_ResultCb {

   private static Logger log = Logger.getLogger(RsToSqlInfo.class.getName());
   private int rowsPerMessage;
   private InitialUpdater initialUpdater;
   private SqlInfo sqlInfo;
   private Set cancelledUpdates;
   private I_AttributeTransformer transformer;
   private long newReplKey;
   private String destination;
   
   public RsToSqlInfo(InitialUpdater initialUpdater, SqlInfo sqlInfo, Set cancelledUpdates, I_AttributeTransformer transformer, long newReplKey, int rowsPerMessage, String destination) {
      this.initialUpdater = initialUpdater;
      this.sqlInfo = sqlInfo;
      this.newReplKey = newReplKey;
      this.cancelledUpdates = cancelledUpdates;
      this.transformer = transformer;
      this.rowsPerMessage = rowsPerMessage;
      this.destination = destination;
   }
   
   /**
    * Can handle all sort of tables (not REPL_ITEM) for initial scan. 
    * @see I_ResultCb#init(ResultSet)
    */
   public final void result(Connection conn, ResultSet rs) throws Exception {
      try {
         // TODO clear the columns since not really used anymore ...
         int msgCount = 1; // since 0 was the create, the first must be 1
         int internalCount = 0;
         while (rs != null && rs.next()) {
            // this.dbUpdateInfo.fillOneRowWithStringEntries(rs, null);
            this.sqlInfo.fillOneRowWithObjects(rs, this.transformer);
            internalCount++;
            log.fine("processing before publishing *" + internalCount + "' of '" + this.rowsPerMessage + "'");
            if (internalCount == this.rowsPerMessage) {
               internalCount = 0;
               // publish
               log.fine("result: going to publish msg '" + msgCount + "' and internal count '" + internalCount + "'");
               if (this.destination != null && cancelledUpdates.contains(this.destination)) {
                  cancelledUpdates.remove(this.destination);
                  log.info("The ongoing initial update for destination '" + this.destination + "' has been cancelled");
                  return;
               }
               this.initialUpdater.publishCreate(msgCount++, this.sqlInfo, this.newReplKey, this.destination);
               this.sqlInfo.getRows().clear(); // clear since re-using the same dbUpdateInfo
            }
         } // end while
         if (this.sqlInfo.getRows().size() > 0) {
            log.fine("result: going to publish last msg '" + msgCount + "' and internal count '" + internalCount + "'");
            this.initialUpdater.publishCreate(msgCount, this.sqlInfo, this.newReplKey, this.destination);
         }
      } catch (Exception e) {
         e.printStackTrace();
         log.severe("Can't publish change event meat for CREATE");
      }
   }
}
