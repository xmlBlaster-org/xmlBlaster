/*------------------------------------------------------------------------------
Name:      SessionInfoProtector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * SessionInfoProtector protects SessionInfo.java from direct access by administrative tasks. 
 * <p>
 * See javadoc of SessionInfo.java
 * </p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class SessionInfoProtector implements SessionInfoProtectorMBean /*I_AdminSession*/
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

   public final String getSubscriptionDump() throws XmlBlasterException {
      return this.sessionInfo.getSubscriptionDump();
   }

   public final String killSession() throws XmlBlasterException {
      return this.sessionInfo.killSession();
   }
   
   public void setDispatcherActive(boolean dispatcherActive) {
      this.sessionInfo.setDispatcherActive(dispatcherActive);
   }
   
   public boolean getDispatcherActive() {
      return this.sessionInfo.getDispatcherActive();
   }
     
   /**
    * keyData is currently unused but it is needed to be consistent with the 
    * admin get convention (i.e. either take no parameters or always take a key
    * and a qos).
    */
   public MsgUnit[] getCbQueueEntries(QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      return this.sessionInfo.getCbQueueEntries(keyData, qosData);
   }

}
