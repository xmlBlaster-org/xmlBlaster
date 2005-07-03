/*------------------------------------------------------------------------------
Name:      I_AdminSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;

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
   public String getSubscriptionList() throws XmlBlasterException;
   /** An XML dump of all subscriptions of this login session */
   public String getSubscriptionDump() throws XmlBlasterException;
   /** Invoke operation to destroy the session (force logout) */
   public String killSession() throws XmlBlasterException;
   /** activates/inhibits the dispatch of messages to this session */
   public void setDispatcherActive(boolean dispatcherActive);
   /** true if the dispatcher is currently able to dispatch asyncronously */
   public boolean getDispatcherActive();
   /** gets the entries in the callback queue according to what is specified in the qosData object */
   public MsgUnit[] getCbQueueEntries(QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException;
   
}
