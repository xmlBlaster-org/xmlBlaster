/*------------------------------------------------------------------------------
 Name:      ReplicationAgent.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConverter;

/**
 * Test basic functionality.
 * <p>
 * To run most of the tests you need to have a database (for example Postgres).
 * </p>
 * <p>
 * The connection configuration (url, password etc.) is configured as JVM
 * property or in {@link #createTest(I_Info, Map)} and
 * {@link #setUpDbPool(I_Info)}
 * </p>
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ReplicationAgent {
   private static Logger log = Logger.getLogger(ReplicationAgent.class.getName());

   private I_Info readerInfo;
   private I_Info writerInfo;
   
   private DbWatcher dbWatcher;
   private DbWriter dbWriter;
   
   /**
    * Keys are the info objects and values are maps containing the used properties as key/value pairs.
    */
   private Map usedPropsMap = new HashMap();
   
   private String masterFilename;
   private String slaveFilename;
   
   public static void main(String[] args) {
      ReplicationAgent agent = new ReplicationAgent();
      try {
         if (agent.displayHelpAndCheck(args)) {
            System.exit(-1);
         }
         agent.setup();
         agent.process();
         agent.shutdown();

      } 
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

   /**
    * Default ctor.
    */
   public ReplicationAgent() {
   }

   /**
    * Helper method to fill the properties. If an entry is found in the system properties it is left as is.
    * 
    * @param info
    * @param key
    * @param val
    */
   private void setProp(I_Info info, String key, String val) {
      String tmp = info.get(key, null);
      if (tmp == null)
         info.put(key, val);
      Map map = (Map)this.usedPropsMap.get(info); 
      if (map == null) {
         map = new TreeMap();
         this.usedPropsMap.put(info, map);
      }
      String val1 = info.get(key, null);
      if (val1 != null)
         map.put(key, val1);
   }

   
   private void setupProperties(I_Info readerInfo, I_Info writerInfo) {
      // we hardcode the first ...
      if (readerInfo != null) {
         readerInfo.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
               "oracle.jdbc.driver.OracleDriver:" +
               "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
               "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
               "org.postgresql.Driver");
         setProp(readerInfo, "db.url", "jdbc:postgresql:test//localhost");
         setProp(readerInfo, "db.user", "postgres");
         setProp(readerInfo, "db.password", "");
         setProp(readerInfo, "mom.loginName", "DbWatcherPlugin.testPoll/1");
         setProp(readerInfo, "mom.topicName", "trans_stamp");
         setProp(readerInfo, "alertScheduler.pollInterval", "2000");
         setProp(readerInfo, "changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");
         setProp(readerInfo, "changeDetector.detectStatement", "SELECT MAX(repl_key) from repl_items");
         setProp(readerInfo, "db.queryMeatStatement", "SELECT * FROM repl_items ORDER BY repl_key");
         setProp(readerInfo, "changeDetector.postUpdateStatement", "DELETE from repl_items");
         setProp(readerInfo, "converter.addMeta", "false");
         setProp(readerInfo, "converter.class", "org.xmlBlaster.contrib.replication.ReplicationConverter");
         setProp(readerInfo, "alertProducer.class", "org.xmlBlaster.contrib.replication.ReplicationScheduler");
         setProp(readerInfo, "replication.doBootstrap", "true");
         
      }
      
      // and here for the dbWriter ...
      // ---- Database settings -----
      if (writerInfo != null) {
         writerInfo.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
               "oracle.jdbc.driver.OracleDriver:" +
               "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
               "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
               "org.postgresql.Driver");
         setProp(writerInfo, "db.url", "jdbc:postgresql:test//localhost");
         setProp(writerInfo, "db.user", "postgres");
         setProp(writerInfo, "db.password", "");
         setProp(writerInfo, "mom.loginName", "DbWriter/1");
         setProp(writerInfo, "replication.mapper.tables", "test_replication=test_replication2");
         String subscribeKey = System.getProperty("mom.subscribeKey", "<key oid='trans_stamp'/>");
         setProp(writerInfo, "mom.subscribeKey", subscribeKey);
         setProp(writerInfo, "mom.subscribeQos", "<qos><initialUpdate>false</initialUpdate><multiSubscribe>false</multiSubscribe><persistent>true</persistent></qos>");
         setProp(writerInfo, "dbWriter.writer.class", "org.xmlBlaster.contrib.replication.ReplicationWriter");
         // these are pure xmlBlaster specific properties
         setProp(writerInfo, "dispatch/callback/retries", "-1");
         setProp(writerInfo, "dispatch/callback/delay", "10000");
         setProp(writerInfo, "queue/callback/maxEntries", "10000");
      }
   }

   private void initProperties(String[] args) {
      this.readerInfo = new PropertiesInfo(new Properties(System.getProperties()));
      this.writerInfo = new PropertiesInfo(new Properties(System.getProperties()));
      setupProperties(this.readerInfo, this.writerInfo);
   }
   /**
    * Configure database access.
    */
   protected void setup() throws Exception {
      if (this.masterFilename != null) {
         Properties props = new Properties(System.getProperties());
         if (!this.masterFilename.equalsIgnoreCase("default")) {
            FileInputStream fis = new FileInputStream(this.masterFilename);
            props.load(fis);
            fis.close();
         }
         this.readerInfo = new PropertiesInfo(props);
      }
      if (this.slaveFilename != null) {
         Properties props = new Properties(System.getProperties());
         if (!this.slaveFilename.equalsIgnoreCase("default")) {
            System.out.println("slave is initializing");
            FileInputStream fis = new FileInputStream(this.slaveFilename);
            props.load(fis);
            fis.close();
         }
         this.writerInfo = new PropertiesInfo(props);
      }
      setupProperties(this.readerInfo, this.writerInfo);

      if (this.writerInfo != null) {
         log.info("setUp: Instantiating DbWriter");
         this.dbWriter = new DbWriter();
         this.dbWriter.init(this.writerInfo);
      }
      if (this.readerInfo != null) {
         log.info("setUp: Instantiating DbWatcher");
         this.dbWatcher = new DbWatcher();
         this.dbWatcher.init(this.readerInfo);
         this.dbWatcher.startAlertProducers();
      }
   }

   protected void shutdown() throws Exception {
      if (this.readerInfo != null) {
         this.dbWatcher.stopAlertProducers();
         this.dbWatcher.shutdown();
         this.dbWatcher = null;
      }
      if (this.readerInfo != null) {
         this.dbWriter.shutdown();
         this.dbWriter = null;
      }
      
   }

   private String displayProperties(I_Info info) {
      Map map = (Map)this.usedPropsMap.get(info);
      int length = map.size();
      String[] keys = (String[])map.keySet().toArray(new String[length]);
      StringBuffer buf = new StringBuffer(4096);
      for (int i=0; i < keys.length; i++) {
         Object val = map.get(keys[i]);
         buf.append(keys[i]);
         int nmax = 35;
         for (int j = keys[i].length(); j < nmax; j++) 
            buf.append(' ');
         buf.append(": ").append(val).append("\n");
      }
      return buf.toString();
   }
   
   
   private boolean displayHelpAndCheck(String[] args) {
      boolean needsHelp = false;
      if(args.length == 0)
         needsHelp = true;
      else {
         for (int i=0; i < args.length; i++) {
            if ( args[i].equalsIgnoreCase("-h") ||
                  args[i].equalsIgnoreCase("-help") ||
                  args[i].equalsIgnoreCase("--h") ||
                  args[i].equalsIgnoreCase("--help") ||
                  args[i].equalsIgnoreCase("?")) {
               needsHelp = true;
               break;
            }
            if (args[i].equalsIgnoreCase("-master")) {
               if (i == (args.length-1) || args[i+1].startsWith("-")) {
                  this.masterFilename = "default";
               }
               else
                  this.masterFilename = args[i+1];
            }
            if (args[i].equalsIgnoreCase("-slave")) {
               if (i == (args.length-1) || args[i+1].startsWith("-")) {
                  this.slaveFilename = "default";
               }
               else
                  this.slaveFilename = args[i+1];
            }
         }
      }
      if (needsHelp) {
         initProperties(args);
         System.out.println("usage: java org.xmlBlaster.contrib.replication.ReplicationAgent [-master masterPropertiesFilename] [-slave slavePropertiesFilename]");
         System.out.println("for example :");
         System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master master.properties");
         System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -slave slave.properties");
         System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master master.properties -slave slave.properties");
         System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master default");
         System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master");
         System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master -slave");
         System.out.println("\nwhere in the first case it will act as a master, the second as a slave and the third as both master and slave");
         System.out.println("The fourth will act as a master with the default properties, and so the fifth.");
         
         System.out.println("You could have several instances of slaves running but only one master instance on the same topic.");
         System.out.println("\n");
         System.out.println("The content of the property files follows the java properties syntax");
         System.out.println("For the master the configuration parameters are (here displayed with the associated values)");
         System.out.println("===========================================================================================");
         System.out.println(this.displayProperties(this.readerInfo));
         System.out.println("\nFor the slave the configuration parameters are (here displayed with the associated values) ");
         System.out.println("===========================================================================================");
         System.out.println(displayProperties(this.writerInfo));
         return true;
      }
      return false;
   }
   
   public final void process() throws Exception {
      log.info("Start");
      InputStreamReader isr = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isr);

      I_Info info = this.readerInfo;
      if (info == null)
         info = this.writerInfo;
      if (info == null)
         return;
      
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      Connection conn = null;
      
      String prompt = "master>";
      if (this.readerInfo == null)
         prompt = "slave>"; 
      
      try {
         conn  = pool.reserve();
         
         I_DbSpecific dbSpecific = null;
         if (this.readerInfo != null) {
            if (this.readerInfo.getBoolean("replication.doBootstrap", false)) {
               dbSpecific = ReplicationConverter.getDbSpecific(this.readerInfo); // done only on master !!!
               dbSpecific.bootstrap(conn);
            }
         }

         System.out.println(prompt + "make your sql statement");
         System.out.print(prompt);
         
         String line = null;
         while ( (line = br.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("q") || 
                  line.equalsIgnoreCase("quit") ||
                  line.equalsIgnoreCase("exit") ||
                  line.equalsIgnoreCase("stop") ||
                  line.equalsIgnoreCase("finish")) 
               break;

            try {
               Statement st = conn.createStatement();
               boolean ret = st.execute(line);
               if (ret) {
                  ResultSet rs = st.getResultSet();
                  while (rs.next()) {
                     int nmax = rs.getMetaData().getColumnCount();
                     StringBuffer buf = new StringBuffer(prompt);
                     for (int i=0; i < nmax; i++) {
                        buf.append(rs.getObject(i+1)).append("\t");
                     }
                     System.out.println(buf.toString());
                  }
                  System.out.println(prompt);
                  rs.close();
               }
               st.close();
            } 
            catch (Exception ex) {
               ex.printStackTrace();
            }
            System.out.println(prompt + "make your sql statement");
            System.out.print(prompt);
         }
      }
      finally {
         if (conn != null)
            pool.release(conn);
      }
      
   }

}
