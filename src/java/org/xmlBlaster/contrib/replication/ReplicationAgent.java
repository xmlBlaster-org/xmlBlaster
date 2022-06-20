/*------------------------------------------------------------------------------
 Name:      ReplicationAgent.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.I_ChangeListener;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.plugin.PluginInfo;

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
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class ReplicationAgent implements I_InitialUpdateListener, I_ConnectionStateListener {
   
   private static Logger LOG = Logger.getLogger(ReplicationAgent.class.getName());
   
   // private I_Info readerInfo;
   // private I_Info writerInfo;
   
   private I_ChangeListener dbWatcher;
   private DbWriter dbWriter;
   // private static String replPrefix = "repl_";
   private String slaveName;
   private String[] cmdLineArgs;
   private boolean hasDbWatcher;
   private boolean stopWatcherOnIU;
   private boolean running = true;
   
   public class OwnGlobalInfo extends GlobalInfo {
      
      public OwnGlobalInfo(Global global, I_Info additionalInfo, String infoId) throws Exception {
         super(global, additionalInfo);
         put(ID, infoId);
      }

      public OwnGlobalInfo(GlobalInfo globalInfo, I_Info additionalInfo, String infoId, I_InitialUpdateListener agent) throws Exception {
         super(globalInfo, additionalInfo);
         put(ID, infoId);
         final String key = "org.xmlBlaster.util.Global";
         if (getObject(key) == null)
            putObject(key, globalInfo.getGlobal());
         if (agent != null)
            putObject(I_InitialUpdateListener.INITIAL_UPDATE_LISTENER_KEY, agent);
      }

      protected void doInit(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
      }
      
   } 
   
   private GlobalInfo createOwnGlobalInfo(Global global, I_Info additionalInfo, String infoId) throws Exception {
      return new OwnGlobalInfo(global, additionalInfo, infoId);
   }
   
   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedAlive(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   @Override
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      LOG.info(String.format("The connection '%s' is now alive", connection.getId()));
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedAliveSync(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   @Override
   public void reachedAliveSync(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      LOG.info(String.format("The connection '%s' is now alive (sync)", connection.getId()));
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedPolling(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   @Override
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      LOG.warning(String.format("The connection '%s' is now polling", connection.getId()));
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedDead(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   @Override
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      LOG.warning(String.format("The connection '%s' is now dead", connection.getId()));
   }

   public GlobalInfo prepare(String[] args) throws Exception {
      cmdLineArgs = args;
      Global global = new Global(cmdLineArgs);
      GlobalInfo cfgInfo = createOwnGlobalInfo(global, null, "configuration");
      stopWatcherOnIU = fillInfoWithCommandLine(cmdLineArgs, cfgInfo);
      return cfgInfo;
   }
   
   public boolean isRunning() {
      return running;
   }
   
   /**
    * Keys are the info objects and values are maps containing the used properties as key/value pairs.
    */
   public static void main(String[] args) {
      try {
         // I_Info cfgInfo = new PropertiesInfo(new Properties());
         
         ReplicationAgent agent = new ReplicationAgent();

         GlobalInfo cfgInfo = agent.prepare(args);
         I_Info readerInfo = agent.createReaderInfo(cfgInfo);
         I_Info writerInfo = agent.createWriterInfo(agent.prepare(args));
         
         if (ReplicationAgent.needsHelp(args)) {
            agent.displayHelp(readerInfo, writerInfo);
            System.exit(-1);
         }

         boolean isInteractive = cfgInfo.getBoolean("interactive", false);
         agent.init(readerInfo, writerInfo);

         LOG.info("REPLICATION AGENT IS NOW READY");
         if (isInteractive)
            agent.process(readerInfo, writerInfo);
         else {
            while (agent.isRunning()) {
               try {
                  Thread.sleep(5000L);
               }
               catch (Exception ex) {
                  LOG.severe("Exception occurred when sleeping: " + ex.getMessage());
                  ex.printStackTrace();
               }
            }
         }
         agent.shutdown();

      } 
      catch (Throwable ex) {
         LOG.severe("An exception occured when starting (or during shutdown)': " + ex.getMessage() + "', will exit");
         ex.printStackTrace();
         System.exit(-1);
      }
   }

   public I_ChangeListener getDbWatcher() {
      return this.dbWatcher;
   }

   public DbWriter getDbWriter() {
      return this.dbWriter;
   }

   /**
    * Default ctor.
    */
   public ReplicationAgent() {
   }

   private static final Map getCommonDefaultMap(I_Info subInfo) {
      String driversDefault = "org.hsqldb.jdbcDriver:" +
      "oracle.jdbc.driver.OracleDriver:" +
      "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
      "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
      "org.postgresql.Driver";
      if (subInfo != null)
         driversDefault = subInfo.get("JdbcDriver.drivers", driversDefault);
      Map defaultMap = new HashMap();
      defaultMap.put("jdbc.drivers", driversDefault);
      defaultMap.put("db.url", "jdbc:postgresql:test//localhost");
      defaultMap.put("db.user", "postgres");
      defaultMap.put("db.password", "");
      return defaultMap;
   }
   
   
   private static final Map getReaderDefaultMap(I_Info readerInfo) {
      Map defaultMap = getCommonDefaultMap(readerInfo);
      String prefix = readerInfo.get("replication.prefix", "repl_");
      defaultMap.put("mom.loginName", "DbWatcherPlugin.testPoll/1");
      defaultMap.put("mom.topicName", "trans_key");
      defaultMap.put("alertScheduler.pollInterval", "2000");
      defaultMap.put("changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");
      defaultMap.put("changeDetector.detectStatement", "SELECT MAX(repl_key) from " + prefix + "items");
      defaultMap.put("db.queryMeatStatement", "SELECT * FROM " + prefix + "items ORDER BY repl_key");
      defaultMap.put("converter.addMeta", "false");
      defaultMap.put("converter.class", "org.xmlBlaster.contrib.replication.ReplicationConverter");
      defaultMap.put("alertProducer.class", "org.xmlBlaster.contrib.replication.ReplicationScheduler");
      defaultMap.put("replication.doBootstrap", "true");
      return defaultMap;
   }

   private static final Map getWriterDefaultMap(I_Info writerInfo) {
      Map defaultMap = getCommonDefaultMap(writerInfo);
      defaultMap.put("mom.loginName", "DbWriter/1");
      defaultMap.put("replication.mapper.tables", "test1=test1_replica,test2=test2_replica,test3=test3_replica");
      defaultMap.put("dbWriter.writer.class", "org.xmlBlaster.contrib.replication.ReplicationWriter");
      defaultMap.put("dispatch/callback/retries", "-1");
      defaultMap.put("dispatch/callback/delay", "10000");
      defaultMap.put("dispatch/connection/retries", "-1");
      defaultMap.put("dispatch/connection/delay", "10000");
      return defaultMap;
   }

   private final static void showSubHelp(I_Info info, Map defaultMap, PrintStream out) {
      String[] keys = (String[])defaultMap.keySet().toArray(new String[defaultMap.size()]);
      out.println("" + keys.length + " default properties displayed: ");
      for (int i=0; i < keys.length; i++) {
         String value = info.get(keys[i], "");
         out.println("  " + keys[i] + "=" + value);
      }
   }
   
   private I_Info createReaderInfo(GlobalInfo cfgInfo) throws Exception {
      String masterFilename = cfgInfo.get("masterFilename", null);
      if (masterFilename == null)
         return null;
      Properties props = new Properties();
      if (!masterFilename.equalsIgnoreCase("default")) {
         InputStream is = getFileFromClasspath(masterFilename);
         props.load(is);
         is.close();
      }
      I_Info readerInfo = new OwnGlobalInfo(cfgInfo, new PropertiesInfo(props), "reader", null);

      readerInfo.putObject("_connectionStateListener", this);

      Map defaultMap = getReaderDefaultMap(readerInfo);
      
      String[] keys = (String[])defaultMap.keySet().toArray(new String[defaultMap.size()]);
      for (int i=0; i < keys.length; i++) {
         if (readerInfo.get(keys[i], null) == null)
            readerInfo.put(keys[i], (String)defaultMap.get(keys[i]));
      }
      return readerInfo;
   }
   
   
   public I_Info createWriterInfo(GlobalInfo cfgInfo) throws Exception {
      String slaveFilename = cfgInfo.get("slaveFilename", null);

      if (slaveFilename == null)
         return null;

      Properties props = new Properties();
      if (!slaveFilename.equalsIgnoreCase("default")) {
         InputStream is = getFileFromClasspath(slaveFilename);
         props.load(is);
         is.close();
      }
      
      I_InitialUpdateListener listener = null;
      if (stopWatcherOnIU)
         listener = this;
      I_Info writerInfo = new OwnGlobalInfo(cfgInfo, new PropertiesInfo(props), "writer", listener);
      
      Map defaultMap = getWriterDefaultMap(writerInfo);
      String[] keys = (String[])defaultMap.keySet().toArray(new String[defaultMap.size()]);
      for (int i=0; i < keys.length; i++) {
         if (writerInfo.get(keys[i], null) == null)
            writerInfo.put(keys[i], (String)defaultMap.get(keys[i]));
      }
      return writerInfo;
      
   }
   
   private static InputStream getFileFromClasspath(String filename) throws IOException {
      Class clazz = ReplicationAgent.class;
      Enumeration enm = clazz.getClassLoader().getResources(filename);
      if(enm.hasMoreElements()) {
         URL url = (URL)enm.nextElement();
         LOG.fine(" loading file '" + url.getFile() + "'");
         while(enm.hasMoreElements()) {
            url = (URL)enm.nextElement();
            LOG.warning("init: an additional matching file has been found in the classpath at '"
               + url.getFile() + "' please check that the correct one has been loaded (see info above)"
            );
         }
         return clazz.getClassLoader().getResourceAsStream(filename); 
      }
      else {
         ClassLoader cl = clazz.getClassLoader();
         StringBuffer buf = new StringBuffer();
         if (cl instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader)cl).getURLs();
            for (int i=0; i < urls.length; i++) 
               buf.append(urls[i].toString()).append("\n");
         }
         throw new IOException("init: no file found with the name '" + filename + "' : " + (buf.length() > 0 ? " classpath: " + buf.toString() : ""));
      }
   }
   
   private void startDbWatcher(I_Info readerInfo) throws Exception {
      if (readerInfo == null)
         return;
      this.dbWatcher = initializeDbWatcher(readerInfo, this.dbWriter);
      boolean autoSub = false;
      if (readerInfo != null)
         autoSub = readerInfo.getBoolean("_autoSubscribe", false);
      if (autoSub) {
         I_ChangePublisher publisher = (I_ChangePublisher)readerInfo.getObject("mom.publisher");
         if (publisher != null) {
            
            String prefix = readerInfo.get("replication.prefix", "repl_");
            String version = readerInfo.get("replication.version", "0.5");
            String prefixWithVersion = prefix + ReplicationConstants.VERSION_TOKEN + version;
            Thread.sleep(5000L);
            ReplSourceEngine.sendInitReplMsg(publisher, new String[] { slaveName }, prefixWithVersion, null, null, null, true);
         }
         else {
            LOG.warning("The publisher has not been initialized");
         }
      }
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
      if (writerInfo != null) {
         LOG.info("setUp: Instantiating DbWriter");
         GlobalInfo.setStrippedHostname(writerInfo, GlobalInfo.UPPER_CASE);
         this.dbWriter = new DbWriter();
         this.dbWriter.init(writerInfo);
         slaveName = "client/" + writerInfo.get("mom.loginName", "DbWriter/session/1");
      }
      startDbWatcher(readerInfo);
   }
   
   public void startInitialUpdate() throws Exception {
      // we stop the dbWatcher if any
      if (this.dbWatcher != null)
         shutdownDbWatcher();
   }
   
   public void stopInitialUpdate() throws Exception {
      if (hasDbWatcher) {
         GlobalInfo cfgInfo = prepare(cmdLineArgs);
         I_Info readerInfo = createReaderInfo(cfgInfo);
         startDbWatcher(readerInfo);
         LOG.info("The DbWatcher has been successfully restarted");
      }
   }

   private static DbWatcher initializeDbWatcher(I_Info readerInfo, DbWriter dbWriter) throws Exception {
      DbWatcher dbWatcher = null;
      if (readerInfo != null) {
         try {
            LOG.info("setUp: Instantiating DbWatcher");
            GlobalInfo.setStrippedHostname(readerInfo, GlobalInfo.UPPER_CASE);
            dbWatcher = new DbWatcher();
            dbWatcher.init(readerInfo);
            
            I_DbSpecific dbSpecific = null;
            if (readerInfo != null) {
               if (readerInfo.getBoolean("replication.doBootstrap", false)) {
                  boolean needsPublisher = readerInfo.getBoolean(I_DbSpecific.NEEDS_PUBLISHER_KEY, true);
                  boolean forceCreationAndInitNo = false;
                  dbSpecific = ReplicationConverter.getDbSpecific(readerInfo, forceCreationAndInitNo); // done only on master !!!
                  readerInfo.put(I_DbSpecific.NEEDS_PUBLISHER_KEY, "" + needsPublisher); // back to original
                  I_DbPool pool = (I_DbPool)readerInfo.getObject("db.pool");
                  if (pool == null)
                     throw new Exception("ReplicationAgent.init: the db pool is null");
                  Connection conn = pool.reserve();
                  try {
                     boolean doWarn = false;
                     boolean force = false;
                     dbSpecific.bootstrap(conn, doWarn, force);
                  }
                  catch (Exception ex) {
                     conn = SpecificDefault.removeFromPool(conn, SpecificDefault.ROLLBACK_YES, pool);
                  }
                  finally {
                     conn = SpecificDefault.releaseIntoPool(conn, SpecificDefault.COMMIT_YES, pool);
                  }
               }
            }
            dbWatcher.startAlertProducers();
         }
         catch (Exception ex) {
            if (dbWriter != null) {
               try {
                  dbWriter.shutdown();
               }
               catch (Exception e) {
                  ex.printStackTrace();
               }
            }
            throw ex;
         }
      }
      return dbWatcher;
   }

   private final void shutdownDbWatcher() throws Exception {
      if (this.dbWatcher != null) {
         try {
            this.dbWatcher.shutdown();
            this.dbWatcher = null;
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }      
   }

   public void shutdown() {
      try {
         shutdownDbWatcher();
         LOG.info("The DbWatcher has been shut down");
      }
      catch (Exception ex) {
         LOG.severe("An exception occured when shutting down the agent");
         ex.printStackTrace();
      }
      finally {
         if (this.dbWriter != null) {
            this.dbWriter.shutdown();
            this.dbWriter = null;
         }
      }
   }

   private static boolean needsHelp(String[] args) {
      for (int i=0; i < args.length; i++) {
         if ( args[i].equalsIgnoreCase("-h") ||
               args[i].equalsIgnoreCase("-help") ||
               args[i].equalsIgnoreCase("--h") ||
               args[i].equalsIgnoreCase("--help") ||
               args[i].equalsIgnoreCase("?")) {
            return true; 
         }
      }
      return false;
   }
   
   private boolean fillInfoWithCommandLine(String[] args, GlobalInfo cfgInfo) {
      String masterFilename = null;
      String slaveFilename = null;
      String isInteractiveTxt = "false";
      String stopWatcherOnIUTxt = "false";
      
      for (int i=0; i < args.length; i++) {
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
         if (args[i].equalsIgnoreCase("-interactive")) {
            if (i < (args.length-1))
               isInteractiveTxt = args[i+1];
         }
         if (args[i].equalsIgnoreCase("-stopWatcherOnIU")) {
            if (i < (args.length-1))
               stopWatcherOnIUTxt = args[i+1];
         }
         
      }
      if (masterFilename != null)
         hasDbWatcher = true;
      cfgInfo.put("masterFilename", masterFilename);
      cfgInfo.put("slaveFilename", slaveFilename);
      cfgInfo.put("interactive", isInteractiveTxt);
      return "true".equals(stopWatcherOnIUTxt);
   }

   private void displayHelp(I_Info readerInfo, I_Info writerInfo) throws Exception {
      System.out.println("usage: java org.xmlBlaster.contrib.replication.ReplicationAgent [-master masterPropertiesFilename] [-slave slavePropertiesFilename] [-interactive true/false]");
      System.out.println("for example :");
      System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master master.properties");
      System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -slave slave.properties");
      System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master master.properties -slave slave.properties");
      System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master default -interactive true");
      System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master");
      System.out.println("   java org.xmlBlaster.contrib.replication.ReplicationAgent -master -slave");
      System.out.println("\nwhere in the first case it will act as a master, the second as a slave and the third as both master and slave");
      System.out.println("The fourth will act as a master with the default properties, and so the fifth.");
      if (readerInfo != null) {
         Map defaultMap = getReaderDefaultMap(readerInfo);
         showSubHelp(readerInfo, defaultMap, System.out);
      }
      System.out.println("You could have several instances of slaves running but only one master instance on the same topic.");
      System.out.println("The 'interactive' flag is false per default.");
      System.out.println("\n");
      System.out.println("The content of the property files follows the java properties syntax");
      System.out.println("\nif you want the default configuration parameters but only a few exceptions, you don't need to specify");
      System.out.println("file names for the properties. You can set these with the JVM arguments as for example:");
      System.out.println("java -Dmom.loginName=dummyName org.xmlBlaster.contrib.replication.ReplicationAgent -master -slave");
      System.out.println("\n");
      if (writerInfo != null) {
         Map defaultMap = getWriterDefaultMap(readerInfo);
         showSubHelp(writerInfo, defaultMap, System.out);
      }
   }
   
   
   public final void process(I_Info readerInfo, I_Info writerInfo) throws Exception {
      InputStreamReader isr = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isr);

      I_Info info = readerInfo;
      if (info == null)
         info = writerInfo;
      if (info == null)
         return;
      
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      Connection conn = null;
      
      String prompt = "master>";
      if (readerInfo == null)
         prompt = "slave>"; 
      
      System.out.println(prompt + "make your sql statement");
      System.out.print(prompt);
      String line = null;
      while ( (line = br.readLine()) != null) {

         if (line.trim().length() < 1) {
            System.out.print(prompt);
            continue;
         }
         line = line.trim();
         if (line.equalsIgnoreCase("q") || 
               line.equalsIgnoreCase("quit") ||
               line.equalsIgnoreCase("exit") ||
               line.equalsIgnoreCase("stop") ||
               line.equalsIgnoreCase("finish")) 
            break;

         try {
            conn  = pool.reserve();
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
            conn = SpecificDefault.removeFromPool(conn, SpecificDefault.ROLLBACK_NO, pool);
         }
         finally {
            conn = SpecificDefault.releaseIntoPool(conn, SpecificDefault.COMMIT_NO, pool);
         }
         System.out.println(prompt + "make your sql statement");
         System.out.print(prompt);
      }
   
   }

   public void registerForUpdates(I_Update registeredForUpdates) {
      if (this.dbWriter != null)
         this.dbWriter.registerForUpdates(registeredForUpdates);
   }
   
}
