/*------------------------------------------------------------------------------
Name:      I_AdminSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of a session for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SessionInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminSession {
   /** Uptime in seconds */
   public long getUptime();
   /** How many messages where sent to this clients login session */
   public long getNumUpdates();
   /** How many messages are in this clients session callback queue */
   public long getCbQueueNumMsgs();
   /** How many messages are max. allowed in this clients session callback queue */
   public long getCbQueueMaxMsgs();
   /** Comma separated list of all subscriptionId of this login session */
   public String getSubscribeList() throws XmlBlasterException;
   /** Destroy the session (force logout) */
   public String getKillSession() throws XmlBlasterException;
}
