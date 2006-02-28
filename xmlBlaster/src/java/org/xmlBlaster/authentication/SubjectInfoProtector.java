/*------------------------------------------------------------------------------
Name:      SubjectInfoProtector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * SubjectInfoProtector protects SubjectInfo.java from direct access by administrative tasks. 
 * <p>
 * See javadoc of SubjectInfo.java for a description
 * </p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class SubjectInfoProtector implements /*I_AdminSubject,*/ SubjectInfoProtectorMBean
{
   private final SubjectInfo subjectInfo;

   public SubjectInfoProtector(SubjectInfo subjectInfo) {
      this.subjectInfo = subjectInfo;
   }

   public long getUptime() {
      return this.subjectInfo.getUptime();
   }

   public String getCreationDate() {
      return this.subjectInfo.getCreationDate();
   }

   public long getNumUpdate() {
      return this.subjectInfo.getNumUpdate();
   }

   public long getSubjectQueueNumMsgs() {
      return this.subjectInfo.getSubjectQueueNumMsgs();
   }

   public long getSubjectQueueMaxMsgs() {
      return this.subjectInfo.getSubjectQueueMaxMsgs();
   }

   public int getNumSessions() {
      return this.subjectInfo.getNumSessions();
   }

   public int getMaxSessions() {
      return this.subjectInfo.getMaxSessions();
   }

   public void setMaxSessions(int maxSessions) {
      this.subjectInfo.setMaxSessions(maxSessions);
   }

   public String getSessionList() {
      return this.subjectInfo.getSessionList();
   }

   public String[] getSessions() {
      SessionInfo[] arr = this.subjectInfo.getSessions();
      String[] ret = new String[arr.length];
      for (int i=0; i<arr.length; i++) {
         ret[i] = ""+arr[i].getPublicSessionId();
      }
      return ret;
   }

   public I_AdminSession getSessionByPubSessionId(long pubSessionId) {
      return this.subjectInfo.getSessionByPubSessionId(pubSessionId);
   }

   public String killClient() throws XmlBlasterException {
      return this.subjectInfo.killClient();
   }

   public String[] peekSubjectMessages(int numOfEntries) throws XmlBlasterException {
      return this.subjectInfo.peekSubjectMessages(numOfEntries);
   }
   public String[] peekSubjectMessagesToFile(int numOfEntries, String path) throws Exception {
      return this.subjectInfo.peekSubjectMessagesToFile(numOfEntries, path);
   }
   
   public MsgUnit[] getSubjectQueueEntries(QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      return this.subjectInfo.getSubjectQueueEntries(keyData, qosData);
   }

   /** JMX */
   public java.lang.String usage() {
      return this.subjectInfo.usage();
   }
   /** JMX */
   public java.lang.String getUsageUrl() {
      return this.subjectInfo.getUsageUrl();
   }
   /* JMX dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}
}
