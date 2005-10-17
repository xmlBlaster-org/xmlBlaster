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
   private static String replPrefix = "repl_";
   
   /**
    * Keys are the info objects and values are maps containing the used properties as key/value pairs.
    */
   public static void main(String[] args) {
      I_Info cfgInfo = new PropertiesInfo(new Properties());
      cfgInfo.putObject("usedPropsMap", new HashMap());
      try {
         if (displayHelpAndCheck(args, cfgInfo)) {
            System.exit(-1);
         }
         cfgSetup(cfgInfo);
         I_Info readerInfo = (I_Info)cfgInfo.getObject("readerInfo");
         I_Info writerInfo = (I_Info)cfgInfo.getObject("writerInfo");
         
         ReplicationAgent agent = new ReplicationAgent();
         agent.init(readerInfo, writerInfo);
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
   private static void setProp(Map usedPropsMap, I_Info info, String key, String val) {
      String tmp = info.get(key, null);
      if (tmp == null)
         info.put(key, val);
      Map map = (Map)usedPropsMap.get(info); 
      if (map == null) {
         map = new TreeMap();
         usedPropsMap.put(info, map);
      }
      String val1 = info.get(key, null);
      if (val1 != null)
         map.put(key, val1);
   }

   private static void setupProperties(Map map, I_Info readerInfo, I_Info writerInfo) {
      // we hardcode the first ...
      if (readerInfo != null) {
         replPrefix = readerInfo.get("replication.prefix", "repl_");
         readerInfo.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
               "oracle.jdbc.driver.OracleDriver:" +
               "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
               "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
               "org.postgresql.Driver");
         setProp(map, readerInfo, "db.url", "jdbc:postgresql:test//localhost");
         setProp(map, readerInfo, "db.user", "postgres");
         setProp(map, readerInfo, "db.password", "");
         setProp(map, readerInfo, "mom.loginName", "DbWatcherPlugin.testPoll/1");
         setProp(map, readerInfo, "mom.topicName", "trans_key");
         setProp(map, readerInfo, "alertScheduler.pollInterval", "2000");
         setProp(map, readerInfo, "changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");
         setProp(map, readerInfo, "changeDetector.detectStatement", "SELECT MAX(repl_key) from " + replPrefix + "items");
         setProp(map, readerInfo, "db.queryMeatStatement", "SELECT * FROM " + replPrefix + "items ORDER BY repl_key");
         setProp(map, readerInfo, "changeDetector.postUpdateStatement", "DELETE from " + replPrefix + "items");
         setProp(map, readerInfo, "converter.addMeta", "false");
         setProp(map, readerInfo, "converter.class", "org.xmlBlaster.contrib.replication.ReplicationConverter");
         setProp(map, readerInfo, "alertProducer.class", "org.xmlBlaster.contrib.replication.ReplicationScheduler");
         setProp(map, readerInfo, "replication.doBootstrap", "true");
      }
      
      // and here for the dbWriter ...
      // ---- Database settings -----
      if (writerInfo != null) {
         writerInfo.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
               "oracle.jdbc.driver.OracleDriver:" +
               "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
               "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
               "org.postgresql.Driver");
         setProp(map, writerInfo, "db.url", "jdbc:postgresql:test//localhost");
         setProp(map, writerInfo, "db.user", "postgres");
         setProp(map, writerInfo, "db.password", "");
         setProp(map, writerInfo, "mom.loginName", "DbWriter/1");
         setProp(map, writerInfo, "replication.mapper.tables", "test1=test1_replica,test2=test2_replica,test3=test3_replica");
         String subscribeKey = System.getProperty("mom.subscribeKey", "<key oid='trans_key'/>");
         setProp(map, writerInfo, "mom.subscribeKey", subscribeKey);
         setProp(map, writerInfo, "mom.subscribeQos", "<qos><initialUpdate>false</initialUpdate><multiSubscribe>false</multiSubscribe><persistent>true</persistent></qos>");
         setProp(map, writerInfo, "dbWriter.writer.class", "org.xmlBlaster.contrib.replication.ReplicationWriter");
         // these are pure xmlBlaster specific properties
         setProp(map, writerInfo, "dispatch/callback/retries", "-1");
         setProp(map, writerInfo, "dispatch/callback/delay", "10000");
         setProp(map, writerInfo, "queue/callback/maxEntries", "10000");
      }
   }

   private static void initProperties(String[] args, I_Info cfgInfo) {
      Map usedPropsMap = (Map)cfgInfo.getObject("usedPropsMap");
      I_Info readerInfo = new PropertiesInfo(new Properties(System.getProperties()));
      I_Info writerInfo = new PropertiesInfo(new Properties(System.getProperties()));
      usedPropsMap.put(readerInfo, new TreeMap());
      usedPropsMap.put(writerInfo, new TreeMap());
      cfgInfo.putObject("readerInfo", readerInfo);
      cfgInfo.putObject("writerInfo", writerInfo);
      setupProperties(usedPropsMap, readerInfo, writerInfo);
   }
   /**
    * Configure database access.
    */
   public static void cfgSetup(I_Info cfgInfo) throws Exception {
      String masterFilename = cfgInfo.get("masterFilename", null);
      String slaveFilename = cfgInfo.get("slaveFilename", null);
      Map usedPropsMap = (Map)cfgInfo.getObject("usedPropsMap");
      I_Info readerInfo = null;
      I_Info writerInfo = null;
      if (masterFilename != null) {
         Properties props = new Properties(System.getProperties());
         if (!masterFilename.equalsIgnoreCase("default")) {
            FileInputStream fis = new FileInputStream(masterFilename);
            props.load(fis);
            fis.close();
         }
         readerInfo = new PropertiesInfo(props);
      }
      if (slaveFilename != null) {
         Properties props = new Properties(System.getProperties());
         if (!slaveFilename.equalsIgnoreCase("default")) {
            System.out.println("slave is initializing");
            FileInputStream fis = new FileInputStream(slaveFilename);
            props.load(fis);
            fis.close();
         }
         writerInfo = new PropertiesInfo(props);
      }
      setupProperties(usedPropsMap, readerInfo, writerInfo);
      cfgInfo.putObject("readerInfo", readerInfo);
      cfgInfo.putObject("writerInfo", writerInfo);
   }
      
    
   /**
    * Initializes the necessary stuff (encapsulated DbWatcher and DbWriter) and starts the DbWriter.
    * Note that the DbWatcher is only started if used, i.e. if the readerInfo is not null.
    * 
    * @param readerInfo
    * @param writerInfo
    * @throws Exception
    */
   public void init(I_Info readerInfo, I_Info writerInfo) throws Exception {
      // check if the info objects are really different (they must never be the same instance !!!)
      if (readerInfo != null && writerInfo != null && readerInfo == writerInfo)
         throw new Exception("ReplicationAgent.init: the info objects are the same instance. This will lead to problems. Check your code and make sure they are separate instances");
      this.readerInfo = readerInfo;
      this.writerInfo = writerInfo;
      if (this.writerInfo != null) {
         log.info("setUp: Instantiating DbWriter");
         this.dbWriter = new DbWriter();
         this.dbWriter.init(this.writerInfo);
      }
      if (this.readerInfo != null) {
         try {
            log.info("setUp: Instantiating DbWatcher");
            this.dbWatcher = new DbWatcher();
            this.dbWatcher.init(this.readerInfo);
            
            I_DbSpecific dbSpecific = null;
            if (this.readerInfo != null) {
               if (this.readerInfo.getBoolean("replication.doBootstrap", false)) {
                  boolean needsPublisher = readerInfo.getBoolean(I_DbSpecific.NEEDS_PUBLISHER_KEY, true);
                  dbSpecific = ReplicationConverter.getDbSpecific(this.readerInfo); // done only on master !!!
                  readerInfo.put(I_DbSpecific.NEEDS_PUBLISHER_KEY, "" + needsPublisher); // back to original
                  I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
                  if (pool == null)
                     throw new Exception("ReplicationAgent.init: the db pool is null");
                  Connection conn = pool.reserve();
                  try {
                     boolean doWarn = false;
                     boolean force = false;
                     dbSpecific.bootstrap(conn, doWarn, false);
                  }
                  catch (Exception ex) {
                     ex.printStackTrace();
                     pool.erase(conn);
                     conn = null;
                  }
                  finally {
                     if (conn != null)
                        pool.release(conn);
                  }
               }
            }
            this.dbWatcher.startAlertProducers();
         }
         catch (Exception ex) {
            if (this.dbWriter != null) {
               try {
                  this.dbWriter.shutdown();
               }
               catch (Exception e) {
                  ex.printStackTrace();
               }
            }
            throw ex;
         }
      }
   }

   public void shutdown() throws Exception {
      Exception e = null;
      if (this.readerInfo != null) {
         try {
            this.dbWatcher.shutdown();
            this.dbWatcher = null;
         }
         catch (Exception ex) {
            ex.printStackTrace();
            e = ex;
         }
      }
      if (this.readerInfo != null) {
         this.dbWriter.shutdown();
         this.dbWriter = null;
      }
      if (e != null)
         throw e;
   }

   private static String displayProperties(Map usedPropsMap, I_Info info) {
      Map map = (Map)usedPropsMap.get(info);
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
   
   
   private static boolean displayHelpAndCheck(String[] args, I_Info cfgInfo) {
      String masterFilename = null;
      String slaveFilename = null;
      
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
                  masterFilename = "default";
               }
               else
                  masterFilename = args[i+1];
            }
            if (args[i].equalsIgnoreCase("-slave")) {
               if (i == (args.length-1) || args[i+1].startsWith("-")) {
                  slaveFilename = "default";
               }
               else
                  slaveFilename = args[i+1];
            }
         }
      }
      boolean ret = false;
      if (needsHelp) {
         initProperties(args, cfgInfo);
         I_Info readerInfo = (I_Info)cfgInfo.getObject("readerInfo");
         I_Info writerInfo = (I_Info)cfgInfo.getObject("writerInfo");
         Map usedPropsMap = (Map)cfgInfo.getObject("usedPropsMap");
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
         System.out.println("For the master the configuration parameters are (here displayed with the associated default values)");
         System.out.println("===========================================================================================");
         System.out.println(displayProperties(usedPropsMap, readerInfo));
         System.out.println("\nFor the slave the configuration parameters are (here displayed with the associated default values) ");
         System.out.println("===========================================================================================");
         System.out.println(displayProperties(usedPropsMap, writerInfo));
         System.out.println("\nif you want the default configuration parameters but only a few exceptions, you don't need to specify");
         System.out.println("file names for the properties. You can set these with the JVM arguments as for example:");
         System.out.println("java -Dmom.loginName=dummyName org.xmlBlaster.contrib.replication.ReplicationAgent -master -slave");
         System.out.println("\n");
         ret = true;
      }
      cfgInfo.put("masterFilename", masterFilename);
      cfgInfo.put("slaveFilename", slaveFilename);
      return ret;
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
