/*------------------------------------------------------------------------------
Name:      I_AdminSubject.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.I_AdminUsage;

/**
 * Declares available methods of a client for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SubjectInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminSubject extends I_AdminUsage {
   /**
    * Get elapsed seconds since this subject was created. 
    */
   public long getUptime();
   /**
    * Get date when this subject was created. 
    * This happens usually on a first login or on a first PtP message for this destination. 
    * @return The date string
    */
   public String getCreationDate();
   /** 
    * How many messages where sent to all of this clients login sessions. 
    * @return Number of updated messages send
    */
   public long getNumUpdate();
   /**
    * Returns how many PtP messages are currently in my subject queue. 
    * @return Number of messages in the queue
    */
   public long getSubjectQueueNumMsgs();
   /**
    * Returns the maximum allowed PtP messages in this queue. 
    * @return The max messages supported
    */
   public long getSubjectQueueMaxMsgs();
   /**
    * Returns how many login sessions this subject currently has. 
    * @return Number of sessions of this subject
    */
   public int getNumSessions();
   /**
    * Returns the maximum allowed login sessions for this subject. 
    * @return Max. number of sessions for this subject
    */
   public int getMaxSessions();
   /**
    * Configure the maximum allowed login sessions for this subject. 
    * @param max The maximum number of sessions for this subject
    */
   public void setMaxSessions(int max);
   /**
    * Get a list of all session names for this subject. 
    * @return Comma separated list of sessions
    */
   public String getSessionList();
   /**
    * Get a list of all session names for this subject. 
    * @return Array of session names
    */
   public String[] getSessions();
   /**
    * Navigate to a session instance. 
    * @param pubSessionId The public session ID of the session to lookup
    * @return The found session interface
    */
   public I_AdminSession getSessionByPubSessionId(long pubSessionId);
   /**
    * Destroy the client with all its sessions. 
    * @return The list of killed sessions (public session IDs)
    */
   public String killClient() throws XmlBlasterException;
   /**
    * Peek point to point messages from subject queue, they are not removed
    * @param numOfEntries The number of messages to peek, taken from the front
    * @return The dump of the messages
    */
   public String[] peekSubjectMessages(int numOfEntries) throws XmlBlasterException;
   /**
    * Peek messages from PtP subject queue and dump them to a file, they are not removed. 
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The absolute file names dumped
    */
   public String[] peekSubjectMessagesToFile(int numOfEntries, String path) throws Exception;
}
