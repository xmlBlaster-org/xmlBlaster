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
 * @author ruff@swand.lake.de
 * @since 0.79f
 */
public interface I_AdminSubject {
   public long getUptime();
   public long getNumUpdates();
   public int getCbQueueNumMsgs();
   public int getCbQueueMaxMsgs();
   public int getNumSessions();
   public int getMaxSessions();
   public String getSessionList();
   public String getKillClient() throws XmlBlasterException;
}
