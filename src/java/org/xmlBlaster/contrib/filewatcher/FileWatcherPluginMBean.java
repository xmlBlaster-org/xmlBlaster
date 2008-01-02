/*------------------------------------------------------------------------------
Name:      FileWatcherPluginMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.filewatcher;

import org.xmlBlaster.util.admin.I_AdminService;

/**
 * JMX control for the native FileWatcherPlugin.  
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface FileWatcherPluginMBean extends I_AdminService {
   /**
    * Triggers a filewatcher scan. 
    */
   public String triggerScan();
   
   /**
    * @return Returns the directoryName.
    */
   public String getDirectoryName();

   /**
    * @param directoryName The directoryName to set.
    */
   public void setDirectoryName(String directoryName);

   /**
    * @return Returns the fileFilter.
    */
   public String getFileFilter();

   /**
    * @param fileFilter The fileFilter to set.
    */
   public void setFileFilter(String fileFilter);

   /**
    * @return Returns the filterType.
    */
   public String getFilterType();

   /**
    * @param filterType The filterType to set.
    */
   public void setFilterType(String filterType);

   /**
    * @return Returns the maximumFileSize.
    */
   public long getMaximumFileSize();

   /**
    * @param maximumFileSize The maximumFileSize to set.
    */
   public void setMaximumFileSize(long maximumFileSize);

   /**
    * @return Returns the pollInterval.
    */
   public long getPollInterval();

   /**
    * @param pollInterval The pollInterval to set.
    */
   public void setPollInterval(long pollInterval);

   /**
    * @return Returns the copyOnMove.
    */
   public boolean isCopyOnMove();

   /**
    * @param copyOnMove The copyOnMove to set.
    */
   public void setCopyOnMove(boolean copyOnMove);

   /**
    * @return Returns the delaySinceLastFileChange.
    */
   public long getDelaySinceLastFileChange();

   /**
    * @param delaySinceLastFileChange The delaySinceLastFileChange to set.
    */
   public void setDelaySinceLastFileChange(long delaySinceLastFileChange);

   /**
    * @return Returns the discarded.
    */
   public String getDiscarded();

   /**
    * @param discarded The discarded to set.
    */
   public void setDiscarded(String discarded);

   /**
    * @return Returns the lockExtention.
    */
   public String getLockExtention();

   /**
    * @param lockExtention The lockExtention to set.
    */
   public void setLockExtention(String lockExtention);

   /**
    * @return Returns the sent.
    */
   public String getSent();

   /**
    * @param sent The sent to set.
    */
   public void setSent(String sent);
   
   void setRecursive(boolean rec);
   
   boolean isRecursive();
}
