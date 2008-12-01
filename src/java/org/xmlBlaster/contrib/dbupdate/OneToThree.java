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
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.TopicAccessor;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StopWatch;
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
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * Important: subjectId ending with a number can't be converted from xb_entries
 * to xbstore.
 * <p>
 * You need to have a xmlBlaster.properties with JdbcQueue configured
 * 
 * @author Marcel
 */
public class OneToThree {
   private static Logger log = Logger.getLogger(OneToThree.class.getName());
   private ServerScope serverScopeOne;
   private ServerScope serverScopeThree;
   private Global globalOne;
   private Global globalThree;
   private File to_file;
   private FileOutputStream out_;
   private Map<String, XBStore> xbStoreMap = new TreeMap<String, XBStore>();
   private int processed;
   private int totalProcessed;
   private int numAnalysed;
   private boolean limitPositivePubToOneDigit = true;
   private String clusterNodeToIgnore = "";

   private CommonTableDatabaseAccessor dbAccessorServerOne;
   private XBDatabaseAccessor dbAccessorServerThree;
   private CommonTableDatabaseAccessor dbAccessorClientOne;
   private XBDatabaseAccessor dbAccessorClientThree;

   private StopWatch stopWatch;

   public OneToThree(ServerScope serverScopeOne, ServerScope serverScopeThree, Global globalOne, Global globalThree)
         throws XmlBlasterException {
      this.serverScopeOne = serverScopeOne;
      this.serverScopeThree = serverScopeThree;
      this.globalOne = globalOne;
      this.globalThree = globalThree;
      this.clusterNodeToIgnore = this.serverScopeOne.getProperty().get("clusterNodeToIgnore", (String) "forstw");
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

      if (this.stopWatch == null)
         this.stopWatch = new StopWatch();
   }
   
   private boolean transferClusterNode(String nodeToTransfer) {
      /*
       * Inclusion filter: Does no work if having foreign cluster node clients
       * final String clusterNodeIdToTransfer =
       * serverScopeThree.getDatabaseNodeStr(); // "heron" if
       * (clusterNodeIdToTransfer.equals(nodeToTransfer)) { return true; } else
       * { return false; }
       */
      if (this.clusterNodeToIgnore == null || this.clusterNodeToIgnore.length() < 1)
         return true;
      // Exclusion filter
      if (this.clusterNodeToIgnore.equals(nodeToTransfer)) {
         return false;
      }
      else {
         return true;
      }
   }

   public void transformServerScope() throws Exception {
      initConnections();
      final String clusterNodeIdToTransfer = serverScopeThree.getDatabaseNodeStr();
      final String[] queueNamePatterns = { Constants.RELATING_TOPICSTORE, Constants.RELATING_MSGUNITSTORE,
            Constants.RELATING_SESSION, Constants.RELATING_SUBSCRIBE, Constants.RELATING_CALLBACK,
            Constants.RELATING_HISTORY, Constants.RELATING_SUBJECT };
      for (int i = 0; i < queueNamePatterns.length; i++) {
         final String relating = queueNamePatterns[i];
         final String queueNamePattern = queueNamePatterns[i] + "%";
         String flag = null; // "UPDATE_REF" "MSG_XML" etc.
         processed = 0;
         logToFile("Executing query on '" + queueNamePattern + "' ...");
         dbAccessorServerOne.getEntriesLike(queueNamePattern, flag, -1, -1, new I_EntryFilter() {
            public I_Entry intercept(I_Entry ent, I_Storage storage) {
               numAnalysed++;
               try {
                  if (!ent.isPersistent()) {
                     log.info("Ignoring transient entry " + ent.getLogId());
                     logToFile(queueNamePattern + "[counter=" + processed + "]: Ignoring transient entry "
                           + ent.getLogId());
                     return null;
                  }
                  if (relating.equals(Constants.RELATING_SUBJECT) || relating.equals(Constants.RELATING_CALLBACK)) {
                     // callback_nodeheronclientjack1
                     // clientsubscriber71
                     // New xbpostfix: "client/jack/session/1"
                     ReferenceEntry refEntry = (ReferenceEntry) ent;
                     // Reconstruct sessionName
                     // /node/heron/client/subscriberDummy <->
                     // clientsubscriberDummy1
                     SessionName sn = refEntry.getReceiver();
                     if (!transferClusterNode(sn.getNodeIdStr())) {
                        logToFile(relating + ": Ignoring wrong cluster node '"
                              + sn.getNodeIdStr() + "': "
                              + sn.getAbsoluteName());
                        return null;
                     }
                     // Fix pubSessionId if it was a PtP to subject
                     String queueName = refEntry.getStorageId().getOldPostfix();
                     // queueName=callback_nodeheronclientsubscriberDummy1
                     // subject_nodeheronclientsubscriberNotExist
                     String strippedLoginName = Global.getStrippedString(sn.getLoginName());
                     int pos = queueName.lastIndexOf(strippedLoginName);
                     if (pos != -1) {
                        String pubStr = queueName.substring(pos + strippedLoginName.length());
                        if (pubStr.length() > 0) { // has pubSessionId
                           try {
                              int pubSessionId = Integer.parseInt(pubStr);
                              sn = new SessionName(serverScopeThree, sn.getNodeId(), sn.getLoginName(), pubSessionId);
                           } catch (NumberFormatException e) {
                              logToFile(queueNamePattern + "[counter=" + processed + "]: SessionName problem queueName="
                                    + queueName + ": " + e.toString());
                              e.printStackTrace();
                           }
                        }
                     }
                     // logToFile(queueNamePattern + "[counter=" + counter
                     // + "]: queueName=" + queueName + " new=" +
                     // sn.getAbsoluteName() + " strippedLoginName=" +
                     // strippedLoginName + " oldPostfix=" +
                     // refEntry.getStorageId().getXBStore().getPostfix());
                     refEntry.setStorageId(new StorageId(serverScopeThree,
                           sn.getNodeId().getId()/* clusterNodeIdToTransfer */,
                           relating, sn));
                     /*
                      * } else { // Dangerous guess: String queueName =
                      * refEntry.getStorageId().getOldPostfix(); SessionName sn
                      * = SessionName.guessSessionName(serverScopeOne,
                      * clusterNodeIdToTransfer, queueName,
                      * limitPositivePubToOneDigit); refEntry.setStorageId(new
                      * StorageId(serverScopeThree, clusterNodeIdToTransfer,
                      * relating, sn)); }
                      */
                     XBStore xbStore = getXBStore(dbAccessorServerThree, serverScopeThree, refEntry.getStorageId());
                     dbAccessorServerThree.addEntry(xbStore, refEntry);
                  } else if (relating.equals(Constants.RELATING_HISTORY)) {
                     MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry) ent;
                     entry.getStorageId().getXBStore().setPostfix(entry.getKeyOid());
                     if (!entry.getStorageId().getPostfix1().startsWith(relating + "_" + clusterNodeIdToTransfer)) {
                        logToFile(relating + ": Ignoring wrong cluster node "
                              + entry.getStorageId().getPostfix1()
                              + ": " + entry.getStorageId().getId());
                        return null;
                     }
                     XBStore xbStore = getXBStore(dbAccessorServerThree, serverScopeThree, entry.getStorageId());
                     dbAccessorServerThree.addEntry(xbStore, entry);
                  } else if (ent instanceof ReferenceEntry) {
                     ReferenceEntry refEntry = (ReferenceEntry) ent;
                     XBStore xbStore = getXBStore(dbAccessorServerThree, serverScopeThree, refEntry.getStorageId());
                     dbAccessorServerThree.addEntry(xbStore, refEntry);
                  } else {
                     I_MapEntry entry = (I_MapEntry) ent;
                     if (relating.equals(Constants.RELATING_MSGUNITSTORE)) {
                        if (!entry.getStorageId().getPostfix1().startsWith(
                              relating + "_" + clusterNodeIdToTransfer)) {
                           logToFile(relating + ": Ignoring wrong cluster node "
                                 + entry.getStorageId().getPostfix1()
                                 + ": " + entry.getStorageId().getId());
                           return null;
                        }
                        MsgUnitWrapper msgUnitWrapper = (MsgUnitWrapper) entry;
                        entry.getStorageId().getXBStore().setPostfix(msgUnitWrapper.getKeyOid());
                     } else if (relating.equals(Constants.RELATING_SESSION)
                           || relating.equals(Constants.RELATING_SUBSCRIBE)) {
                        // "subPersistence,1_0" to "subPersistence,1.0"
                        // "topicStore_heron"
                        if (!entry.getStorageId().getPostfix1().startsWith(
                              relating + "_" + clusterNodeIdToTransfer)) {
                           logToFile(relating + ": Ignoring wrong cluster node "
                                 + entry.getStorageId().getPostfix1()
                                 + ": " + entry.getStorageId().getId());
                           return null;
                        }
                        entry.getStorageId().getXBStore().setPostfix(
                              ReplaceVariable.replaceAll(entry.getStorageId().getXBStore().getPostfix(), "1_0", "1.0"));
                     }
                     //else if (relating.equals(Constants.RELATING_TOPICSTORE)) {
                     //   TopicEntry topicEntry = (TopicEntry)entry;
                     // logToFile(queueNamePattern + " [processed=" + counter +
                     // "] processing topicStore " + entry.getLogId());
                     //}
                     XBStore xbStore = getXBStore(dbAccessorServerThree, serverScopeThree, entry.getStorageId());
                     dbAccessorServerThree.addEntry(xbStore, entry);
                  }
                  processed++;
                  if ((processed % 1000) == 0)
                     logToFile(queueNamePattern + " [processed=" + processed + "] processing ...");
                  totalProcessed++;
                  return null; // Filter away so getAll returns nothing
               } catch (Throwable e) {
                  e.printStackTrace();
                  log.warning(ent.getLogId() + " Ignoring during callback queue processing exception: " + e.toString());
                  logToFile(queueNamePattern + "[counter=" + processed + "]: Ignoring during processing exception: "
                        + e.toString() + " " + ent.getLogId());
                  return null; // Filter away so getAll returns nothing
               }
            }
         });
         logToFile(queueNamePattern + " [processed=" + processed + "]: Done");
      }
   }

   public void transformClientSide() throws Exception {
      initConnections();
      // final String clusterNodeIdToTransfer =
      // globalThree.getDatabaseNodeStr(); // "heron"
      String[] queueNamePatterns = { Constants.RELATING_CLIENT, Constants.RELATING_CLIENT_UPDATE };
      for (int i = 0; i < queueNamePatterns.length; i++) {
         final String queueNamePattern = queueNamePatterns[i] + "%";
         String flag = null; // "UPDATE_REF" "MSG_XML" etc.
         logToFile("Executing query on '" + queueNamePattern + "' ...");
         processed = 0;
         dbAccessorClientOne.getEntriesLike(queueNamePattern, flag, -1, -1, new I_EntryFilter() {
            public I_Entry intercept(I_Entry ent, I_Storage storage) {
               numAnalysed++;
               try {
                  if (!ent.isPersistent()) {
                     log.info("Ignoring transient entry " + ent.getLogId());
                     logToFile(queueNamePattern + "[counter=" + processed + "]: Ignoring transient entry "
                           + ent.getLogId());
                     return null;
                  }
                  MsgQueueEntry entry = (MsgQueueEntry) ent;

                  // "heron"
                  String nodeId = globalThree.getDatabaseNodeStr();
                  // MethodName: "publish", "subscribe"
                  // String relatingType = entry.getEmbeddedType();
                  // "connection_clientsubscriber1"
                  String queueName = entry.getStorageId().getOldPostfix();
                  StorageId oldStorageId = StorageId.valueOf(globalOne, queueName);
                  // "connection"
                  String relating = oldStorageId.getXBStore().getType();
                  // reset nodeId to ""
                  // nodeId = oldStorageId.getXBStore().getNode();
                  StorageId storageId = null;
                  // sn is most time null
                  SessionName sender = entry.getSender();// entry.getMsgUnit().getQosData().getSender();
                  SessionName receiver = entry.getReceiver();
                  SessionName guessed = SessionName.guessSessionName(globalOne, null, queueName,
                        limitPositivePubToOneDigit);
                  if (receiver != null && receiver.isNodeIdExplicitlyGiven())
                     nodeId = receiver.getNodeIdStr();
                  else if (guessed != null && guessed.isNodeIdExplicitlyGiven())
                     nodeId = guessed.getNodeIdStr();
                  // else if (sn != null)
                  // nodeId = sn.getNodeIdStr();
                  // if (sn != null) {
                  // storageId = new StorageId(globalThree, nodeId, relating,
                  // sn);
                  // } else {
                     // xb_entries.queueName="connection_clientpublisherToHeron2"
                     // --->
                     // xbstore.xbpostfix="client/publisherToHeron/2"
                  SessionName sn = SessionName.guessSessionName(globalOne, nodeId, queueName,
                        limitPositivePubToOneDigit);
                     storageId = new StorageId(globalThree, nodeId, relating, sn);
                  // }
                  logToFile("storageId=" + storageId.getXBStore().toString() + " from: nodeId=" + nodeId + "sender="
                        + (sender == null ? null : sender.getAbsoluteName()) + " receiver="
                        + (receiver == null ? null : receiver.getAbsoluteName()) + " guessed="
                        + (guessed == null ? null : guessed.getAbsoluteName()));
                  XBStore xbStore = getXBStore(dbAccessorClientThree, globalThree, storageId);
                  entry.getStorageId().getXBStore().setPostfix(storageId.getXBStore().getPostfix());
                  dbAccessorClientThree.addEntry(xbStore, entry);
                  processed++;
                  if ((processed % 1000) == 0)
                     logToFile(queueNamePattern + " [processed=" + processed + "] processing ...");
                  totalProcessed++;
                  return null; // Filter away so getAll returns nothing
               } catch (Throwable e) {
                  e.printStackTrace();
                  log.warning(ent.getLogId() + " Ignoring during callback queue processing exception: " + e.toString());
                  logToFile(queueNamePattern + "[counter=" + processed + "]: Ignoring during processing exception: "
                        + e.toString() + " " + ent.getLogId());
                  return null; // Filter away so getAll returns nothing
               }
            }
         });
         logToFile(queueNamePattern + " [processed=" + processed + "]: Done");
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
      log.info(text);
      try {
         String str = new Timestamp().toString() + " " + text + "\n";
         out_.write(str.getBytes("UTF-8"));
         out_.flush();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void createReportFile() throws Exception {
      String reportFileName = "OneToThree-report-" + serverScopeOne.getNodeId() + ".log";
      to_file = new File(reportFileName);
      if (to_file.getParent() != null) {
         to_file.getParentFile().mkdirs();
      }
      final FileOutputStream out = new FileOutputStream(to_file);
      out_ = out;
      out_.write(("XmlBlaster " + new Timestamp().toString()).getBytes());
      out_.write(("\n" + XmlBlasterException.createVersionInfo() + "\n").getBytes());

      log.info("Report file is '" + to_file.getAbsolutePath() + "'");
      System.out.println("Report file is '" + to_file.getAbsolutePath() + "'");
   }

   public void closeReportFile() {
      if (this.stopWatch == null)
         this.stopWatch = new StopWatch();
      this.stopWatch.stop();
      int sec = (int) (stopWatch.elapsed() / 1000);
      if (sec < 1)
         sec = 1;
      int avg = totalProcessed / sec;
      String str = "Total analyzed=" + numAnalysed + ", total processed=" + totalProcessed + " " + stopWatch.nice()
            + " average=" + avg + " messages/sec\n"
            + "See reporting in '" + to_file.getAbsolutePath() + "'";
      try {
         logToFile(str);
         out_.close();
      } catch (IOException e) {
         e.printStackTrace();
      }
      System.out.println(str);
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
      String queueCfg = serverScopeThree.getProperty().get("QueuePlugin[" + confType + "][" + confVersion + "]",
            (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      XBDatabaseAccessor accessorThree = XBDatabaseAccessor.createInstance(serverScopeThree, confType, confVersion,
            queueProps);
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
      logToFile("one-cluster.node.id=" + serverScopeOne.getNodeId() + " three-cluster.node.id="
            + serverScopeThree.getNodeId());
      boolean interactive = globalOne.getProperty().get("interactive", true);
      if (interactive) {
         int ret = Global.waitOnKeyboardHit("Do you really want to destroy XBSTORE/XBREF/XBMEAT ? <y>");
         if (ret != 'y') {
            System.err.println("Aborted, no data transferred.");
            System.exit(-1);
         }
      }
      String confType = "JDBC";
      String confVersion = "1.0";
      String queueCfg = serverScopeThree.getProperty().get("QueuePlugin[" + confType + "][" + confVersion + "]",
            (String) null);
      Properties queueProps = parsePropertyValue(queueCfg);
      XBDatabaseAccessor.wipeOutDB(serverScopeThree, confType, confVersion, queueProps, true);
   }

   public XBStore getXBStore(XBDatabaseAccessor accessor, Global glob, StorageId oldStorageId)
         throws XmlBlasterException {
      String key = oldStorageId.getId(); // "connection:connection_clientpubisherToHeron2"
      XBStore store = (XBStore) xbStoreMap.get(key);
      if (store == null) {
         // oldQueueuName = callback:callback_nodeheronclientsubscriber71
         // prefix: callback
         // postfix: callback_nodeheronclientsubscriber71
         // xbnode: heron
         StorageId uniqueQueueId = new StorageId(glob, oldStorageId.getXBStore().getNode(), oldStorageId.getXBStore()
               .getType(), oldStorageId.getXBStore().getPostfix());
         store = accessor.getXBStore(uniqueQueueId);
         xbStoreMap.put(key, store);
      }
      return store;
   }

   // java -Xms18M -Xmx1064M org.xmlBlaster.contrib.dbupdate.OneToThree
   // -cluster.node.id heron -interactive false
   public static void main(String[] args) {
      OneToThree ott = null;
      try {
         ott = new OneToThree(new ServerScope(args), new ServerScope(args), new Global(args), new Global(args));
         ott.createReportFile();
         ott.wipeOutThree();
         ott.transformServerScope();
         ott.transformClientSide();
         ott.closeReportFile();
      } catch (Exception e) {
         e.printStackTrace();
         if (ott != null)
            ott.closeReportFile();
      }
   }
}
