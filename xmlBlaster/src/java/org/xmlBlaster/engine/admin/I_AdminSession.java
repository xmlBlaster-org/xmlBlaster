/*------------------------------------------------------------------------------
Name:      I_AdminSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

/**
 * Declares all available methods of a session for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SessionInfo.java, delivering the meat.
 * @author ruff@swand.lake.de
 * @since 0.79f
 */
public interface I_AdminSession {
   public long getUptime();
   public long getNumUpdates();
   public int getCbQueueNumMsgs();
   public int getCbQueueMaxMsgs();
}
