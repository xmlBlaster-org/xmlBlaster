/*------------------------------------------------------------------------------
Name:      MD5ChangeDetector.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.detector;


import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;
import org.xmlBlaster.contrib.dbwatcher.I_ChangeListener;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;


/**
 * Check the database and compare the MD5 of the result set
 * to the previous one. 
 * <p>Configuration:</p>
 * <ul>
 *  <li><tt>changeDetector.detectStatement</tt> for example
 *      <tt>"SELECT col1, col2, ICAO_ID FROM TEST_POLL ORDER BY ICAO_ID</tt>
 *  </li>
 *  <li><tt>changeDetector.groupColName</tt> in the above example
 *      <tt>ICAO_ID</tt>, the SELECT must be sorted after this column and must
 *       list it. All distinct <tt>ICAO_ID</tt> values trigger an own publish event.
 *       If not configured, the whole query is MD5 compared and triggers on change exactly one publish event
 *  </li>
 * </ul>
 * <p>
 * If the table does not exist in the DB no event is triggered, if an empty
 * table comes to existence an empty event with untouched topic name
 * is triggered:
 * </p>
 * <pre>
 * topic='db.change.event.${ICAO_ID}'
 * 
 * &lt;?xml version='1.0' encoding='UTF-8' ?>
 * &lt;sql>
 * &lt;/sql>
 * </pre>
 *
 * <p>
 * Note that the previous MD5 values are hold in RAM only, after
 * plugin restart they are lost and a complete set of data is send again.
 * </p>
 * 
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
       <td>INSERT</td>
       <td>On multiple table entries for a <tt>changeDetector.groupColName</tt> the change is reported as <tt>UPDATE</tt></td>
     </tr>
     <tr>
       <td>UPDATE</td>
       <td>UPDATE</td>
       <td>-</td>
     </tr>
     <tr>
       <td>DELETE</td>
       <td>DELETE</td>
       <td>-</td>
     </tr>
     <tr>
       <td>DROP</td>
       <td>DROP</td>
       <td>see <tt>mom.eraseOnDrop</tt> configuration</td>
     </tr>
   </table>
 * @author Marcel Ruff
 */
public class MD5ChangeDetector implements I_ChangeDetector
{
   private static Logger log = Logger.getLogger(MD5ChangeDetector.class.getName());
   protected I_Info info;
   protected I_ChangeListener changeListener;
   protected I_DataConverter dataConverter;
   protected I_DbPool dbPool;
   protected boolean poolOwner;
   protected boolean tableExists=true;
   protected MessageDigest digest;
   protected final Map md5Map = new HashMap();
   protected final Set touchSet = new HashSet();
   protected String changeDetectStatement;
   protected String groupColName;
   protected boolean useGroupCol;
   protected int changeCount;
   protected String queryMeatStatement;
   protected Connection conn;

   /**
    * Default constructor, you need to call {@link #init} thereafter. 
    */
   public MD5ChangeDetector() {
      // void
   }
   
   /**
    * Convenience constructor which calls {@link #init}.
    * @param info The configuration environment
    * @param changeListener The listener to notify if something has changed
    * @param dataConverter A converter or null
    * @throws Exception
    */
   public MD5ChangeDetector(I_Info info, I_ChangeListener changeListener, I_DataConverter dataConverter) throws Exception {
      init(info, changeListener, dataConverter);
   }
   
   /**
    * Needs to be called after construction. 
    * @param info The configuration
    * @param changeListener The listener to notify if something has changed
    * @param dataConverter If not null the data will be transformed immediately during change detection
    * @throws Exception
    */
   public synchronized void init(I_Info info, I_ChangeListener changeListener, I_DataConverter dataConverter) throws Exception {
      this.changeListener = changeListener;
      this.info = info;
      this.dataConverter = dataConverter;
      
      this.queryMeatStatement = info.get("db.queryMeatStatement", (String)null);
      if (this.queryMeatStatement != null && this.queryMeatStatement.length() < 1)
         this.queryMeatStatement = null;
      if (this.queryMeatStatement != null)
         this.queryMeatStatement = this.queryMeatStatement.trim();

      // Check if we need to make a data conversion
      if (this.dataConverter != null && this.queryMeatStatement != null) {
         this.dataConverter = null;
         log.info("Ignoring given dataConverter as 'db.queryMeatStatement' is configured");
      }

      try {
         this.digest = MessageDigest.getInstance("MD5");
      }
      catch(NoSuchAlgorithmException e) {
         log.severe("'MD5' is not supported: " + e.toString());
         throw e; //new Exception(e);
      }

      this.changeDetectStatement = this.info.get("changeDetector.detectStatement", "");
      if (this.changeDetectStatement == null) {
         throw new IllegalArgumentException("Please pass a change detection SQL statement, for example 'changeDetector.detectStatement=SELECT col1, col2 FROM TEST_POLL ORDER BY ICAO_ID'");
      }
      this.changeDetectStatement = this.changeDetectStatement.trim();

      ClassLoader cl = this.getClass().getClassLoader();

      this.dbPool = (I_DbPool)this.info.getObject("db.pool");
      if (this.dbPool == null) {
         String dbPoolClass = this.info.get("dbPool.class", "org.xmlBlaster.contrib.db.DbPool").trim();
         if (dbPoolClass.length() > 0) {
            this.dbPool = (I_DbPool)cl.loadClass(dbPoolClass).newInstance();
            this.dbPool.init(info);
            if (log.isLoggable(Level.FINE)) log.fine(dbPoolClass + " created and initialized");
         }
         else
            throw new IllegalArgumentException("Couldn't initialize I_DbPool, please configure 'dbPool.class' to provide a valid JDBC access.");
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
    * <p />
    * The method is synchronized so you can call it from the AlertScheduler
    * or manually from outside simultaneously. 
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector#checkAgain(Map)
    */
   public synchronized int checkAgain(Map attrMap) throws Exception {
      if (log.isLoggable(Level.FINE)) log.fine("Checking for MD5 changes ...");
      this.changeCount = 0;
      this.conn = null;

      try {
         this.conn = this.dbPool.select(this.conn, this.changeDetectStatement, new I_ResultCb() {
            public void result(Connection conn, ResultSet rs) throws Exception {
               if (log.isLoggable(Level.FINE)) log.fine("Processing result set");
            
               if (rs == null) {
                  int changeCount = 0; 
                  if (!tableExists || md5Map.size() == 0) {
                     if (log.isLoggable(Level.FINE)) log.fine("Table/view '" + changeDetectStatement + "' does not exist, no changes to report");
                  }
                  else {
                     if (log.isLoggable(Level.FINE)) log.fine("Table/view '" + changeDetectStatement + "' has been deleted");
                     Iterator it = md5Map.keySet().iterator();
                     while (it.hasNext()) {
                        String key = (String)it.next();
                        String resultXml = "";
                        if (queryMeatStatement != null) { // delegate processing of message meat ...
                           ChangeEvent changeEvent = new ChangeEvent(groupColName, key, null, "DROP", null);
                           changeCount = changeListener.publishMessagesFromStmt(queryMeatStatement, useGroupCol, changeEvent, conn);
                        }
                        else {
                           if (dataConverter != null) {
                              ByteArrayOutputStream bout = new ByteArrayOutputStream();
                              BufferedOutputStream out = new BufferedOutputStream(bout);
                              dataConverter.setOutputStream(out, "DROP", key, null);
                              dataConverter.done();
                              resultXml = bout.toString();
                           }
                           changeListener.hasChanged(
                                 new ChangeEvent(groupColName, key, resultXml, "DROP", null));
                           changeCount++;
                        }
                     }
                     md5Map.clear();
                  }
                  tableExists = false;
                  return;
               }

               if (useGroupCol)
                  changeCount = checkWithGroupCol(conn, rs);
               else
                  changeCount = checkComplete(conn, rs);
               if (log.isLoggable(Level.FINE)) log.fine("Processing result set done");
            }
         });
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Panic: Change detection failed for '" +
                    this.changeDetectStatement + "': " + e.toString()); 
      }
      finally {
         if (this.conn != null) {
            this.conn.commit();
            this.dbPool.release(this.conn);
            this.conn = null;
         }
      }
      return changeCount;
   }

   /**
    * The select statement contains no grouping.  
    * <tt>CREATE</tt> and <tt>DROP</tt> are reliable, all other changes are marked as <tt>UPDATE</tt>.
    * @param rs The JDBC query result
    * @return The number of changes detected 
    * @throws Exception of any type
    */
   private int checkComplete(Connection conn, ResultSet rs) throws Exception {                
      int count = 0;
      String resultXml = "";
      ByteArrayOutputStream bout = null;
      BufferedOutputStream out = null;
      StringBuffer buf = new StringBuffer(2048);
      int cols = rs.getMetaData().getColumnCount();
      
      while (rs.next()) {
         for (int i=1; i<=cols; i++) { // Add cols for later MD5 calculation
            buf.append(rs.getString(i));
         }
         
         if (dataConverter != null) { // Create XML dump on demand
            if (bout == null) {
               bout = new ByteArrayOutputStream();
               out = new BufferedOutputStream(bout);
               String command = "UPDATE"; // (md5Map.size() == 0) ? "INSERT" : "UPDATE";
               dataConverter.setOutputStream(out, command, this.groupColName, null);
            }
            dataConverter.addInfo(conn, rs, I_DataConverter.ALL);
         }
      }

      String newMD5 = getMD5(buf.toString());
      String old = (String)md5Map.get(this.groupColName);
      
      if (old == null || !old.equals(newMD5)) {
         
         String command = (this.tableExists) ? "UPDATE" : "CREATE";
         this.tableExists = true;

         if (this.queryMeatStatement != null) { // delegate processing of message meat ...
            ChangeEvent changeEvent = new ChangeEvent(this.groupColName, null, null, command, null);
            String stmt = this.queryMeatStatement;
            count = changeListener.publishMessagesFromStmt(stmt, false, changeEvent, this.conn);
         }
         else { // send message without meat ...
            if (dataConverter != null) {
               if (bout == null) {
                  bout = new ByteArrayOutputStream();
                  out = new BufferedOutputStream(bout);
                  dataConverter.setOutputStream(out, command, this.groupColName, null);
                  dataConverter.addInfo(conn, rs, I_DataConverter.META_ONLY); // Add the meta info for a CREATE
               }
               dataConverter.done();
               resultXml = bout.toString();
            }
            
            changeListener.hasChanged(
               new ChangeEvent(this.groupColName, this.groupColName, resultXml, command, null));
            count++;
         }
      }
      
      md5Map.put(this.groupColName, newMD5);
      buf.setLength(0);
      return count;
   }
   
   /**
    * The select statement with grouping.
    * <p>
    * "CREATE" and "DROP" are reliable,
    * all other changes "UPDATE", "INSERT"
    * and "DELETE" are guessed and not reliable.
    * </p>
    * <p>
    * Note that for each removed groupColValue a "DROP" is issued
    * even so the table still may exist.
    * </p>
    * @param rs The JDBC query result
    * @return The number of changes detected 
    * @throws Exception of any type
    */
   private int checkWithGroupCol(Connection conn, ResultSet rs) throws Exception {
      int count = 0;
      int rowCount = 0;
      String resultXml = "";
      ByteArrayOutputStream bout = null;
      BufferedOutputStream out = null;
      String command = "UPDATE";
      ResultSetMetaData rsmd = rs.getMetaData();
      int cols = rsmd.getColumnCount();
      StringBuffer buf = new StringBuffer(2048);

      // default if no grouping is configured
      String groupColValue = "${"+groupColName+"}";
      String newGroupColValue = null;
      boolean first = true;
      
      try {
         while (rs.next()) {
            newGroupColValue = rs.getString(groupColName);
            if (rs.wasNull())
               newGroupColValue = "__NULL__";
            touchSet.add(newGroupColValue);
            if (rowCount == 0) {
               touchSet.add(groupColValue); // Add the CREATE table name: ${ICAO_ID} itself
               if (!this.tableExists) {
                  command = "CREATE";
                  if (this.queryMeatStatement != null) { // delegate processing of message meat ...
                      ChangeEvent changeEvent = new ChangeEvent(groupColName, groupColValue, null, command, null);
                      String stmt = org.xmlBlaster.contrib.dbwatcher.DbWatcher.replaceVariable(this.queryMeatStatement, groupColValue);
                      count = changeListener.publishMessagesFromStmt(stmt, true, changeEvent, conn);
                  }
                  else { // send message directly
                     if (dataConverter != null && bout == null) {
                        bout = new ByteArrayOutputStream();
                        out = new BufferedOutputStream(bout);
                        dataConverter.setOutputStream(out, command, groupColValue, null);
                        dataConverter.done();
                        resultXml = bout.toString();
                        bout = null;
                     }
                     changeListener.hasChanged(
                           new ChangeEvent(groupColName, groupColValue,
                                             resultXml, command, null));
                  }
                  this.tableExists = true;
               }
            }
            if (first) {
               command = (md5Map.get(newGroupColValue) != null) ? "UPDATE" : "INSERT";
            }

            rowCount++;
            if (dataConverter != null && bout == null) {
               bout = new ByteArrayOutputStream();
               out = new BufferedOutputStream(bout);
               dataConverter.setOutputStream(out, command, newGroupColValue, null);
            }

            if (!first && !groupColValue.equals(newGroupColValue)) {
               first = false;
               if (log.isLoggable(Level.FINE)) log.fine("Processing " + groupColName + "=" +
                  groupColValue + " next one to check is '" + newGroupColValue + "'");
               String newMD5 = getMD5(buf.toString());
               String old = (String)md5Map.get(groupColValue);
               if (old == null || !old.equals(newMD5)) {
                  if (this.queryMeatStatement != null) { // delegate processing of message meat ...
                      ChangeEvent changeEvent = new ChangeEvent(groupColName, groupColValue, null, command, null);
                      String stmt = org.xmlBlaster.contrib.dbwatcher.DbWatcher.replaceVariable(this.queryMeatStatement, groupColValue);
                      count += changeListener.publishMessagesFromStmt(stmt, true, changeEvent, conn);
                  }
                  else { // send message directly
                     if (dataConverter != null) {
                        dataConverter.done();
                        resultXml = bout.toString();
                     }
                     changeListener.hasChanged(
                           new ChangeEvent(groupColName, groupColValue,
                                             resultXml, command, null));
                     count++;
                  }
               }
               buf.setLength(0);
               command = (md5Map.get(newGroupColValue) != null) ? "UPDATE" : "INSERT";
               if (dataConverter != null) {
                  bout = new ByteArrayOutputStream();
                  out = new BufferedOutputStream(bout);
                  dataConverter.setOutputStream(out, command, newGroupColValue, null);
               }
               md5Map.put(groupColValue, newMD5);
            }
            groupColValue = newGroupColValue;

            for (int i=1; i<=cols; i++) { // Add cols for later MD5 calculation
               //System.out.println(">"+rs.getObject(i).toString()+"<");   ">oracle.sql.TIMESTAMP@157b46f<"
               //System.out.println(">"+rs.getString(i)+"<");              ">2005-1-31.23.0. 47. 236121000<"
               // -> getObject is not useful as it returns for same timestamp another object instance.
               buf.append(rs.getString(i));
            }
            
            if (dataConverter != null) { // Create XML dump on demand
               dataConverter.addInfo(conn, rs, I_DataConverter.ALL);
            }
            first = false;
         }

         String newMD5 = getMD5(buf.toString());
         String old = (String)md5Map.get(groupColValue);
         
         
         if (old == null || !old.equals(newMD5)) {
            if (!this.tableExists) {
               command = "CREATE";
               this.tableExists = true;
            }
            else if (old == null)
               command = "INSERT";
            else
               command = "UPDATE";
            
            if (this.queryMeatStatement != null) { // delegate processing of message meat ...
               ChangeEvent changeEvent = new ChangeEvent(groupColName, groupColValue, null, command, null);
               String stmt = org.xmlBlaster.contrib.dbwatcher.DbWatcher.replaceVariable(this.queryMeatStatement, groupColValue);
               count += changeListener.publishMessagesFromStmt(stmt, true, changeEvent, conn);
            }
            else { // send message directly
                if (dataConverter != null) {
                   if (bout == null) {
                      bout = new ByteArrayOutputStream();
                      out = new BufferedOutputStream(bout);
                      dataConverter.setOutputStream(out, command, newGroupColValue, null);
                      dataConverter.addInfo(conn, rs, I_DataConverter.META_ONLY); // Add the meta info for a CREATE
                   }
                   dataConverter.done();
                   resultXml = bout.toString();
                }
                changeListener.hasChanged(
                   new ChangeEvent(groupColName, groupColValue, resultXml, command, null));
                count++;
            }
         }
         touchSet.add(groupColValue);
         md5Map.put(groupColValue, newMD5);
         buf.setLength(0);
         
         // Check for DELETEd entries ...
         String[] arr = (String[])md5Map.keySet().toArray(new String[md5Map.size()]);
         for (int i=0; i<arr.length; i++) {
            if (!touchSet.contains(arr[i])) {
               String key = arr[i];
               md5Map.remove(key);
               command = "DELETE";
               if (this.queryMeatStatement != null) { // delegate processing of message meat ...
                  ChangeEvent changeEvent = new ChangeEvent(groupColName, key, null, command, null);
                  String stmt = org.xmlBlaster.contrib.dbwatcher.DbWatcher.replaceVariable(this.queryMeatStatement, key);
                  count += changeListener.publishMessagesFromStmt(stmt, true, changeEvent, conn);
               }
               else { // send message directly
                  if (dataConverter != null) {
                     bout = new ByteArrayOutputStream();
                     out = new BufferedOutputStream(bout);
                     dataConverter.setOutputStream(out, command, key, null);
                     dataConverter.done();
                     resultXml = bout.toString();
                  }
                  changeListener.hasChanged(
                     new ChangeEvent(groupColName, key,
                                    resultXml, command, null));
               }
               count++;
            }
         }
         //if (md5Map.size() == 0)
         //   this.tableExists = false;
      }
      finally {
         touchSet.clear();
      }
      return count;
   }

   /**
    * Calculate the MD5 value.  
    * @param value The accumulated string to check
    * @return The MD5 value
    */
   private String getMD5(String value) {
      this.digest.update(value.getBytes());
      return new String(this.digest.digest()); // Resets digest
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#shutdown
    * @throws Exception Typicall XmlBlasterException
    */
   public synchronized void shutdown() throws Exception {
      if (this.poolOwner) {
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }

}
