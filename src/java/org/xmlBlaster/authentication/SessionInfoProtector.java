/*------------------------------------------------------------------------------
Name:      SessionInfoProtector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ClientProperty;

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

   public final String getQos() {
      return this.sessionInfo.getQos();
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

   public final String getAliveSinceDate() {
      return this.sessionInfo.getAliveSinceDate();
   }

   public final String getPollingSinceDate() {
      return this.sessionInfo.getPollingSinceDate();
   }

   public final String getLastCallbackException() {
      return this.sessionInfo.getLastCallbackException();
   }

   public final void clearLastCallbackException() {
      this.sessionInfo.clearLastCallbackException();
   }

   public final int getNumCallbackExceptions() {
      return this.sessionInfo.getNumCallbackExceptions();
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

   public final long getCbQueueNumMsgsCache() {
	      return this.sessionInfo.getCbQueueNumMsgsCache();
	   }

   public final long getCbQueueMaxMsgs() {
      return this.sessionInfo.getCbQueueMaxMsgs();
   }
   
   public final long getCbQueueMaxMsgsCache() {
      return this.sessionInfo.getCbQueueMaxMsgsCache();
   }

   public String pingClientCallbackServer() {
      return this.sessionInfo.pingClientCallbackServer();
   }

   public long getPingRoundTripDelay() {
      return this.sessionInfo.getPingRoundTripDelay();
   }

   public long getRoundTripDelay() {
      return this.sessionInfo.getRoundTripDelay();
   }

   public final String[] getSubscribedTopics() {
      return this.sessionInfo.getSubscribedTopics();
   }

   public final long getCurrBytesRead() {
      return this.sessionInfo.getDispatchStatistic().getCurrBytesRead();
   }
   public final long getNumBytesToRead() {
      return this.sessionInfo.getDispatchStatistic().getNumBytesToRead();
   }
   public final long getOverallBytesRead() {
      return this.sessionInfo.getDispatchStatistic().getOverallBytesRead();
   }

   public final long getCurrBytesWritten() {
      return this.sessionInfo.getDispatchStatistic().getCurrBytesWritten();
   }
   public final long getNumBytesToWrite() {
      return this.sessionInfo.getDispatchStatistic().getNumBytesToWrite();
   }
   public final long getOverallBytesWritten() {
      return this.sessionInfo.getDispatchStatistic().getOverallBytesWritten();
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

   public final String[] getSubscriptions() throws Exception {
      try {
         return this.sessionInfo.getSubscriptions();
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
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

   public MsgUnit[] getCallbackQueueEntries(String querySpec) throws XmlBlasterException {
      return this.sessionInfo.getCallbackQueueEntries(querySpec);
   }

   public String[] peekCallbackMessagesToFile(int numOfEntries, String path) throws Exception {
      return this.sessionInfo.peekCallbackMessagesToFile(numOfEntries, path);
   }

   public long clearCallbackQueue() {
      return this.sessionInfo.clearCallbackQueue();
   }

   public long removeFromCallbackQueue(long numOfEntries) throws Exception {
      try {
         return this.sessionInfo.removeFromCallbackQueue(numOfEntries);
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.getMessage());
      }
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
   public final void setSessionTimeout(long timeout) {
      this.sessionInfo.setSessionTimeout(timeout);
   }
   /** Enforced by ConnectQosDataMBean interface. */
   public final boolean isPtpAllowed() {
      return this.sessionInfo.getConnectQos().isPtpAllowed();
   }
   /** Enforced by ConnectQosDataMBean interface. */
   public final boolean isPersistent() {
      return this.sessionInfo.getConnectQos().getData().isPersistent();
   }

   public String[] getRemoteProperties() {
      ClientProperty[] cp = this.sessionInfo.getRemotePropertyArr();
      String[] arr = new String[cp.length];
      for (int i=0; i<cp.length; i++)
         arr[i] = cp[i].toXml("", "remoteProperty", true).trim();
      return arr;
   }

   public String clearRemotePropertiesStartingWith(String prefix) {
      ClientPropertiesInfo info = this.sessionInfo.getRemoteProperties();
      if (info == null || prefix == null) return "No remote properties found, nothing to clear";
      synchronized (this.sessionInfo) {
         ClientProperty[] arr = info.getClientPropertyArr();
         int count = 0;
         for (int i=0; i<arr.length; i++) {
            if (arr[i].getName().startsWith(prefix)) {
               info.getClientPropertyMap().remove(arr[i].getName());
               count++;
            }
         }
         return "Removed " + count + " remote properties which are starting with '"+prefix+"'";
      }
   }

   public String clearRemoteProperties() {
      ClientPropertiesInfo info = this.sessionInfo.getRemoteProperties();
      if (info == null) return "No remote properties found, nothing to clear";
      this.sessionInfo.setRemoteProperties(null);
      return "Removed " + info.getClientPropertyMap().size() + " remote properties";
   }

   public String addRemoteProperty(String key, String value) {
      ClientProperty old = this.sessionInfo.addRemoteProperty(key, value);
      if (old == null)
         return "Added client property '" + key + "'";
      else
         return "Replaced existing client property '" + old.toXml("", "remoteProperty").trim() + "'";
   }

   /** JMX */
   public java.lang.String usage() {
      return this.sessionInfo.usage();
   }
   /** JMX */
   public java.lang.String getUsageUrl() {
      return this.sessionInfo.getUsageUrl();
   }
   /* JMX dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}

   public ConnectQosServer getConnectQos() {
      return this.sessionInfo.getConnectQos();
   }

   public boolean isStalled() {
      return this.sessionInfo.isStalled();
   }
}
