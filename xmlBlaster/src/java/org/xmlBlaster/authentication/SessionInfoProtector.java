/*------------------------------------------------------------------------------
Name:      SessionInfoProtector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * SessionInfoProtector protects SessionInfo.java from direct access by administrative tasks. 
 * <p>
 * See javadoc of SessionInfo.java
 * </p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class SessionInfoProtector implements I_AdminSession
{
   private final SessionInfo sessionInfo;

   public SessionInfoProtector(SessionInfo sessionInfo) {
      this.sessionInfo = sessionInfo;
   }
   public final long getUptime() {
      return this.sessionInfo.getUptime();
   }

   public final long getNumUpdates() {
      return this.sessionInfo.getNumUpdates();
   }

   public final long getCbQueueNumMsgs() {
      return this.sessionInfo.getCbQueueNumMsgs();
   }

   public final long getCbQueueMaxMsgs() {
      return this.sessionInfo.getCbQueueMaxMsgs();
   }

   public final String getSubscriptionList() throws XmlBlasterException {
      return this.sessionInfo.getSubscriptionList();
   }

   public final String getKillSession() throws XmlBlasterException {
      return this.sessionInfo.getKillSession();
   }
}
