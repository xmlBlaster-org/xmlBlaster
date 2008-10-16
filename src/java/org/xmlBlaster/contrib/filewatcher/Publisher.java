/*------------------------------------------------------------------------------
Name:      Publisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewatcher;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.contrib.ContribConstants;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher;
import org.xmlBlaster.contrib.replication.I_ReplSource;
import org.xmlBlaster.contrib.replication.ReplSourceEngine;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.jms.XBConnectionMetaData;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.MsgQosData;


/**
 * Publisher
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class Publisher implements I_Timeout {

   private String ME = "Publisher";
   private Global global;
   private static Logger log = Logger.getLogger(Publisher.class.getName());
   private DirectoryManager[] directoryManagers;
   private I_XmlBlasterAccess access;
   private String publishKey;
   private String publishQos;
   private ConnectQos connectQos;

   private long pollInterval;
   private long maximumFileSize;
   private String fileFilter;
   private String filterType;
   private String directoryName;
   private boolean copyOnMove;
   private String sent;
   private String discarded;
   private String lockExtention;
   private long delaySinceLastFileChange;
   private boolean recursive;
   
   public static final String USE_REGEX = "regex";

   private Timestamp timeoutHandle;
   private static Timeout timeout = new Timeout("FileSystem-Watcher");
   
   /** used to identify if it has shut down (to get a new global) */
   private boolean isShutdown;
   /** used to break the loop in doPublish when shutting down */
   private boolean forceShutdown;
   
   /** only used as a default login name and logging */
   private String name;
   
   private boolean isActive;
   private int maximumChunkSize = Integer.MAX_VALUE;
   
   // Used for monitoring and to use this as a source for replication
   private String replPrefix;
   // private String replPrefixGroup;
   // private String replVersion;
   private I_Info info_;
   private ReplSource replSource;
   private ReplSourceEngine replSourceEngine;
   private XmlBlasterPublisher publisher;
   
   public class ReplSourceData {
      
      private final String replTopic;
      private final String replManagerAddress;
      private final String requestedVersion;
      private final String initialFilesLocation;
      
      public ReplSourceData(String topic, String address, String version, String location) {
         this.replTopic = topic;
         this.replManagerAddress = address;
         this.requestedVersion = version;
         this.initialFilesLocation = location;
      }

      public String getInitialFilesLocation() {
         return initialFilesLocation;
      }

      public String getReplManagerAddress() {
         return replManagerAddress;
      }

      public String getReplTopic() {
         return replTopic;
      }

      public String getRequestedVersion() {
         return requestedVersion;
      }
   }
   
   public class ReplSource implements I_ReplSource {
      
      private Map preparedUpdates = new HashMap();
      private boolean collectInitialUpdates;
      private ReplSourceEngine engine;
      private I_Info info;
      private String oid;
      
      public ReplSource(I_Info info, String oid) {
         this.info = info;
         this.oid = oid;
      }
      
      public void setEngine(ReplSourceEngine engine) {
         this.engine = engine;
      }

      public void cancelUpdate(String slaveName) {
         synchronized(preparedUpdates) {
            preparedUpdates.remove(slaveName);
         }
      }

      public void collectInitialUpdate() throws Exception {
         synchronized(preparedUpdates) {
            collectInitialUpdates = true;
            preparedUpdates.clear();
         }
      }

      public byte[] executeStatement(String sql, long maxResponseEntries, boolean isHighPrio, boolean isMaster, String sqlTopic, String statementId) throws Exception {
         return "".getBytes();
      }

      public void initialUpdate(String topic, String address, String slaveName, String version, String location, boolean onlyRegister) throws Exception {
         synchronized(preparedUpdates) {
            if (collectInitialUpdates || onlyRegister) {
               ReplSourceData data = new ReplSourceData(topic, address, version, location);
               preparedUpdates.put(slaveName, data);
            }
            else {
               String[] slaveSessionNames = new String[] { slaveName };
               long minKey = 0;
               long maxKey = 1;
               this.engine.sendInitialDataResponse(slaveSessionNames, address, minKey, maxKey);
               this.engine.sendEndOfTransitionMessage(info, topic, slaveSessionNames);
            }
         }
      }

      public void recreateTriggers() throws Exception {
      }

      public void startInitialUpdateBatch() throws Exception {
         synchronized(preparedUpdates) {
            if (preparedUpdates.size() > 0) {
               String[] slaveSessionNames = (String[])preparedUpdates.keySet().toArray(new String[preparedUpdates.size()]);
               ReplSourceData data = (ReplSourceData)preparedUpdates.get(slaveSessionNames[0]);
               String replManagerAddress = data.getReplManagerAddress();
               String initialDataTopic = data.getReplTopic();
               long minKey = 0;
               long maxKey = 1;
               this.engine.sendInitialDataResponse(slaveSessionNames, replManagerAddress, minKey, maxKey);
               this.engine.sendEndOfTransitionMessage(info, initialDataTopic, slaveSessionNames);
               preparedUpdates.clear();
               collectInitialUpdates = false;
            }
         }
      }

      public String getTopic() {
         return oid;
      }

   }
   
   
   public Publisher(Global globOrig, String name, I_Info info) throws XmlBlasterException {
      ME += "-" + name;
      this.name = name;
      this.isShutdown = false;
      // This should already be done by the GlobalInfo
      // this.global = globOrig.getClone(globOrig.getNativeConnectArgs()); 
      this.global = globOrig.getClone(null); // sets session.timeout to 0 etc.
      // this.global = globOrig;
      // this.pluginConfig = pluginConfig;
      this.info_ = info;
      if (log.isLoggable(Level.FINER)) 
         log.finer(ME+": constructor");
      // retrieve all necessary properties:
      String tmp = null;
      tmp = info_.get("mom.publishKey", null);
      String topicName =  info_.get("mom.topicName", null);
      replPrefix = info_.get(ReplicationConstants.REPL_PREFIX_KEY, null);

      if (tmp != null) {
         this.publishKey = tmp;
         if (topicName != null)
            log.warning(ME+": constructor: since 'mom.publishKey' is defined, 'topicName' will be ignored");
      }
      else {
         if (topicName == null) {
            if (replPrefix == null)
               throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "at least one of the properties 'topicName', 'publishKey' or 'replication.prefix' must be defined");
            else
               topicName = "topic." + replPrefix.trim();
         }
         this.publishKey = (new PublishKey(this.global, topicName)).toXml(); 
      }
      
      this.publishQos = info_.get("mom.publishQos", "<qos/>");

      tmp  = info_.get("mom.connectQos", null);
      if (tmp != null) {
         ConnectQosData data = this.global.getConnectQosFactory().readObject(tmp);
         this.connectQos = new ConnectQos(this.global, data);
      }
      else {
         String userId = info_.get("mom.loginName", "_" + name);
         String password = info_.get("mom.password", null);
         this.connectQos = new ConnectQos(this.global, userId, password);
         global.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, globOrig.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope));
      }

      this.fileFilter =  info_.get("filewatcher.fileFilter", null);
      this.directoryName = info_.get("filewatcher.directoryName", null);
      if (directoryName == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "constructor: 'filewatcher.directoryName' is mandatory");
      
      this.maximumFileSize = info_.getLong("filewatcher.maximumFileSize", 10000000L);
      this.maximumChunkSize = info_.getInt("filewatcher.maximumChunkSize", Integer.MAX_VALUE);

      delaySinceLastFileChange = info_.getLong("filewatcher.delaySinceLastFileChange", 10000L);
      pollInterval = info_.getLong("filewatcher.pollInterval", 2000L);

      sent =  info_.get("filewatcher.sent", null);
      discarded =  info_.get("filewatcher.discarded", null);
      lockExtention =  info_.get("filewatcher.lockExtention", null);
     
      // this would throw an exception and act as a validation if something is not OK in configuration
      new MsgUnit(this.publishKey, (byte[])null, this.publishQos);
      this.filterType = info_.get("filewatcher.filterType", "simple");
      this.copyOnMove = info_.getBoolean("filewatcher.copyOnMove", true);

      // replPrefixGroup = info_.get(ReplicationConstants.REPL_PREFIX_GROUP_KEY, null);
      // replVersion = info_.get(ReplicationConstants.REPLICATION_VERSION, null);
      if (replPrefix != null) {
         String replVersion = info_.get(ReplicationConstants.REPLICATION_VERSION, null);
         if (replVersion == null)
            info_.put(ReplicationConstants.REPLICATION_VERSION, "1.0");
         String tmpKey = "replication.countSingleMsg";
         String tmpVal = info_.get(tmpKey, null);
         if (tmpVal == null)
            info_.put(tmpKey, "true");
         MsgKeyData key = global.getMsgKeyFactory().readObject(publishKey);
         String oid = key.getOid();
         info.put("mom.topicName", oid); // this must ALWAYS be set if using replication
         prepareReplSource(replPrefix != null);
      }
      this.recursive = info_.getBoolean("filewatcher.recursive", false);
      createDirectoryManagers();
   }
   
   private void prepareReplSource(boolean doFill) throws XmlBlasterException {
      if (!doFill)
         return;

      Set set = info_.getKeys();
      String[] keys = (String[])set.toArray(new String[set.size()]);
      for (int i=0; i < keys.length; i++)
         connectQos.addClientProperty(keys[i], info_.get(keys[i], null));
      publisher = new XmlBlasterPublisher();
      String oid = null;
      if (publishKey != null) {
         MsgKeyData key = global.getMsgKeyFactory().readObject(publishKey);
         oid = key.getOid();
      }
      publisher.initWithExistingGlob(global, publishKey, publishQos, 0);
      replSource = new ReplSource(info_, oid);
      replSourceEngine = new ReplSourceEngine(replPrefix, publisher, replSource);
      replSource.setEngine(replSourceEngine);
      Map attrs = new HashMap();
      attrs.put("ptp", "true");
      try {
         publisher.registerAlertListener(replSourceEngine, attrs);
      }
      catch (Exception ex) {
         if (ex instanceof XmlBlasterException)
            throw (XmlBlasterException)ex;
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "Publisher.prepareReplSource", "occured when registering alert listener", ex);
      }
   }
   
   private String getSubDir(DirectoryManager root, String base, String subDir) {
      String ret = null;
      if (base == null)
         ret = subDir;
      else {
         if (subDir == null)
            ret = base;
         else {
            base = base.trim();
            if (base.charAt(base.length()-1) == '/')
               ret = base + subDir;
            else
               ret = base + "/" + subDir;
         }
      }
      File tmp = new File(ret);
      if (tmp.isAbsolute())
         return ret;
      else {
         return root.getDir().getAbsolutePath() + "/" + ret;
      }
   }
   
   private String[] getChildDirectories(boolean isRecursive, DirectoryManager rootDirManager) {
      if (!isRecursive)
         return null;
      if (rootDirManager == null)
         return null;
      return rootDirManager.getAllSubDirs();
   }
   
   /**
    * Create the file checker instance with the current configuration. 
    * @throws XmlBlasterException
    */
   private void createDirectoryManagers() throws XmlBlasterException {
      boolean isTrueRegex = USE_REGEX.equalsIgnoreCase(filterType);
      DirectoryManager rootDirManager = new DirectoryManager(global, name, directoryName, null,
            delaySinceLastFileChange, fileFilter, sent, discarded, lockExtention, isTrueRegex, copyOnMove);
      
      String[] dirNames = getChildDirectories(recursive, rootDirManager);
      if (dirNames != null)
         directoryManagers = new DirectoryManager[dirNames.length + 1];
      else
         directoryManagers = new DirectoryManager[1];
      directoryManagers[0] = rootDirManager;
      if (dirNames != null) {
         for (int i=0; i < dirNames.length; i++) {
            String sentDir = getSubDir(rootDirManager, sent, dirNames[i]);
            String discardedDir = getSubDir(rootDirManager, discarded, dirNames[i]);
            directoryManagers[i+1] = new DirectoryManager(global, name, directoryName, dirNames[i],  
                  delaySinceLastFileChange, fileFilter, sentDir, discardedDir, lockExtention, isTrueRegex, copyOnMove);
         }
      }
   }

   /**
    * Useful for JMX invocations
    */
   private synchronized void reCreateDirectoryManagers() {
      try {
         createDirectoryManagers();
      } catch (XmlBlasterException e) {
         throw new IllegalArgumentException(e.getMessage());
      }
   }

   public String toString() {
      return "FileWatcher " + this.filterType + " directoryName=" + this.directoryName + " fileFilter='" + this.fileFilter + "'";
   }
   
   /**
    * Connects to the xmlBlaster.
    * 
    * @throws XmlBlasterException
    */
   public synchronized void init() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer(ME+": init");
      if (this.isShutdown) { // on a second init
         this.global = this.global.getClone(null);
      }
      this.isShutdown = false;
      this.forceShutdown = false;
      this.access = this.global.getXmlBlasterAccess();
      
      this.access.connect(this.connectQos, publisher);
      this.isActive = true;
      activatePoller();
   }
   
   /**
    * If an exception occurs it means it could not publish the entry
    * @throws XmlBlasterException
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer(ME+": shutdown");
      timeout.removeTimeoutListener(this.timeoutHandle);
      this.isActive = false;
      this.forceShutdown = true; // in case doPublish is looping due to an exception
      synchronized (this) {
         if (replSource != null) {
            replSource.setEngine(null);
            replSource = null;
            replSourceEngine = null;
            publisher.shutdown();
            publisher = null;
         }
         this.isShutdown = false;
         this.access.disconnect(new DisconnectQos(this.global));
         this.global.shutdown();
      }
   }
   
   /**
    * Fail-safe sending files. 
    * @return Comman separated list of send file names
    */
   public synchronized void publish() {
      while (true) {
         try {
            DirectoryManager[] tmpDirs = directoryManagers;
            for (int i=0; i < tmpDirs.length; i++)
               doPublish(tmpDirs[i]);
            break;
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+": publish: exception " + ex.getMessage());
            try {
               Thread.sleep(this.pollInterval);
            }
            catch  (Exception e) {}      
         }
         if (this.forceShutdown)
            break;
      }
   }

   /**
    * Create a comma separated list of file names. 
    * @param infos
    * @param max Max file names to collect
    * @return
    */
   public String toString(FileInfo[] infos, int max) {
      StringBuffer sb = new StringBuffer();
      if (max <= 0) max = infos.length;
      if (max > infos.length) max = infos.length;
      for (int i=0; i<max; i++) {
         if (i>0) sb.append(",");
         sb.append(infos[i].getRelativeName());
      }
      return sb.toString();
   }
   
   private String preparePubQos(String origQos) throws XmlBlasterException {
      if (replSourceEngine == null || origQos == null)
         return origQos;
      
      MsgQosData msgQosData = global.getMsgQosFactory().readObject(origQos);
      MsgQosData preparedMsgQosData = replSourceEngine.preparePubQos(msgQosData);
      return preparedMsgQosData.toXml();
   }
   
   /**
    * Publish file or files to xmlBlaster. 
    * @return An empty string if nothing was sent, is never null
    * @throws XmlBlasterException
    */
   private FileInfo[] doPublish(DirectoryManager directoryManager) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer(ME+": doPublish");
      Set entries = directoryManager.getEntries();
      if (entries == null || entries.size() < 1)
         return new FileInfo[0];
      FileInfo[] infos = (FileInfo[])entries.toArray(new FileInfo[entries.size()]);
      for (int i=0; i < infos.length; i++) {
         if (this.maximumFileSize <= 0L || infos[i].getSize() <= this.maximumFileSize) {
            
            if (infos[i].getSize() > maximumChunkSize) {
               // log.warning("Not implemented yet: the size '" + infos[i].getSize() + "' is bigger than the maximum chunk size (" + maximumChunkSize + ")");
               InputStream is = directoryManager.getContentStream(infos[i]);
               Global glob = access.getGlobal();
               MsgKeyData keyData = glob.getMsgKeyFactory().readObject(publishKey);
               MsgQosData qosData = glob.getMsgQosFactory().readObject(publishQos);
               qosData.addClientProperty(ContribConstants.FILENAME_ATTR, infos[i].getRelativeName());
               qosData.addClientProperty(ContribConstants.FILE_DATE, infos[i].getTimestamp());
               qosData.addClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_MAX_CHUNK_SIZE, log), maximumChunkSize);
               String subDir = directoryManager.getSubDir();
               if (subDir != null)
                  qosData.addClientProperty(ContribConstants.SUBDIR_ATTR, subDir);
               access.publishStream(is, keyData, qosData, maximumChunkSize, replSourceEngine);
               if (log.isLoggable(Level.FINE)) 
                  log.fine(ME+": Successfully published file " + infos[i].getRelativeName() + " with size=" +infos[i].getSize());
            }
            else if (infos[i].getSize() > Integer.MAX_VALUE) {
               log.severe(ME+": doPublish: sizes bigger than '" + Integer.MAX_VALUE + "' are currently not implemented");
            }
            else {
               byte[] content = directoryManager.getContent(infos[i]);
               String pubQos = preparePubQos(this.publishQos);
               MsgUnit msgUnit = new MsgUnit(this.publishKey, content, pubQos);
               msgUnit.getQosData().addClientProperty(ContribConstants.FILENAME_ATTR, infos[i].getRelativeName());
               msgUnit.getQosData().addClientProperty(ContribConstants.FILE_DATE, infos[i].getTimestamp());
               String subDir = directoryManager.getSubDir();
               if (subDir != null)
                  msgUnit.getQosData().addClientProperty(ContribConstants.SUBDIR_ATTR, subDir);
               this.access.publish(msgUnit);
               if (log.isLoggable(Level.FINE)) 
                  log.fine(ME+": Successfully published file " + infos[i].getRelativeName() + " with size=" +infos[i].getSize());
            }

            while (true) { // must repeat until it works or until shut down
               try {
                  boolean success = true;
                  directoryManager.deleteOrMoveEntry(infos[i].getName(), success);
                  break;
               }
               catch (XmlBlasterException ex) {
                  log.severe(ME+": Moving " + infos[i].getName() + " failed, we try again without further publishing (please fix manually): " + ex.getMessage());
                  try {
                     Thread.sleep(this.pollInterval);
                  }
                  catch (Exception e){}
               }
               if (this.forceShutdown)
                  break;
            }
         }
         else { // delete or move to 'discarded'
            log.warning(ME+": doPublish: the file '" + infos[i].getName() + "' is too long (" + infos[i].getSize() + "'): I will remove it without publishing");
            boolean success = false;
            try {
               directoryManager.deleteOrMoveEntry(infos[i].getName(), success);
            }
            catch (XmlBlasterException ex) {
               log.warning(ME+": doPublish: could not handle file '" + infos[i].getName() + "' which was too big: check file and directories permissions and fix it manually: I will continue working anyway. " + ex.getMessage());
            }
         }
      }
      return infos;
   }
   
   private void activatePoller() {
      if (this.pollInterval >= 0)
         this.timeoutHandle = timeout.addTimeoutListener(this, this.pollInterval, null);
   }
   
   /**
    * @see org.xmlBlaster.util.I_Timeout#timeout(java.lang.Object)
    */
   public void timeout(Object userData) {
      try {
         if (log.isLoggable(Level.FINER)) 
            log.finer(ME+": timeout");
         publish();
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         log.severe(ME+": timeout: " + ex.getMessage());
      }
      finally {
         activatePoller();
      }
   }

   public void activate() throws Exception {
      if (!this.isActive) {
         this.isActive = true;
         activatePoller();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#deActivate()
    */
   public void deActivate() {
      timeout.removeTimeoutListener(this.timeoutHandle);
      this.timeoutHandle = null;
      this.isActive = false;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#isActive()
    */
   public boolean isActive() {
      return this.isActive;
   }
   
   public String triggerScan() {
      try {
         //this.timeoutHandle = timeout.addTimeoutListener(this, 0, null);
         // Hack: I need to call it twice to be effective, why? (Marcel 2006-01)
         for (int i=0; i < 2; i++) {
            DirectoryManager[] tmpDirs = directoryManagers;
            for (int j=0; j < tmpDirs.length; j++) {
               FileInfo[] infos = doPublish(tmpDirs[j]);
               if (infos.length > 0) {
                  return "Published matching files '" + toString(infos, 10) + "'";
               }
            }
         }
         if (this.delaySinceLastFileChange > 0)
            return "No matching file found to publish, note that it may take delaySinceLastFileChange=" + this.delaySinceLastFileChange + " millis until the file is sent.";
         else
            return "No matching file found to publish.";
      } catch (XmlBlasterException e) {
         throw new IllegalArgumentException(e.getMessage());
      }
   }

   /**
    * @return Returns the directoryName.
    */
   public String getDirectoryName() {
      return this.directoryName;
   }

   /**
    * @param directoryName The directoryName to set.
    */
   public void setDirectoryName(String directoryName) {
      this.directoryName = directoryName;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the fileFilter.
    */
   public String getFileFilter() {
      return this.fileFilter;
   }

   /**
    * @param fileFilter The fileFilter to set.
    */
   public void setFileFilter(String fileFilter) {
      this.fileFilter = fileFilter;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the filterType.
    */
   public String getFilterType() {
      return this.filterType;
   }

   /**
    * @param filterType The filterType to set.
    */
   public void setFilterType(String filterType) {
      this.filterType = filterType;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the maximumFileSize.
    */
   public long getMaximumFileSize() {
      return this.maximumFileSize;
   }

   /**
    * @param maximumFileSize The maximumFileSize to set.
    */
   public void setMaximumFileSize(long maximumFileSize) {
      this.maximumFileSize = maximumFileSize;
   }

   /**
    * @return Returns the pollInterval.
    */
   public long getPollInterval() {
      return this.pollInterval;
   }

   /**
    * @param pollInterval The pollInterval to set.
    */
   public void setPollInterval(long pollInterval) {
      deActivate();
      this.pollInterval = pollInterval;
      activatePoller();
   }

   /**
    * @return Returns the copyOnMove.
    */
   public boolean isCopyOnMove() {
      return this.copyOnMove;
   }

   /**
    * @param copyOnMove The copyOnMove to set.
    */
   public void setCopyOnMove(boolean copyOnMove) {
      this.copyOnMove = copyOnMove;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the delaySinceLastFileChange.
    */
   public long getDelaySinceLastFileChange() {
      return this.delaySinceLastFileChange;
   }

   /**
    * @param delaySinceLastFileChange The delaySinceLastFileChange to set.
    */
   public void setDelaySinceLastFileChange(long delaySinceLastFileChange) {
      this.delaySinceLastFileChange = delaySinceLastFileChange;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the discarded.
    */
   public String getDiscarded() {
      return this.discarded;
   }

   /**
    * @param discarded The discarded to set.
    */
   public void setDiscarded(String discarded) {
      this.discarded = discarded;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the lockExtention.
    */
   public String getLockExtention() {
      return this.lockExtention;
   }

   /**
    * @param lockExtention The lockExtention to set.
    */
   public void setLockExtention(String lockExtention) {
      this.lockExtention = lockExtention;
      reCreateDirectoryManagers();
   }

   /**
    * @return Returns the sent.
    */
   public String getSent() {
      return this.sent;
   }

   /**
    * @param sent The sent to set.
    */
   public void setSent(String sent) {
      this.sent = sent;
      reCreateDirectoryManagers();
   }
   
   public boolean isRecursive() {
      return recursive;
   }

   public void setRecursive(boolean rec) {
      if (recursive != rec)
         recursive = rec;
      reCreateDirectoryManagers();
   }
   
   
}
