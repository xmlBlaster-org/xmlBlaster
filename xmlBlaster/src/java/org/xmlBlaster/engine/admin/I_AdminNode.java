/*------------------------------------------------------------------------------
Name:      I_AdminNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares all available methods of an xmlBlaster server instance for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by RequestBroker.java, delivering the meat.
 * @author ruff@swand.lake.de
 * @since 0.79f
 */
public interface I_AdminNode {
   public int getRunlevel();
   public void setRunlevel(int level) throws XmlBlasterException;

   public long getUptime();
   public long getFreeMem();
   public long getTotalMem();
   public long getUsedMem();
   public String getHostname();
   public int getPort();
   public int getNumClients();
   public int getMaxClients();
   public String getClientList();
   public int getNumSysprop();
   public String getSyspropList();
   public int getNumMsgs();
   public String getMsgList();

   //public void setMaxClients(int maxClients);
}
