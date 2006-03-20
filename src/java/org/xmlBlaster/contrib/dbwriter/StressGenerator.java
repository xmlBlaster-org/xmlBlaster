/*------------------------------------------------------------------------------
Name:      StressGenerator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.prefs.Preferences;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.Info;

/**
 * Example code to run DbWriter as a standalone application. 
 * <p>
 * You can edit this file and change the configuration settings.
 * </p>
 * <p>
 * Simple usage example:
 * </p>
 * <p>
 * <tt>java org.xmlBlaster.contrib.dbwriter.Example -db.password secret</tt>
 * </p>
 * <p>
 * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.dbwriter.Example -db.password secret</tt>
 * </p>
 * @author Marcel Ruff
 */
public class StressGenerator /*extends Thread*/ {
   private static Logger log = Logger.getLogger(StressGenerator.class.getName());
   
   private I_DbPool dbPool;
   private I_Info info;
   private Random random;
   private int nmax = 1000;
   private long sleep = 0L;
   private int compareNmax = 0;
   private int compareCount = 0;
   private long seed;
   private List operations = new ArrayList();
   private boolean commitCheck;
   private boolean exceptionOccured;
   
   public StressGenerator(I_Info info) throws Exception {
      this.info = info;
      ClassLoader cl = this.getClass().getClassLoader();
      this.dbPool = (I_DbPool)this.info.getObject("db.pool");
      if (this.dbPool == null) {
         String dbPoolClass = this.info.get("dbPool.class", "org.xmlBlaster.contrib.db.DbPool");
         if (dbPoolClass.length() > 0) {
             this.dbPool = (I_DbPool)cl.loadClass(dbPoolClass).newInstance();
             this.dbPool.init(info);
             if (log.isLoggable(Level.FINE)) log.fine(dbPoolClass + " created and initialized");
         }
         else
            throw new IllegalArgumentException("Couldn't initialize I_DbPool, please configure 'dbPool.class' to provide a valid JDBC access.");
         this.info.putObject("db.pool", this.dbPool);
      }
      // comment the next line out if you always want the same pseudo-random sequence.
      this.nmax = this.info.getInt("stress.nmax", 1000);
      this.sleep = this.info.getLong("stress.sleep", 0L);
      this.compareNmax = this.info.getInt("stress.compareNmax", 0);
      this.seed = this.info.getLong("stress.seed", 0L);
      this.commitCheck = this.info.getBoolean("stress.commitCheck", true);
      if (this.seed < 0L)
         this.seed = System.currentTimeMillis();
      this.random = new Random(this.seed);
      System.out.println("compareNmax: " + this.compareNmax);
   }
   
   public static void usage() {
      System.err.println("\n\n");
      System.err.println("'stress.nmax'        (1000) maximum number of tests/updates");
      System.err.println("'stress.sleep'          (0) time in ms to sleep between each update");
      System.err.println("'stress.compareNmax'    (0) number of updates after which to make a compare of replicated\n");
      System.err.println("                            items (compared toward source tables) (makes only sense if ");
      System.err.println("                            'stress.commitCheck' is set to 'false'");
      System.err.println("'stress.seed'           (0) seed number to use to gereate a pseudo-random sequence. If negative");
      System.err.println("                            then the current time is taken");
      System.err.println("'stress.commitCheck' (true) if true, then the 'replitems' table is erased after each check.");
      System.err.println("                            and a check is done on every commit (or rollback). You will not be");
      System.err.println("                            able to use DbWatcher when this option is choosen.");
      System.err.println("\n\n");
   }
   
   private int getUniqueId() {
      return this.random.nextInt(10);
   }
   
   private String getName() {
      return "myName" + this.random.nextInt(10);
   }
   
   private String getSurname() {
      return "mySurname" + this.random.nextInt(1000);
   }
   
   private String getEmail() {
      return "email" + this.random.nextInt(1000) + ".com";
   }

   private String getPhoto() {
      return "photo" + this.random.nextInt(1000);
   }
   
   private String getTable() {
      int val = this.random.nextInt(2);
      if (val == 0)
         return "repltest";
      else 
         return "repltest2";
   }
   
   private String getWhere() {
      String ret = " WHERE uniqueId=" + getUniqueId() + " AND name='" + getName() + "'";
      return ret;
   }
   
   private int update(Connection conn, String sql) throws SQLException {
      Statement st = conn.createStatement();
      System.out.println(this.compareCount + "/" + this.compareNmax + ": '" + sql + "'");
      try {
         return st.executeUpdate(sql);
      }
      catch (SQLException ex) {
         this.exceptionOccured = true;
         throw ex;
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
   private int doInsert(Connection conn) throws SQLException {
      String sql = "INSERT INTO " + getTable() + " VALUES (" + getUniqueId() + ",'" + getName() + "','" + getSurname() + "','" + getEmail() + "','" + getPhoto() + "')";
      int ret = update(conn, sql);
      for (int i=0; i < ret; i++)
         this.operations.add(sql);
      if (ret > 1)
         log.severe("doUpdate: too many inserts have been done, should be max. one but are '" + ret + "'");
      return ret;
   }
   
   private int  doDelete(Connection conn) throws SQLException { 
      String sql = "DELETE FROM " + getTable() + getWhere();
      int ret = update(conn, sql);
      for (int i=0; i < ret; i++)
         this.operations.add(sql);
      if (ret > 1)
         log.severe("doDelete: too many deletions have been done, should be max. one but are '" + ret + "'");
      return ret;
   }
   
   private int doUpdate(Connection conn) throws SQLException { 
      String sql = "UPDATE " + getTable() + " SET email = '" + getEmail() + "', surname = '" + getSurname() + "', photo = '" + getPhoto() + "'" + getWhere();
      int ret = update(conn, sql);
      for (int i=0; i < ret; i++)
         this.operations.add(sql);
      if (ret > 1)
         log.severe("doUpdate: too many updates have been done, should be max. one but are '" + ret + "'");
      return ret;
   }
   
   private void assertEquals(String msg, String val1, String val2) throws Exception {
      if (val1 == null && val2 == null)
         return;
      if (val1 == null)
         throw new Exception(msg + " should be null but is '" + val2 + "'");
      if (val2 == null)
         throw new Exception(msg + " should be '" + val1 + "' but is null");
      if (!val2.equals(val1)) {
         throw new Exception(msg + " should be '" + val1 + "' but is '" + val2 + "'");
      }
   }
   
   private void assertEquals(String msg, int val1, int val2) throws Exception {
      if (val2 != val1) {
         throw new Exception(msg + " should be '" + val1 + "' but is '" + val2 + "'");
      }
   }
   
   private void checkConsistency(Connection conn, String tableName, long timeToSleep) throws Exception {
      conn.commit();
      System.out.print("COMPARING (sleep) ... '" + timeToSleep + "' ");
      Thread.sleep(timeToSleep);
      Statement st1 = null;
      Statement st2 = null;
      int count = 0;
      try {
         String sql1 = "SELECT * from " + tableName;
         String sql2 = "SELECT * from " + tableName + "repl";
         
         st1 = conn.createStatement();
         ResultSet rs1 = st1.executeQuery(sql1);
         st2 = conn.createStatement();
         ResultSet rs2 = st2.executeQuery(sql2);
         while (true) {
            boolean cont1 = rs1.next();
            boolean cont2 = rs2.next();
            if (cont1 != cont2)
               throw new Exception("checkConsistency of '" + tableName + "' failed because different length in result set cont1='" + cont1 + "' cont2='" + cont2 + "'");
            if (!cont1)
               break;
            count++;
            assertEquals(tableName + ": entry '" + count + "' uniqueId ", rs1.getInt(1), rs2.getInt(1));
            assertEquals(tableName + ": entry '" + count + "' name ", rs1.getString(2), rs2.getString(2));
            assertEquals(tableName + ": entry '" + count + "' surname ", rs1.getString(3), rs2.getString(3));
            assertEquals(tableName + ": entry '" + count + "' mail ", rs1.getString(4), rs2.getString(4));
            assertEquals(tableName + ": entry '" + count + "' photo ", rs1.getString(5), rs2.getString(5));
         }
      }
      finally {
         if (st1 != null)
            st1.close();
         if (st2 != null)
            st2.close();
         conn.commit();
      }
      System.out.println("COMPARISON OF TABLES '" + tableName + "' SUCCESSFULLY COMPLETED: entries '" + count + "' ");
   }
   
   private void checkReplItems(Connection conn) throws Exception {
      if (!this.commitCheck)
         return;
      Statement st = null;
      int count = 0;
      try {
         String sql = "SELECT * FROM replitems";
         
         st = conn.createStatement();
         ResultSet rs = st.executeQuery(sql);
         Iterator iter = this.operations.iterator();
         String oldTransId = null;
         while (true) {
            boolean cont1 = rs.next();
            boolean cont2 = iter.hasNext();
            if (cont1 != cont2) {
               System.err.println("====================================");
               for (int i=0; i < this.operations.size(); i++) {
                  System.err.println(this.operations.get(i));
               }
               System.err.println("====================================");
               throw new Exception("checkReplItems failed because different length rs='" + cont1 + "' sent='" + cont2 + "'");
            }
            if (!cont1)
               break;
            count++;
            String newTransId = rs.getString(2);  
            String completeOp = (String)iter.next();
            String op = completeOp.substring(0, completeOp.indexOf(' '));
            try {
               assertEquals("entry '" + count + "' has wrong action ", rs.getString(6), op);
               if (count != 1)
                  assertEquals("entry '" + count + "' has wrong transaction Id ", oldTransId, newTransId);
               oldTransId = newTransId;
            }
            catch (Exception ex) {
               System.err.println("====================================");
               for (int i=0; i < this.operations.size(); i++) {
                  System.err.println(this.operations.get(i));
               }
               System.err.println("====================================");
               
               throw ex;
            }
         }
         this.operations.clear();
         sql = "DELETE FROM replitems";
         st.close();
         st = conn.createStatement();
         st.executeUpdate(sql);
         st.close();
      }
      finally {
         if (st != null)
            st.close();
         conn.commit();
      }
   }

   private void commit(Connection conn) {
      try {
         if (conn != null) {
            if (this.exceptionOccured) {
               this.operations.clear();
               this.exceptionOccured = false;
               conn.rollback();
               
            }
            else 
               conn.commit();
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   public void run() {

      Connection conn = null;
      if (this.commitCheck) {
         log.warning("'commitCheck' is choosen: Entries on 'replitems' will be deleted after each commit. DbWatcher must be switched off !!!");
      }
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(false);
         this.compareCount = 1;
         for (int i=0; i < this.nmax; i++) {
            try {
               int choice = this.random.nextInt(4);
               switch (choice) {
               case 0 : doInsert(conn); break;
               case 1 : doUpdate(conn); break;
               case 2 : doDelete(conn); break;
               default :{
                  commit(conn);
                  checkReplItems(conn);
               }
               }
               Thread.sleep(this.sleep);
            }
            catch (SQLException ex) {
               
            }
            finally {
               if (this.compareCount == this.compareNmax && this.compareNmax > 0) {
                  this.compareCount = 1;
                  checkConsistency(conn, "repltest", 20000L);
                  checkConsistency(conn, "repltest2", 0L);
               }
               else
                  this.compareCount++;
            }
         }
      }
      catch (Throwable ex) {
         ex.printStackTrace();
      }
      finally {
         try {
            if (conn != null) {
               conn.setAutoCommit(true);
               this.dbPool.release(conn);
            }
         }
         catch (Throwable ex) {
            ex.printStackTrace();
         }
      }
   }
   
   
   
   /**
    * Example code. 
    * <p />
    * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.dbwatcher.Example -alertScheduler.pollInterval 10000 -db.password secret</tt>
    * @param args Command line
    */
   public static void main(String[] args) {
      try {
         System.setProperty("java.util.logging.config.file", "testlog.properties");
         LogManager.getLogManager().readConfiguration();

         Preferences prefs = loadArgs(args);
         if (prefs == null)
            return;
         Info info = new Info(prefs);
         StressGenerator example = new StressGenerator(info);
         example.run();
      
      }
      catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Parse command line arguments
    * @param args Command line
    * @return Configuration
    */
   public static Preferences loadArgs(String[] args) {
      try {
         Preferences prefs = Preferences.userRoot();
         prefs.clear();
         // String dbUrl = System.getProperty("db.url", "jdbc:oracle:thin:@localhost:1521:orcl");
         // String dbUser = System.getProperty("db.user", "system");

         String driverClass = System.getProperty("jdbc.drivers", "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
         String dbUrl = System.getProperty("db.url", "jdbc:postgresql:test//localhost/test");
         String dbUser = System.getProperty("db.user", "postgres");
         String dbPassword = System.getProperty("db.password", "");
      
         prefs.put("jdbc.drivers", driverClass);
         prefs.put("db.url", dbUrl);
         prefs.put("db.user", dbUser);
         prefs.put("db.password", dbPassword);

         prefs.put("stress.nmax", System.getProperty("stress.nmax", "1000"));
         prefs.put("stress.sleep", System.getProperty("stress.sleep", "0"));
         prefs.put("stress.compareNmax", System.getProperty("stress.compareNmax", "0"));
         prefs.put("stress.seed", System.getProperty("stress.seed", "0"));
         prefs.put("stress.commitCheck", System.getProperty("stress.commitCheck", "true"));
         
         for (int i=0; i<args.length; i++) {
            if (args[i].startsWith("-h")) {
               usage();
               return null;
            }
            if (i < (args.length-1)) {
               if (args[i].startsWith("-")) {
                  prefs.put(args[i].substring(1), args[++i]);
               }
            }
         }
         prefs.flush();
         return prefs;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Problems: " + e.toString());
      }
      return null;
   }
}