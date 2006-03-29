/*------------------------------------------------------------------------------
Name:      Publisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filepoller;

import java.util.Set;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.qos.ConnectQosData;


/**
 * Publisher
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class Publisher implements I_Timeout {

   private String ME = "Publisher";
   private Global global;
   private static Logger log = Logger.getLogger(Publisher.class.getName());
   private DirectoryManager directoryManager;
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
   
   public static final String USE_REGEX = "regex";

   private Timestamp timeoutHandle;
   private static Timeout timeout = new Timeout("FileSystem-Poller");
   
   /** used to identify if it has shut down (to get a new global) */
   private boolean isShutdown;
   /** used to break the loop in doPublish when shutting down */
   private boolean forceShutdown;
   
   /** only used as a default login name and logging */
   private String name;
   
   private boolean isActive;
   
   
   // private I_PluginConfig pluginConfig;
   
   public Publisher(Global globOrig, String name, I_PluginConfig pluginConfig) throws XmlBlasterException {
      ME += "-" + name;
      this.name = name;
      this.isShutdown = false;
      this.global = globOrig.getClone(globOrig.getNativeConnectArgs()); // sets session.timeout to 0 etc.
      // this.pluginConfig = pluginConfig;

      if (log.isLoggable(Level.FINER)) 
         log.finer("constructor");
      // retrieve all necessary properties:
      String tmp = null;
      tmp = this.global.get("publishKey", (String)null, null, pluginConfig);
      String topicName =  this.global.get("topicName", (String)null, null, pluginConfig);
      if (tmp != null) {
         // this.publishKey = new PublishKey(this.global, new MsgKeyData(this.global, tmp));
         this.publishKey = tmp;
         if (topicName != null)
            log.warning("constructor: since 'publishKey' is defined, 'topicName' will be ignored");
      }
      else {
         if (topicName == null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "at least one of the properties 'topicName' or 'publishKey' must be defined");
         this.publishKey = (new PublishKey(this.global, topicName)).toXml(); 
      }
      
      this.publishQos = this.global.get("publishQos", "<qos/>", null, pluginConfig);

      tmp  = this.global.get("connectQos", (String)null, null, pluginConfig);
      if (tmp != null) {
         ConnectQosData data = this.global.getConnectQosFactory().readObject(tmp);
         this.connectQos = new ConnectQos(this.global, data);
      }
      else {
         String userId = this.global.get("loginName", "_" + this.name, null, pluginConfig);
         String password = this.global.get("password", (String)null, null, pluginConfig);
         this.connectQos = new ConnectQos(this.global, userId, password);
         this.global.addObjectEntry("ServerNodeScope", globOrig.getObjectEntry("ServerNodeScope"));
      }

      this.fileFilter =  this.global.get("fileFilter", (String)null, null, pluginConfig);
      this.directoryName = this.global.get("directoryName", (String)null, null, pluginConfig);
      if (directoryName == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "constructor: 'directoryName' is mandatory");
      
      this.maximumFileSize = this.global.get("maximumFileSize", 10000000L, null, pluginConfig);
      this.delaySinceLastFileChange = this.global.get("delaySinceLastFileChange", 10000L, null, pluginConfig);
      this.pollInterval = this.global.get("pollInterval", 2000L, null, pluginConfig);

      this.sent =  this.global.get("sent", (String)null, null, pluginConfig);
      this.discarded =  this.global.get("discarded", (String)null, null, pluginConfig);
      this.lockExtention =  this.global.get("lockExtention", (String)null, null, pluginConfig);
     
      // this would throw an exception and act as a validation if something is not OK in configuration
      new MsgUnit(this.publishKey, (byte[])null, this.publishQos);
      this.filterType = this.global.get("filterType", "simple", null, pluginConfig);
      this.copyOnMove = this.global.get("copyOnMove", false, null, pluginConfig);
      
      createDirectoryManager();
   }
   
   /**
    * Create the file checker instance with the current configuration. 
    * @throws XmlBlasterException
    */
   private void createDirectoryManager() throws XmlBlasterException {
      boolean isTrueRegex = USE_REGEX.equalsIgnoreCase(filterType);
      this.directoryManager = new DirectoryManager(this.global,
            this.name, this.directoryName, this.delaySinceLastFileChange, 
            this.fileFilter, this.sent, this.discarded, this.lockExtention, isTrueRegex,
            this.copyOnMove);
   }

   /**
    * Useful for JMX invocations
    */
   private void reCreateDirectoryManager() {
      try {
         createDirectoryManager();
      } catch (XmlBlasterException e) {
         throw new IllegalArgumentException(e.getMessage());
      }
   }

   public String toString() {
      return "FilePoller " + this.filterType + " directoryName=" + this.directoryName + " fileFilter='" + this.fileFilter + "'";
   }
   
   /**
    * Connects to the xmlBlaster.
    * 
    * @throws XmlBlasterException
    */
   public synchronized void init() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("init");
      if (this.isShutdown) { // on a second init
         this.global = this.global.getClone(null);
      }
      this.isShutdown = false;
      this.forceShutdown = false;
      this.access = this.global.getXmlBlasterAccess();
      // no callback listener (we are not subscribing and don't want ptp)
      this.access.connect(this.connectQos, null);
      this.isActive = true;
      if (this.pollInterval >= 0)
         this.timeoutHandle = timeout.addTimeoutListener(this, this.pollInterval, null);
   }
   
   /**
    * If an exception occurs it means it could not publish the entry
    * @throws XmlBlasterException
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("shutdown");
      timeout.removeTimeoutListener(this.timeoutHandle);
      this.isActive = false;
      this.forceShutdown = true; // in case doPublish is looping due to an exception
      synchronized (this) {
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
            doPublish();
            break;
         }
         catch (XmlBlasterException ex) {
            log.severe("publish: exception " + ex.getMessage());
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
   
   /**
    * Publish file to xmlBlaster. 
    * @return An empty string if nothing was sent, is never null
    * @throws XmlBlasterException
    */
   private FileInfo[] doPublish() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) 
         log.finer("doPublish");
      Set entries = this.directoryManager.getEntries();
      if (entries == null || entries.size() < 1)
         return new FileInfo[0];
      FileInfo[] infos = (FileInfo[])entries.toArray(new FileInfo[entries.size()]);
      for (int i=0; i < infos.length; i++) {
         if (this.maximumFileSize <= 0L || infos[i].getSize() <= this.maximumFileSize) {
            if (infos[i].getSize() > Integer.MAX_VALUE) {
               log.severe("doPublish: sizes bigger than '" + Integer.MAX_VALUE + "' are currently not implemented");
            }
            else {
               byte[] content = this.directoryManager.getContent(infos[i]);
               MsgUnit msgUnit = new MsgUnit(this.publishKey, content, this.publishQos);
               msgUnit.getQosData().addClientProperty("_fileName", infos[i].getRelativeName());
               msgUnit.getQosData().addClientProperty("_fileDate", infos[i].getTimestamp());
               this.access.publish(msgUnit);
            }

            while (true) { // must repeat until it works or until shut down
               try {
                  boolean success = true;
                  this.directoryManager.deleteOrMoveEntry(infos[i].getName(), success);
                  break;
               }
               catch (XmlBlasterException ex) {
                  log.severe("Moving " + infos[i].getName() + " failed, we try again without further publishing (please fix manually): " + ex.getMessage());
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
            log.warning("doPublish: the file '" + infos[i].getName() + "' is too long (" + infos[i].getSize() + "'): I will remove it without publishing");
            boolean success = false;
            try {
               this.directoryManager.deleteOrMoveEntry(infos[i].getName(), success);
            }
            catch (XmlBlasterException ex) {
               log.warning("doPublish: could not handle file '" + infos[i].getName() + "' which was too big: check file and directories permissions and fix it manually: I will continue working anyway. " + ex.getMessage());
            }
         }
      }
      return infos;
   }
   
   /**
    * @see org.xmlBlaster.util.I_Timeout#timeout(java.lang.Object)
    */
   public void timeout(Object userData) {
      try {
         if (log.isLoggable(Level.FINER)) 
            log.finer("timeout");
         publish();
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         log.severe("timeout: " + ex.getMessage());
      }
      finally {
         if (this.pollInterval >= 0)
            this.timeoutHandle = timeout.addTimeoutListener(this, this.pollInterval, null);
      }
   }

   public void activate() throws Exception {
      if (!this.isActive) {
         this.isActive = true;
         if (this.pollInterval >= 0)
            this.timeoutHandle = timeout.addTimeoutListener(this, this.pollInterval, null);
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
         for (int i=0; i<2; i++) {
            FileInfo[] infos = doPublish();
            if (infos.length > 0) {
               return "Published matching files '" + toString(infos, 10) + "'";
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
      reCreateDirectoryManager();
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
      reCreateDirectoryManager();
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
      reCreateDirectoryManager();
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
      this.pollInterval = pollInterval;
      if (this.pollInterval < 0)
         deActivate();
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
      reCreateDirectoryManager();
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
      reCreateDirectoryManager();
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
      reCreateDirectoryManager();
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
      reCreateDirectoryManager();
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
      reCreateDirectoryManager();
   }
}
