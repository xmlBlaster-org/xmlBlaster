/*------------------------------------------------------------------------------
Name:      TimestampChangeDetector.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.detector;


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.sql.Connection;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.I_ChangeListener;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;

/**
 * Check the database and compare the Timestamp of a table of the result set
 * to the previous one. 
 * <p>
 * Note: There is a transaction gap between the detectStatement and the
 * queryMeatStatement. Under heavy changes there may come some events
 * multiple times!
 * </p>
 * @author Marcel Ruff 
 */
public class TimestampChangeDetector implements I_ChangeDetector
{
   private static Logger log = Logger.getLogger(TimestampChangeDetector.class.getName());
   protected I_Info info;
   protected I_ChangeListener changeListener;
   protected I_DataConverter dataConverter;
   protected I_DbPool dbPool;
   protected boolean poolOwner;
   protected String changeDetectStatement;
   protected int timestampColNum;
   protected String groupColName;
   protected boolean useGroupCol;
   protected boolean tableExists=true;
   protected String changeCommand;
   protected String oldTimestamp;
   protected String newTimestamp;
   public final String MINSTR = " ";
   protected String queryMeatStatement;

   /**
    * @param info
    * @param changeListener
    * @param dataConverter
    * @see I_ChangeDetector#init
    * @throws Exception
    */
   public synchronized void init(I_Info info, I_ChangeListener changeListener, I_DataConverter dataConverter) throws Exception {
      this.changeListener = changeListener;
      this.info = info;
      this.dataConverter = dataConverter;
      
      this.queryMeatStatement = info.get("db.queryMeatStatement", (String)null);
      if (this.queryMeatStatement != null && this.queryMeatStatement.length() < 1)
         this.queryMeatStatement = null;

      // Check if we need to make a data conversion
      if (this.dataConverter != null && this.queryMeatStatement != null) {
         this.dataConverter = null;
         log.info("Ignoring given dataConverter as 'db.queryMeatStatement' is configured");
      }

      this.changeDetectStatement = this.info.get("changeDetector.detectStatement", "");
      if (this.changeDetectStatement == null || this.changeDetectStatement.length() < 1) {
         throw new IllegalArgumentException("Please pass a change detection SQL statement, for example 'changeDetector.detectStatement=SELECT col1, col2 FROM TEST_POLL ORDER BY ICAO_ID'");
      }

      this.timestampColNum = this.info.getInt("changeDetector.timestampColNum", 1);
      if (this.timestampColNum < 1) {
         throw new IllegalArgumentException("Please pass the JDBC index (starts with 1) of the column containing the timestamp, for example 'changeDetector.timestampColNum=1'");
      }

      this.dbPool = (I_DbPool)this.info.getObject("db.pool");
      if (this.dbPool == null) {
         this.dbPool = new DbPool(info);
         this.poolOwner = true;
         this.info.putObject("db.pool", this.dbPool);
      }
      
      // if null: check the complete table
      // if != null: check for each groupColName change separately
      this.groupColName = this.info.get("changeDetector.groupColName", (String)null);
      if (this.groupColName != null && this.groupColName.length() < 1)
         this.groupColName = null;
      this.useGroupCol = this.groupColName != null;
      if (this.groupColName == null)
         this.groupColName = this.info.get("mom.topicName", "db.change.event");
   }

   /**
    * Check the observed data for changes. 
    * @return true if the observed data has changed
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector#checkAgain
    */
   public synchronized int checkAgain(Map attrMap) throws Exception {
      if (log.isLoggable(Level.FINE)) log.fine("Checking for Timestamp changes '" + this.changeDetectStatement + "' ...");
      int changeCount = 0;
      this.changeCommand = null;
      // We need the connection for detection and in the same transaction to the queryMeat
      Connection conn = null;

      try {
         conn = this.dbPool.select(conn, this.changeDetectStatement, new I_ResultCb() {
            public void result(ResultSet rs) throws Exception {
               log.fine("Processing result set");
            
               // Check for missing/dropped table
               if (rs == null) {
                  if (!tableExists || oldTimestamp == null) {
                     if (log.isLoggable(Level.FINE)) log.fine("Table/view '" + changeDetectStatement + "' does not exist, no changes to report");
                  }
                  else {
                     if (log.isLoggable(Level.FINE)) log.fine("Table/view '" + changeDetectStatement + "' has been deleted");
                     changeCommand = "DROP";
                     oldTimestamp = null;
                  }
                  tableExists = false;
                  return;
               }

               // Check if something has changed
               newTimestamp = null;
               int rowCount = 0;
               while (rs.next()) {
                  newTimestamp = rs.getString(timestampColNum);
                  if (rs.wasNull())
                    newTimestamp = MINSTR;
                  if (oldTimestamp == null || !oldTimestamp.equals(newTimestamp)) {
                    changeCommand = (tableExists) ? "UPDATE" : "CREATE";
                  }
                  rowCount++;
               }

               if (rowCount > 1)
                  throw new IllegalArgumentException("Please correct your change detection SQL statement, it may return max one result set: 'changeDetector.detectStatement="+changeDetectStatement);
            }
         });
       
         if (this.changeCommand != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Data has changed");
            if (!"DROP".equals(this.changeCommand))
                tableExists = true;

            if (this.queryMeatStatement != null) { // delegate processing of message meat ...
                ChangeEvent changeEvent = new ChangeEvent(groupColName, null, null, this.changeCommand);
                String stmt = DbWatcher.replaceVariable(this.queryMeatStatement, oldTimestamp==null?MINSTR:oldTimestamp);
                changeCount = changeListener.publishMessagesFromStmt(stmt, groupColName!=null, changeEvent, conn);
            }
            else { // send message without meat ...
               String resultXml = "";
               if (dataConverter != null) { // add some basic meta info ...
                  ByteArrayOutputStream bout = new ByteArrayOutputStream();
                  BufferedOutputStream out = new BufferedOutputStream(bout);
                  dataConverter.setOutputStream(out, this.changeCommand, groupColName);
                  dataConverter.done();
                  resultXml = bout.toString();
               }
               changeListener.hasChanged(new ChangeEvent(groupColName, null,
                                         resultXml, this.changeCommand));
               changeCount++;
            }
            oldTimestamp = newTimestamp;
         }
      }
      catch (Exception e) {
         log.severe("Panic: Change detection failed for '" +
                    this.changeDetectStatement + "': " + e.toString()); 
      }
      finally {
         if (conn != null) {
            conn.commit();
            this.dbPool.release(conn);
         }
      }
      return changeCount;
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector#shutdown
    */
   public synchronized void shutdown() throws Exception {
      if (this.poolOwner) {
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }
}
