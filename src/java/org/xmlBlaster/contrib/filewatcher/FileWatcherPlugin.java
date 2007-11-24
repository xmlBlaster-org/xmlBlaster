/*------------------------------------------------------------------------------
Name:      FileWatcherPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewatcher;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * FileWatcherPlugin polls on a directory in the file system for new files. If one new file
 * is found which meets the required specifications, its content is read and published.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/contrib.filewatcher.html">The
 *      contrib.filewatcher requirement</a>
 */
public class FileWatcherPlugin extends GlobalInfo implements FileWatcherPluginMBean {
   private static Logger log = Logger.getLogger(FileWatcherPlugin.class.getName());
   private String ME = "FileWatcherPlugin";
   //private PluginInfo info;
   private Publisher publisherClient;
   /** My JMX registration */
   private Object mbeanHandle;
   private ContextNode contextNode;
   // private Global glob;
   // private PluginInfo pluginConfig;
   private boolean isShutdown;
   
   public FileWatcherPlugin() {
      super(new String[] {"mom.topicName", 
                           "filewatcher.directoryName", 
                           "mom.topicName", 
                           "mom.publishKey", 
                           "mom.publishQos", 
                           "mom.connectQos", 
                           "mom.loginName", 
                           "mom.password", 
                           "filewatcher.pollIntervalL", 
                           "filewatcher.delaySinceLastFileChange", 
                           "filewatcher.maximumFileSize", 
                           "filewatcher.maximumChunkSize", 
                           "filewatcher.fileFilter", 
                           "filewatcher.lockExtention", 
                           "filewatcher.sent", 
                           "filewatcher.discarded", 
                           "filewatcher.filterType", 
                           "filewatcher.copyOnMove",
                           ReplicationConstants.REPL_PREFIX_KEY,
                           ReplicationConstants.REPL_PREFIX_GROUP_KEY,
                           ReplicationConstants.REPLICATION_VERSION});
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void doInit(Global global_, PluginInfo pluginInfo_) throws XmlBlasterException {
      // this.glob = global;
      // this.pluginConfig = pluginInfo_;
      this.ME += "-" + getType();
      if (log.isLoggable(Level.FINER))
         log.finer(ME+"init");
      this.publisherClient = new Publisher(global, this.getType(), this);
      this.publisherClient.init();
      if (log.isLoggable(Level.FINEST)) {
         log.finest(ME+": plugin paramenters: '" + pluginInfo.dumpPluginParameters() + "'");
         log.finest(ME+": plugin user data  : '" + pluginInfo.getUserData() + "'");
      }
      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "FileWatcherPlugin[" + getType() + "]", global.getScopeContextNode());
      this.mbeanHandle = global.registerMBean(this.contextNode, this);
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      if (global != null && this.mbeanHandle != null)
         global.unregisterMBean(this.mbeanHandle);

      this.publisherClient.shutdown();
      this.isShutdown = true;
      log.fine(ME+": shutdown done");
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#triggerScan()
    */
   public String triggerScan() {
      log.info(ME+": invoking a scan of harddisk");
      return this.publisherClient.triggerScan();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getDirectoryName()
    */
   public String getDirectoryName() {
      return this.publisherClient.getDirectoryName();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setDirectoryName(java.lang.String)
    */
   public void setDirectoryName(String directoryName) {
      log.info(ME+": changing directory name to " + directoryName);
      this.publisherClient.setDirectoryName(directoryName);
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getFileFilter()
    */
   public String getFileFilter() {
      return this.publisherClient.getFileFilter();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setFileFilter(java.lang.String)
    */
   public void setFileFilter(String fileFilter) {
      log.info(ME+": changing file filter to " + fileFilter);
      this.publisherClient.setFileFilter(fileFilter);
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getFilterType()
    */
   public String getFilterType() {
      return this.publisherClient.getFilterType();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setFilterType(java.lang.String)
    */
   public void setFilterType(String filterType) {
      log.info(ME+": changing filter type to " + filterType);
      this.publisherClient.setFilterType(filterType);
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getMaximumFileSize()
    */
   public long getMaximumFileSize() {
      return this.publisherClient.getMaximumFileSize();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setMaximumFileSize(long)
    */
   public void setMaximumFileSize(long maximumFileSize) {
      log.info(ME+": changing max file size in bytes to " + maximumFileSize);
      this.publisherClient.setMaximumFileSize(maximumFileSize);
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getPollInterval()
    */
   public long getPollInterval() {
      return this.publisherClient.getPollInterval();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setPollInterval(long)
    */
   public void setPollInterval(long pollInterval) {
      log.info(ME+": changing pollInterval to " + pollInterval);
      this.publisherClient.setPollInterval(pollInterval);
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminService#activate()
    */
   public void activate() throws Exception {
      log.info(ME+": calling activate()");
      this.publisherClient.activate();
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminService#deActivate()
    */
   public void deActivate() {
      log.info(ME+": calling deActivate()");
      this.publisherClient.deActivate();
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminService#isActive()
    */
   public boolean isActive() {
      return this.publisherClient.isActive();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#isCopyOnMove()
    */
   public boolean isCopyOnMove() {
      return this.publisherClient.isCopyOnMove();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setCopyOnMove(boolean)
    */
   public void setCopyOnMove(boolean copyOnMove) {
      log.info(ME+": changing copyOnMove to " + copyOnMove);
      this.publisherClient.setCopyOnMove(copyOnMove);      
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getDelaySinceLastFileChange()
    */
   public long getDelaySinceLastFileChange() {
      return this.publisherClient.getDelaySinceLastFileChange();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setDelaySinceLastFileChange(long)
    */
   public void setDelaySinceLastFileChange(long delaySinceLastFileChange) {
      log.info(ME+": changing delaySinceLastFileChange to " + delaySinceLastFileChange);
      this.publisherClient.setDelaySinceLastFileChange(delaySinceLastFileChange);      
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getDiscarded()
    */
   public String getDiscarded() {
      return this.publisherClient.getDiscarded();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setDiscarded(java.lang.String)
    */
   public void setDiscarded(String discarded) {
      log.info(ME+": changing discarded to " + discarded);
      this.publisherClient.setDiscarded(discarded);      
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getLockExtention()
    */
   public String getLockExtention() {
      return this.publisherClient.getLockExtention();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setLockExtention(java.lang.String)
    */
   public void setLockExtention(String lockExtention) {
      log.info(ME+": changing lockExtention to " + lockExtention);
      this.publisherClient.setLockExtention(lockExtention);      
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#getSent()
    */
   public String getSent() {
      return this.publisherClient.getSent();
   }

   /**
    * @see org.xmlBlaster.contrib.filewatcher.FileWatcherPluginMBean#setSent(java.lang.String)
    */
   public void setSent(String sent) {
      log.info(ME+": changing sent to " + sent);
      this.publisherClient.setSent(sent);
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminPlugin#isShutdown()
    */
   public boolean isShutdown() {
      return this.isShutdown;
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminUsage#usage()
    */
   public String usage() {
      return "Polls filesystem for new incoming files"
      + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminUsage#getUsageUrl()
    */
   public String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /**
    * @see org.xmlBlaster.util.admin.I_AdminUsage#setUsageUrl(java.lang.String)
    */
   public void setUsageUrl(String url) {
   }
}
