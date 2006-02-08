/*------------------------------------------------------------------------------
Name:      TimestampChangeDetector.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.detector;


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.sql.Connection;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.I_ChangeListener;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;

/**
 * Check the database and compare the change timestamp of a table to the previous one. 
 * <h2>Configuration</h2>
 * <ul>
 *  <li><tt>changeDetector.detectStatement</tt> the SQL statement which detects that a change has occurred, for example
 *      <tt>SELECT MAX(TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF')) FROM TEST_TS</tt>
 *  </li>
 *  <li><tt>changeDetector.timestampColNum</tt> is set to 1 and specifies the column number
 *       where the above <tt>changeDetector.detectStatement</tt> delivers the max result.
 *       Usually you don't need to change this.
 *  </li>
 *  <li><tt>db.queryMeatStatement</tt> is executed when a change was detected, it collects
 *      the wanted data to send as a message, for example
 *      <tt>SELECT * FROM TEST_POLL WHERE TO_CHAR(TS, 'YYYY-MM-DD HH24:MI:SSXFF') > '${oldTimestamp}' ORDER BY CAR</tt>.
 *      It should be order by the <tt>changeDetector.groupColName</tt> value (if such is given).
 *  </li>
 *  <li><tt>changeDetector.groupColName</tt> in the above example
 *      <tt>ICAO_ID</tt>, the SELECT must be sorted after this column and must
 *       list it. All distinct <tt>ICAO_ID</tt> values trigger an own publish event.
 *       If not configured, this plugin triggers on change exactly one publish event.
 *  </li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <p>The nature of this plugin is based on a timestamp comparison,
 * as such it does not detect <b>DELETE</b> changes of database rows, as this
 * will not create a new timestamp. All other commands (CREATE, INSERT, UPDATE) will
 * touch the timestamp and are therefor detected. Additionally a DROP is detected.</p>
   <table border="1">
     <tr>
       <th>DB statement</th>
       <th>Reported change</th>
       <th>Comment</th>
     </tr>
     <tr>
       <td>CREATE</td>
       <td>CREATE</td>
       <td>-</td>
     </tr>
     <tr>
       <td>INSERT</td>
       <td>UPDATE</td>
       <td>SQL <tt>INSERT</tt> statement are reported as <tt>UPDATE</tt></td>
     </tr>
     <tr>
       <td>UPDATE</td>
       <td>UPDATE</td>
       <td>-</td>
     </tr>
     <tr>
       <td>DELETE</td>
       <td>-</td>
       <td>Is not detected</td>
     </tr>
     <tr>
       <td>DROP</td>
       <td>DROP</td>
       <td>see <tt>mom.eraseOnDrop</tt> configuration</td>
     </tr>
   </table>
 *
 * <p>
 * Note that the previous timestamp value is hold in RAM only, after
 * plugin restart it is lost and a complete set of data is send again.
 * </p>
 * @author Marcel Ruff
 * @author Michele Laghi 
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
   String MINSTR = " ";
   protected String queryMeatStatement;

   // additions for replication ...
   protected String postUpdateStatement;
   
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

      this.MINSTR = this.info.get("changeDetector.MINSTR", this.MINSTR);
      
      this.queryMeatStatement = this.info.get("db.queryMeatStatement", (String)null);
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

      this.dbPool = DbWatcher.getDbPool(this.info);
      
      // if null: check the complete table
      // if != null: check for each groupColName change separately
      this.groupColName = this.info.get("changeDetector.groupColName", (String)null);
      if (this.groupColName != null && this.groupColName.length() < 1)
         this.groupColName = null;
      this.useGroupCol = this.groupColName != null;
      if (this.groupColName == null)
         this.groupColName = this.info.get("mom.topicName", "db.change.event");
      
      this.postUpdateStatement = this.info.get("changeDetector.postUpdateStatement", null);
      if (this.postUpdateStatement != null)
         this.postUpdateStatement = this.postUpdateStatement.trim();
      
   }

   
   /**
    * Compares the two strings as numerical values. If the newTimestamp is really newer than the oldTimestamp,
    * then it returns true, false otherwise.
    * 
    * @param oldTimestamp
    * @param newTimestamp
    * @return
    */
   private final boolean compareTo(String oldTimestamp, String newTimestamp) {
      int ret = newTimestamp.length() - oldTimestamp.length();
      if (ret == 0)
         return oldTimestamp.compareTo(newTimestamp) > 0;
      return ret < 0;
   }
   
   
   /**
    * Check the observed data for changes. 
    * @param attrMap Currently "oldTimestamp" can be passed to force a specific scan
    * @return true if the observed data has changed
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector#checkAgain
    */
   public synchronized int checkAgain(Map attrMap) throws Exception {
      if (log.isLoggable(Level.FINE)) log.fine("Checking for Timestamp changes '" + this.changeDetectStatement + "' ...");
      int changeCount = 0;
      this.changeCommand = null;
      // We need the connection for detection and in the same transaction to the queryMeat
      Connection conn = null;
      boolean reported = false;
      
      if (attrMap != null && attrMap.containsKey("oldTimestamp")) {
         this.oldTimestamp = (String)attrMap.get("oldTimestamp");
         log.info("Reconfigured oldTimestamp to '" +this.oldTimestamp + "' as given by attrMap");
      }

      try {
         conn = this.dbPool.reserve(); // This has been added 2005-08-27 (Michele Laghi)
         // FIXME this !!!!!
         // conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE); // TOO RESTRICTIVE IN MOST CASES !!!
         
         conn = this.dbPool.select(conn, this.changeDetectStatement, new I_ResultCb() {
            public void result(ResultSet rs) throws Exception {
               if (log.isLoggable(Level.FINE)) log.fine("Processing result set");
            
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
                    if (oldTimestamp != null && compareTo(oldTimestamp, newTimestamp)) {
                       // The newest entry was removed -> 
                       //changeCommand=DELETE
                       // as other DELETE are not detected, we ignore this one as well
                       if (log.isLoggable(Level.FINE)) log.fine("Ignoring DELETE of newest entry to be consistent (as we can't detect other DELETEs): oldTimestamp=" + oldTimestamp + " newTimestamp=" + newTimestamp);
                       changeCommand = null;
                    }
                  }
                  rowCount++;
               }

               if (rowCount > 1)
                  throw new IllegalArgumentException("Please correct your change detection SQL statement, it may return max one result set: 'changeDetector.detectStatement="+changeDetectStatement);

               if (log.isLoggable(Level.FINE)) log.fine("oldTimestamp=" + oldTimestamp + " newTimestamp=" + newTimestamp);
            }
         });
       
         if (this.changeCommand != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Data has changed");
            if (!"DROP".equals(this.changeCommand))
                tableExists = true;

            if (this.queryMeatStatement != null) { // delegate processing of message meat ...
               ChangeEvent changeEvent = new ChangeEvent(groupColName, null, null, this.changeCommand, null);
               String stmt = DbWatcher.replaceVariable(this.queryMeatStatement, oldTimestamp==null?MINSTR:oldTimestamp);
               try {
                  changeCount = changeListener.publishMessagesFromStmt(stmt, groupColName!=null, changeEvent, conn);
               }
               catch (Exception e) {
                  log.severe("Panic: Query meat failed for '" + stmt + "': " + e.toString()); 
                  reported = true;
                  throw e;
               }
           }
           else { // send message without meat ...
              String resultXml = "";
              ChangeEvent changeEvent = new ChangeEvent(groupColName, null, resultXml, this.changeCommand, null);
              if (dataConverter != null) { // add some basic meta info ...
                 ByteArrayOutputStream bout = new ByteArrayOutputStream();
                 BufferedOutputStream out = new BufferedOutputStream(bout);
                 dataConverter.setOutputStream(out, this.changeCommand, groupColName, changeEvent);
                 dataConverter.done();
                 resultXml = bout.toString();
                 changeEvent.setXml(resultXml);
              }
              changeListener.hasChanged(changeEvent);
              changeCount++;
           }
            oldTimestamp = newTimestamp;
            
            // TODO rollback in case of an exception and distributed transactions ...
            /*
             if (this.postUpdateStatement != null) {
               this.dbPool.update(conn, this.postUpdateStatement);
            }
            */
         }
      }
      catch (Exception e) {
         if (conn != null) {
            try {
               conn.rollback();
            }
            catch (SQLException ex) {
            }
         }
         if (!reported) {
            log.severe("Panic: Change detection failed for '" +
                       this.changeDetectStatement + "': " + e.toString()); 
         }
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
      if (this.dbPool != null) {
         this.dbPool.shutdown();
         this.dbPool = null;
      }
   }
}
