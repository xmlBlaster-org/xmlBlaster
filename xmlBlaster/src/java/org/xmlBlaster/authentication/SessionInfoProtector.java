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

   public final String getId() {
      return this.sessionInfo.getId();
   }

   public final String getLoginName() {
      return this.sessionInfo.getLoginName();
   }

   public final boolean isCallbackConfigured() {
      return this.sessionInfo.isCallbackConfigured();
   }

   public final String getConnectionState() {
      return this.sessionInfo.getConnectionState();
   }

   public final long getPublicSessionId() {
      return this.sessionInfo.getPublicSessionId();
   }

   public final String getLoginDate() {
      return this.sessionInfo.getLoginDate();
   }

   public final String getSessionTimeoutExpireDate() {
      return this.sessionInfo.getSessionTimeoutExpireDate();
   }

   public final void refreshSession() throws XmlBlasterException {
      this.sessionInfo.refreshSession();
   }

   public final long getUptime() {
      return this.sessionInfo.getUptime();
   }

   public final long getNumPublish() {
      return this.sessionInfo.getNumPublish();
   }

   public final long getNumSubscribe() {
      return this.sessionInfo.getNumSubscribe();
   }

   public final long getNumUnSubscribe() {
      return this.sessionInfo.getNumUnSubscribe();
   }

   public final long getNumGet() {
      return this.sessionInfo.getNumGet();
   }

   public final long getNumErase() {
      return this.sessionInfo.getNumErase();
   }

   public final long getNumUpdateOneway() {
      return this.sessionInfo.getNumUpdateOneway();
   }

   public final long getNumUpdate() {
      return this.sessionInfo.getNumUpdate();
   }

   public final long getCbQueueNumMsgs() {
      return this.sessionInfo.getCbQueueNumMsgs();
   }

   public final long getCbQueueMaxMsgs() {
      return this.sessionInfo.getCbQueueMaxMsgs();
   }

   public final String[] getSubscribedTopics() {
      return this.sessionInfo.getSubscribedTopics();
   }

   public final long getNumSubscriptions() {
      return this.sessionInfo.getNumSubscriptions();
   }

   public final String subscribe(String url, String qos) throws XmlBlasterException {
      return this.sessionInfo.subscribe(url, qos);
   }

   public final String[] unSubscribeByIndex(int index, String qos) throws XmlBlasterException {
      return this.sessionInfo.unSubscribeByIndex(index, qos);
   }

   public final String[] unSubscribe(String url, String qos) throws XmlBlasterException {
      return this.sessionInfo.unSubscribe(url, qos);
   }

   public final String[] getSubscriptions() throws XmlBlasterException {
      return this.sessionInfo.getSubscriptions();
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
     
   public String[] peekCallbackMessages(int numOfEntries) throws XmlBlasterException {
      return this.sessionInfo.peekCallbackMessages(numOfEntries);
   }

   /**
    * keyData is currently unused but it is needed to be consistent with the 
    * admin get convention (i.e. either take no parameters or always take a key
    * and a qos).
    */
   public MsgUnit[] getCbQueueEntries(QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      return this.sessionInfo.getCbQueueEntries(keyData, qosData);
   }

   public String[] peekCallbackMessagesToFile(int numOfEntries, String path) throws XmlBlasterException {
      return this.sessionInfo.peekCallbackMessagesToFile(numOfEntries, path);
   }

   /** Enforced by ConnectQosDataMBean interface. */
   public final long getMaxSessions() {
      return this.sessionInfo.getConnectQos().getMaxSessions();
   }
   /** Enforced by ConnectQosDataMBean interface. */
   public final long getSessionTimeout() {
      return this.sessionInfo.getConnectQos().getSessionTimeout();
   }
   /** Enforced by ConnectQosDataMBean interface. */
   public final boolean isPtpAllowed() {
      return this.sessionInfo.getConnectQos().isPtpAllowed();
   }
   /** Enforced by ConnectQosDataMBean interface. */
   public final boolean isPersistent() {
      return this.sessionInfo.getConnectQos().getData().isPersistent();
   }
}
