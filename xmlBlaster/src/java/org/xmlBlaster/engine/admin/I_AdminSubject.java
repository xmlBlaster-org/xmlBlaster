/*------------------------------------------------------------------------------
Name:      I_AdminSubject.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of a client for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SubjectInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminSubject {
   public long getUptime();
   /** How many messages where sent to all of this clients login sessions */
   public long getNumUpdate();
   public long getSubjectQueueNumMsgs();
   public long getSubjectQueueMaxMsgs();
   public int getNumSessions();
   public int getMaxSessions();
   public void setMaxSessions(int max);
   public String getSessionList();
   public I_AdminSession getSessionByPubSessionId(long pubSessionId);
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
   public String[] peekSubjectMessagesToFile(int numOfEntries, String path) throws XmlBlasterException;
}
