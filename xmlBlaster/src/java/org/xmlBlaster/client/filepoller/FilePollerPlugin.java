/*------------------------------------------------------------------------------
Name:      FilePollerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filepoller;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * FilePollerPlugin polls on a directory in the file system for new files. If one new file
 * is found which meets the required specifications, its content is read and published.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.filepoller.html">The
 *      client.filepoller requirement</a>
 */
public class FilePollerPlugin implements I_Plugin, FilePollerPluginMBean {
   private final static String ME = "FilePollerPlugin";
   //private PluginInfo info;
   private Publisher publisherClient;
   /** My JMX registration */
   private Object mbeanHandle;
   private ContextNode contextNode;
   private Global glob;
   private PluginInfo pluginConfig;
   private boolean isShutdown;
   
   public FilePollerPlugin() {
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
      LogChannel log = global.getLog("filepoller");
      if (log.CALL)
         log.call(ME, "init");
      this.glob = global;
      this.pluginConfig = pluginInfo;
      this.publisherClient = new Publisher(global, this.getType(), this.pluginConfig);
      this.publisherClient.init();
      if (log.DUMP) {
         log.dump(ME, "init: plugin paramenters: '" + this.pluginConfig.dumpPluginParameters() + "'");
         log.dump(ME, "init: plugin user data  : '" + this.pluginConfig.getUserData() + "'");
      }
      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "FilePollerPlugin[" + getType() + "]", global.getScopeContextNode());
      this.mbeanHandle = global.registerMBean(this.contextNode, this);
   }

   /**
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.pluginConfig != null)
         return this.pluginConfig.getType();
      return ME;
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.pluginConfig != null)
         return this.pluginConfig.getVersion();
      return "1.0";
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      if (this.glob != null && this.mbeanHandle != null)
         this.glob.unregisterMBean(this.mbeanHandle);

      this.publisherClient.shutdown();
      this.isShutdown = true;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#triggerScan()
    */
   public String triggerScan() {
      return this.publisherClient.triggerScan();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getDirectoryName()
    */
   public String getDirectoryName() {
      return this.publisherClient.getDirectoryName();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setDirectoryName(java.lang.String)
    */
   public void setDirectoryName(String directoryName) {
      this.publisherClient.setDirectoryName(directoryName);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getFileFilter()
    */
   public String getFileFilter() {
      return this.publisherClient.getFileFilter();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setFileFilter(java.lang.String)
    */
   public void setFileFilter(String fileFilter) {
      this.publisherClient.setFileFilter(fileFilter);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getFilterType()
    */
   public String getFilterType() {
      return this.publisherClient.getFilterType();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setFilterType(java.lang.String)
    */
   public void setFilterType(String filterType) {
      this.publisherClient.setFilterType(filterType);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getMaximumFileSize()
    */
   public long getMaximumFileSize() {
      return this.publisherClient.getMaximumFileSize();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setMaximumFileSize(long)
    */
   public void setMaximumFileSize(long maximumFileSize) {
      this.publisherClient.setMaximumFileSize(maximumFileSize);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getPollInterval()
    */
   public long getPollInterval() {
      return this.publisherClient.getPollInterval();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setPollInterval(long)
    */
   public void setPollInterval(long pollInterval) {
      this.publisherClient.setPollInterval(pollInterval);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#activate()
    */
   public void activate() throws Exception {
      this.publisherClient.activate();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#deActivate()
    */
   public void deActivate() {
      this.publisherClient.deActivate();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#isActive()
    */
   public boolean isActive() {
      return this.publisherClient.isActive();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#isCopyOnMove()
    */
   public boolean isCopyOnMove() {
      return this.publisherClient.isCopyOnMove();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setCopyOnMove(boolean)
    */
   public void setCopyOnMove(boolean copyOnMove) {
      this.publisherClient.setCopyOnMove(copyOnMove);      
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getDelaySinceLastFileChange()
    */
   public long getDelaySinceLastFileChange() {
      return this.publisherClient.getDelaySinceLastFileChange();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setDelaySinceLastFileChange(long)
    */
   public void setDelaySinceLastFileChange(long delaySinceLastFileChange) {
      this.publisherClient.setDelaySinceLastFileChange(delaySinceLastFileChange);      
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getDiscarded()
    */
   public String getDiscarded() {
      return this.publisherClient.getDiscarded();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setDiscarded(java.lang.String)
    */
   public void setDiscarded(String discarded) {
      this.publisherClient.setDiscarded(discarded);      
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getLockExtention()
    */
   public String getLockExtention() {
      return this.publisherClient.getLockExtention();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setLockExtention(java.lang.String)
    */
   public void setLockExtention(String lockExtention) {
      this.publisherClient.setLockExtention(lockExtention);      
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#getSent()
    */
   public String getSent() {
      return this.publisherClient.getSent();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.filepoller.FilePollerPluginMBean#setSent(java.lang.String)
    */
   public void setSent(String sent) {
      this.publisherClient.setSent(sent);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminPlugin#isShutdown()
    */
   public boolean isShutdown() {
      return this.isShutdown;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#usage()
    */
   public String usage() {
      return "Polls filesystem for new incoming files"
      + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#getUsageUrl()
    */
   public String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#setUsageUrl(java.lang.String)
    */
   public void setUsageUrl(String url) {
   }
}
