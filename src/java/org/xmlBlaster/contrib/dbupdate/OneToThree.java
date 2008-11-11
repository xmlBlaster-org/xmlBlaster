package org.xmlBlaster.contrib.dbupdate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.client.queuemsg.ClientEntryFactory;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.TopicAccessor;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.jdbc.CommonTableDatabaseAccessor;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
import org.xmlBlaster.util.queue.jdbc.XBDatabaseAccessor;
import org.xmlBlaster.util.queue.jdbc.XBStore;

public class OneToThree {
   private static Logger log = Logger.getLogger(OneToThree.class.getName());
   private ServerScope serverScopeOne;
   private ServerScope serverScopeThree;
   private Global globalOne;
   private Global globalThree;
   private File to_file;
   private FileOutputStream out_;
   private Map xbStoreMap = new TreeMap();
   private int counter;
   private int total;

   private CommonTableDatabaseAccessor dbAccessorServerOne;
   private XBDatabaseAccessor dbAccessorServerThree;
   private CommonTableDatabaseAccessor dbAccessorClientOne;
   private XBDatabaseAccessor dbAccessorClientThree;

   public OneToThree(ServerScope serverScopeOne, ServerScope serverScopeThree, Global globalOne, Global globalThree)
         throws XmlBlasterException {
      this.serverScopeOne = serverScopeOne;
      this.serverScopeThree = serverScopeThree;
      this.globalOne = globalOne;
      this.globalThree = globalThree;
   }
   
   public void initConnections() throws Exception {
      if (this.dbAccessorServerOne == null)
         this.dbAccessorServerOne = createServerScopeAccessorOne();
      if (this.dbAccessorServerThree == null)
         this.dbAccessorServerThree = createServerScopeAccessorThree();

      if (this.dbAccessorClientOne == null)
         this.dbAccessorClientOne = createClientAccessorOne();
      if (this.dbAccessorClientThree == null)
         this.dbAccessorClientThree = createClientAccessorThree();
   }

   public void transformServerScope() throws Exception {
      initConnections();
      String[] queueNamePatterns = { 
            Constants.RELATING_TOPICSTORE, Constants.RELATING_MSGUNITSTORE,
            Constants.RELATING_SESSION, Constants.RELATING_SUBSCRIBE, Constants.RELATING_CALLBACK,
            Constants.RELATING_HISTORY };
      for (int i = 0; i < queueNamePatterns.length; i++) {
         final String queueNamePattern = queueNamePatterns[i] + "%";
         String flag = null; // "UPDATE_REF" "MSG_XML" etc.
         counter = 0;
         dbAccessorServerOne.getEntriesLike(queueNamePattern, flag, -1, -1, new I_EntryFilter() {
            public I_Entry intercept(I_Entry ent, I_Storage storage) {
               try {
                  if (!ent.isPersistent()) {
                     log.info("Ignoring transient entry " + ent.getLogId());
                     logToFile(queueNamePattern + "[counter=" + counter + "]: Ignoring transient entry "
                           + ent.getLogId());
                     return null;
                  }
                  if (ent instanceof ReferenceEntry) {
                     ReferenceEntry refEntry = (ReferenceEntry) ent;
                     String queueName = refEntry.getStorageId().getId(); // "callback:callback_nodeheronclientsubscriber71";
                     XBStore xbStore = getXBStore(queueName);
                     dbAccessorServerThree.addEntry(xbStore, refEntry);
                  } else {
                     I_MapEntry entry = (I_MapEntry) ent;
                     String queueName = entry.getStorageId().getId(); // msgUnitStore:msgUnitStore_heronHello
                     XBStore xbStore = getXBStore(queueName);
                     dbAccessorServerThree.addEntry(xbStore, entry);
                  }
                  counter++;
                  total++;
                  return null; // Filter away so getAll returns nothing
               } catch (Throwable e) {
                  e.printStackTrace();
                  log.warning("Ignoring during callback queue processing exception: " + e.toString());
                  logToFile(queueNamePattern + "[counter=" + counter + "]: Ignoring during processing exception: "
                        + e.toString());
                  return null; // Filter away so getAll returns nothing
               }
            }
         });
         logToFile(queueNamePattern + " [count=" + counter + "]: Done");
      }
   }

   public void transformClientSide() throws Exception {
      initConnections();
      String[] queueNamePatterns = { Constants.RELATING_CLIENT };
      for (int i = 0; i < queueNamePatterns.length; i++) {
         final String queueNamePattern = queueNamePatterns[i] + "%";
         String flag = null; // "UPDATE_REF" "MSG_XML" etc.
         counter = 0;
         dbAccessorClientOne.getEntriesLike(queueNamePattern, flag, -1, -1, new I_EntryFilter() {
            public I_Entry intercept(I_Entry ent, I_Storage storage) {
               try {
                  if (!ent.isPersistent()) {
                     log.info("Ignoring transient entry " + ent.getLogId());
                     logToFile(queueNamePattern + "[counter=" + counter + "]: Ignoring transient entry "
                           + ent.getLogId());
                     return null;
                  }
                  log.severe("TODO CLIENT SIDE");
                  counter++;
                  total++;
                  return null; // Filter away so getAll returns nothing
               } catch (Throwable e) {
                  e.printStackTrace();
                  log.warning("Ignoring during callback queue processing exception: " + e.toString());
                  logToFile(queueNamePattern + "[counter=" + counter + "]: Ignoring during processing exception: "
                        + e.toString());
                  return null; // Filter away so getAll returns nothing
               }
            }
         });
         logToFile(queueNamePattern + " [count=" + counter + "]: Done");
      }
   }

   /**
    * @param rawString
    *           e.g."org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    */
   private Properties parsePropertyValue(String rawString) throws XmlBlasterException {
      Properties params = new Properties();
      StringTokenizer st = new StringTokenizer(rawString, ",");
      boolean first = true;
      while (st.hasMoreTokens()) {
         String tok = st.nextToken();
         if (first) { // The first is always the class name
            first = false;
            continue;
         }
         int pos = tok.indexOf("=");
         if (pos < 0) {
            log.info("Accepting param '" + tok + "' without value (missing '=')");
            params.put(tok, "");
         } else
            params.put(tok.substring(0, pos), tok.substring(pos + 1));
      }
      return params;
   }
   
   public void logToFile(String text) {
      try {
         out_.write((text + "\n").getBytes());
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void createReportFile() throws Exception {
      String reportFileName = "OneToThree-report.xml";
      to_file = new File(reportFileName);
      if (to_file.getParent() != null) {
         to_file.getParentFile().mkdirs();
      }
      final FileOutputStream out = new FileOutputStream(to_file);
      out_ = out;
      out_.write(("XmlBlaster " + new Timestamp().toString()).getBytes());
      out_.write(("\n" + XmlBlasterException.createVersionInfo() + "\n").getBytes());

      log.info("Reporting check to '" + to_file.getAbsolutePath() + "'");
   }

   public void closeReportFile() {
      try {
         logToFile("Total processed=" + total);
         out_.close();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public CommonTableDatabaseAccessor createServerScopeAccessorOne() throws Exception {
      this.serverScopeOne.setTopicAccessor(new TopicAccessor(this.serverScopeOne));
      ServerEntryFactory sf = new ServerEntryFactory();
      sf.initialize(this.serverScopeOne);
      String queueCfg = this.serverScopeOne.getProperty().get("QueuePlugin[JDBC][1.0]", (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      JdbcConnectionPool pool = new JdbcConnectionPool();
      pool.initialize(this.serverScopeOne, queueProps);
      CommonTableDatabaseAccessor manager = new CommonTableDatabaseAccessor(pool, sf, "dbupdate.OneToThree", null);
      pool.registerStorageProblemListener(manager);
      manager.setUp();
      return manager;
   }

   public CommonTableDatabaseAccessor createClientAccessorOne() throws Exception {
      ClientEntryFactory sf = new ClientEntryFactory();
      sf.initialize(this.globalOne);
      String queueCfg = this.globalOne.getProperty().get("QueuePlugin[JDBC][1.0]", (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      JdbcConnectionPool pool = new JdbcConnectionPool();
      pool.initialize(this.globalOne, queueProps);
      CommonTableDatabaseAccessor manager = new CommonTableDatabaseAccessor(pool, sf, "dbupdate.ClientOneToThree", null);
      pool.registerStorageProblemListener(manager);
      manager.setUp();
      return manager;
   }

   public XBDatabaseAccessor createServerScopeAccessorThree() throws Exception {
      String confType = "JDBC";
      String confVersion = "1.0";
      String queueCfg = serverScopeThree.getProperty()
            .get("QueuePlugin[" + confType + "][" + confVersion + "]", (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      XBDatabaseAccessor accessorThree = XBDatabaseAccessor
            .createInstance(serverScopeThree, confType, confVersion, queueProps);
      return accessorThree;
   }

   public XBDatabaseAccessor createClientAccessorThree() throws Exception {
      String confType = "JDBC";
      String confVersion = "1.0";
      String queueCfg = globalThree.getProperty().get("QueuePlugin[" + confType + "][" + confVersion + "]",
            (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      XBDatabaseAccessor accessorThree = XBDatabaseAccessor.createInstance(globalThree, confType, confVersion,
            queueProps);
      return accessorThree;
   }

   public void wipeOutThree() throws Exception {
      String confType = "JDBC";
      String confVersion = "1.0";
      String queueCfg = serverScopeThree.getProperty()
            .get("QueuePlugin[" + confType + "][" + confVersion + "]", (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      XBDatabaseAccessor.wipeOutDB(serverScopeThree, confType, confVersion, queueProps, true);
   }

   /*
    * public XBDatabaseAccessor getXBDatabaseAccessor(String oldQueueName)
    * throws XmlBlasterException { String key = oldQueueName; XBDatabaseAccessor
    * accessor = (XBDatabaseAccessor) jdbcQueueMap.get(key); if (accessor ==
    * null) { accessor = new XBDatabaseAccessor(); StorageId uniqueQueueId = new
    * StorageId(globOne, oldQueueName); // jdbcQueue.init(globThree,
    * pluginInfo); // jdbcQueue.initialize(uniqueQueueId, queuePropertyBase);
    * jdbcQueueMap.put(key, accessor); } return accessor; }
    */

   public XBStore getXBStore(String oldQueueName) throws XmlBlasterException {
      String key = oldQueueName;
      XBStore store = (XBStore) xbStoreMap.get(key);
      if (store == null) {
         // oldQueueuName = callback:callback_nodeheronclientsubscriber71
         // prefix: callback
         // postfix: callback_nodeheronclientsubscriber71
         // xbnode: heron
         // xbpostfix: client/callback_nodeheronclientsubscriber71
         StorageId uniqueQueueId = new StorageId(serverScopeOne, oldQueueName);
         // store = new XBStore();
         // store.setId(id);
         // store.setNode(node);
         // store.setType(storeType);
         // store.setPostfix(storePostfix);
         store = dbAccessorServerThree.getXBStore(uniqueQueueId);
         // jdbcQueue.init(globThree, pluginInfo);
         // jdbcQueue.initialize(uniqueQueueId, queuePropertyBase);
         xbStoreMap.put(key, store);
      }
      return store;
   }

   /*
    * public JdbcQueue getJdbcQueue(String oldQueueName) throws
    * XmlBlasterException { String key = oldQueueName; JdbcQueue jdbcQueue =
    * (JdbcQueue) jdbcQueueMap.get(key); if (jdbcQueue == null) { jdbcQueue =
    * new JdbcQueue(); StorageId uniqueQueueId = new StorageId(globOne,
    * oldQueueName); QueuePropertyBase queuePropertyBase = null; if
    * (oldQueueName.toLowerCase().startsWith("session")) queuePropertyBase = new
    * SessionStoreProperty(globThree, globThree.getNodeId().getId()); else if
    * (oldQueueName.toLowerCase().startsWith("subscribe")) queuePropertyBase =
    * new SubscribeStoreProperty(globThree, globThree.getNodeId().getId()); else
    * if (oldQueueName.toLowerCase().startsWith("topicstore")) queuePropertyBase
    * = new TopicStoreProperty(globThree, globThree.getNodeId().getId()); else
    * if (oldQueueName.toLowerCase().startsWith("history")) queuePropertyBase =
    * new HistoryQueueProperty(globThree, globThree.getNodeId().getId()); else
    * if (oldQueueName.toLowerCase().startsWith("msgunitstore"))
    * queuePropertyBase = new MsgUnitStoreProperty(globThree,
    * globThree.getNodeId().getId()); else if
    * (oldQueueName.toLowerCase().startsWith("callback")) queuePropertyBase =
    * new CbQueueProperty(globThree, null, globThree.getNodeId().getId()); else
    * if (oldQueueName.toLowerCase().startsWith("connection")) queuePropertyBase
    * = new ClientQueueProperty(globThree, globThree.getNodeId().getId()); else
    * throw new IllegalArgumentException("Don't know how to handle queuename=" +
    * oldQueueName);
    * 
    * PluginInfo pluginInfo = null;
    * 
    * PluginInfo pluginInfo = new PluginInfo(globThree, pluginManager, "JDBC",
    * "1.0"); java.util.Properties prop =
    * (java.util.Properties)pluginInfo.getParameters();
    * prop.put("tableNamePrefix", "TEST"); prop.put("entriesTableName",
    * "_entries"); I_Queue tmpQueue = pluginManager.getPlugin(pluginInfo,
    * queueId, cbProp);
    * 
    * String queueCfg = globOne.getProperty().get("QueuePlugin[JDBC][1.0]",
    * (String)null);
    * 
    * jdbcQueue.init(globThree, pluginInfo); jdbcQueue.initialize(uniqueQueueId,
    * queuePropertyBase); jdbcQueueMap.put(key, jdbcQueue); } return jdbcQueue;
    * }
    */

   // java org.xmlBlaster.contrib.dbupdate.OneToThree -cluster.node.id heron
   public static void main(String[] args) {
      OneToThree ott = null;
      try {
         ott = new OneToThree(new ServerScope(args), new ServerScope(args), new Global(args), new Global(args));
         ott.createReportFile();
         ott.wipeOutThree();
         ott.transformServerScope();
         ott.transformClientSide();
      } catch (Exception e) {
         e.printStackTrace();
         if (ott != null)
            ott.closeReportFile();
      }
   }
}
