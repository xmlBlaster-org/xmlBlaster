/*------------------------------------------------------------------------------
Name:      I_AdminNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of an xmlBlaster server instance for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by RequestBroker.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminNode {
   public int getNumNodes();
   public String getNodeList();

   public String getNodeId();
   public String getVersion();
   public String getBuildTimestamp();
   public String getBuildJavaVendor();
   public String getBuildJavaVersion();
   public String getDump() throws XmlBlasterException;
   public void setDump(String fn) throws XmlBlasterException;

   public String getRunlevel();
   public void setRunlevel(String level) throws XmlBlasterException;

   public long getUptime();
   public long getFreeMem();
   public long getTotalMem();
   public long getUsedMem();

   public String getGc();
   public void setGc(String dummy);

   // public String getExit() throws XmlBlasterException;
   public void setExit(String exitValue) throws XmlBlasterException;

   public String getHostname();
   public int getPort();
   public int getNumClients();
   public int getMaxClients();
   public String getClientList();
   public int getNumSysprop();
   public String getSyspropList();
   public int getNumTopics();
   public String getTopicList();
   public int getNumSubscriptions();
   public String getSubscriptionList();

   //public void setMaxClients(int maxClients);
}
