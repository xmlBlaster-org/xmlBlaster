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
   /**
    * Dump the complete internal state of xmlBlaster. 
    * Is an operation to not do it automatically on JMX load
    */
   public String dump() throws XmlBlasterException;
   public void setDump(String fn) throws XmlBlasterException;

   public String getRunlevel();
   public void setRunlevel(String level) throws XmlBlasterException;
   /**
    * Returns the current server time in milliseconds. 
    * For an accuracy discussion please consult {@link System.currentTimeMillis}
    * @return For example 1111400317333
    */
   public long getServerTimestampMillis();
   /**
    * Access the current server time as a java.sql.Timestamp string. 
    * @return For example "2005-03-21 11:18:12.622"
    */
   public String getServerTimestamp();
   public long getUptime();
   public long getFreeMem();
   public long getTotalMem();
   public long getUsedMem();

   /* TODO: rename to gc(); as it is an operation */
   public String getGc();
   public void setGc(String dummy);

   /**
    * Shutdown xmlBlaster, exit value is '0'
    */
   public void exit() throws XmlBlasterException;
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
