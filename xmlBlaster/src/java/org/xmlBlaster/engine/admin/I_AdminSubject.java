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
   public long getNumUpdates();
   public long getSubjectQueueNumMsgs();
   public long getSubjectQueueMaxMsgs();
   public int getNumSessions();
   public int getMaxSessions();
   public void setMaxSessions(int max);
   public String getSessionList();
   public I_AdminSession getSessionByPubSessionId(long pubSessionId);
   public String killClient() throws XmlBlasterException;
}
