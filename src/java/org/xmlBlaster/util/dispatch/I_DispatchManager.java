/*------------------------------------------------------------------------------
Name:      DispatchManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import java.util.ArrayList;
import java.util.List;

import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * Manages the sending of messages and commands and does error recovery
 * further we communicate with the dispatcher plugin if one is configured.
 * <p />
 * There is one instance of this class per queue and remote connection.
 * @author xmlBlaster@marcelruff.info
 */
public interface I_DispatchManager extends I_Timeout, I_QueuePutListener {

   /**
    * @return Never null
    */
   SessionName getSessionName();

   boolean isSyncMode();
   
   boolean isForceAsyncConnect();

   /**
    * Set behavior of dispatch framework.
    * @param trySyncMode true: client side queue embedding, false: server side callback queue
    * defaults to false
    */
    void trySyncMode(boolean trySyncMode);

   /**
    * Reconfigure dispatcher with given properties.
    *
    * Note that only a limited re-configuration is supported
    * @param addressArr The new configuration
    */
     void updateProperty(CallbackAddress[] addressArr) throws XmlBlasterException;

    I_Queue getQueue();

   /*
    * Register yourself if you want to be informed about the remote connection status.
    * @param connectionStatusListener The implementation which listens on connectionState events (e.g. XmlBlasterAccess.java)
    * @return true if we did not already contain the specified element.
    */
    boolean addConnectionStatusListener(I_ConnectionStatusListener connectionStatusListener);

    boolean addConnectionStatusListener(I_ConnectionStatusListener connectionStatusListener, boolean fireInitial);

   /**
    * Remove the given listener
    * @param connectionStatusListener
    * @return true if it was removed
    */
    boolean removeConnectionStatusListener(I_ConnectionStatusListener connectionStatusListener);

    I_ConnectionStatusListener[] getConnectionStatusListeners();

   /**
    * The name in the configuration file for the plugin
    * @return e.g. "Priority,1.0"
    */
    String getTypeVersion();

   /**
    * @return The import/export encrypt handle or null if created by a SubjectInfo (no session info available)
    */
    I_MsgSecurityInterceptor getMsgSecurityInterceptor();

   /**
    * @return The handler of all callback plugins, is never null
    */
     DispatchConnectionsHandler getDispatchConnectionsHandler();

   /**
    * How many messages maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver in one bulk.
    */
     int getBurstModeMaxEntries();

   /**
    * How many bytes maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver in one bulk.
    */
     long getBurstModeMaxBytes();


   /**
    * Get timestamp when we went to ALIVE state.
    * @return millis timestamp
    */
     long getAliveSinceTime();

   /**
    * Get timestamp when we went to POLLING state.
    * @return millis timestamp
    */
     long getPollingSinceTime();

   /**
    * Call by DispatchConnectionsHandler on state transition
    * NOTE: toAlive is called initially when a protocol plugin is successfully loaded
    * but we don't know yet if it ever is able to connect
    */
    void toAlive(ConnectionStateEnum oldState);

    void reachedAliveSync(ConnectionStateEnum oldState, I_XmlBlasterAccess connection);

   /** Call by DispatchConnectionsHandler on state transition */
    void toPolling(ConnectionStateEnum oldState);

   /**
    * 
    * @param ex
    */
    void toDead(XmlBlasterException ex);

   /** Call by DispatchConnectionsHandler on state transition */
    void shutdownFomAnyState(ConnectionStateEnum oldState, XmlBlasterException ex);
   
    void postSendNotification(MsgQueueEntry entry);
   
    void postSendNotification(MsgQueueEntry[] entries);
   
   /**
    * Notify I_PostSendListener about problem. 
    * <p>
    * Typically XmlBlasterAccess is notified when message came asynchronously from queue
    *  
    * @param entryList
    * @param ex
    * @return true if processed
    * @see I_PostSendListener#postSend(MsgQueueEntry) for explanation
    */
    boolean sendingFailedNotification(MsgQueueEntry[] entries, XmlBlasterException ex);

   /**
    * Called by DispatchWorker if an Exception occured in sync mode
    * Only on client side
    */
    void handleSyncWorkerException(List<I_Entry> entryList, Throwable throwable) throws XmlBlasterException;

   /**
    * Messages are successfully sent, remove them now from queue (sort of a commit()):
    * We remove filtered/destroyed messages as well (which doen't show up in entryListChecked)
    * @param postSendNotify TODO
    */
    void removeFromQueue(MsgQueueEntry[] entries, boolean postSendNotify) throws XmlBlasterException;

   /**
    * Called by DispatchWorker if an Exception occurred in async mode. 
    * @throws XmlBlasterException should never happen but is possible during removing entries from queue
    */
    void handleWorkerException(List<I_Entry> entryList, Throwable throwable) throws XmlBlasterException;

    I_MsgErrorHandler getMsgErrorHandler();

   /**
    * We register a QueuePutListener and all put() into the queue are
    * intercepted - our put() is called instead.
    * We then deliver this QueueEntry directly to the remote
    * connection and return synchronously the returned value or the
    * Exception if one is thrown.
    */
    void switchToSyncMode();
      
   /**
    * Switch back to asynchronous mode.
    * Our thread pool will take the messages out of the queue
    * and deliver them in asynchronous mode.
    */
    void switchToASyncMode();

   /**
    * @see I_QueuePutListener#putPre(I_QueueEntry)
    */
    boolean putPre(I_QueueEntry queueEntry) throws XmlBlasterException;

   /**
    * @see #putPre(I_QueueEntry)
    * @see I_QueuePutListener#putPre(I_QueueEntry[])
    */
    boolean putPre(I_QueueEntry[] queueEntries) throws XmlBlasterException;

   /**
    * @see I_QueuePutListener#putPost(I_QueueEntry)
    */
    void putPost(I_QueueEntry queueEntry) throws XmlBlasterException;

   /**
    * @see #putPost(I_QueueEntry)
    * @see I_QueuePutListener#putPost(I_QueueEntry[])
    */
    void putPost(I_QueueEntry[] queueEntries) throws XmlBlasterException;

   /**
    * Here we prepare messages which are coming directly from the queue.
    * <ol>
    *   <li>We eliminate destroyed messages</li>
    *   <li>We make a shallow copy of the message.
    *       We need to do this, out messages are references directly into the queue.
    *       The delivery framework is later changing the QoS
    *       and plugins may change the content - and this should not modify the queue entries</li>
    * </ol>
    */
    ArrayList prepareMsgsFromQueue(List<I_Entry> entryList);

   /**
    * When somebody puts a new entry into the queue, we want to be
    * notified about this after the entry is fed.
    * <p>
    * Called by I_Queue.putPost()
    */
    void notifyAboutNewEntry();

   /**
    * Counts how often a new entry was added since the current worker thread was started.
    */
    int getNotifyCounter();

    boolean isDead();

    boolean isPolling();

    boolean isAlive();

   /**
    * Can be called when client connection is lost (NOT the callback connection).
    * Currently only detected by the SOCKET protocol plugin.
    * Others can only detect lost clients with their callback protocol pings
    */
    void lostClientConnection();

   /**
    * We are notified about the burst mode timeout through this method.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
    void timeout(Object userData);

   /**
    * @return The interceptor plugin if available, otherwise null
    */
    I_MsgDispatchInterceptor getMsgDispatchInterceptor();

   /**
    * Set new callback addresses, typically after a session login/logout
    */
    void setAddresses(AddressBase[] addr) throws XmlBlasterException;

   /**
    * The worker notifies us that it is finished, if messages are available
    * it is triggered again.
    */
   void setDispatchWorkerIsActive(boolean val);

   /**
    * Called locally and from TopicHandler when internal error (Throwable) occurred to avoid infinite looping
    */
    void internalError(Throwable throwable);

   /**
    * @return A container holding some statistical delivery information
    */
    DispatchStatistic getDispatchStatistic();

    boolean isShutdown();

   /**
    * Stop all callback drivers of this client.
    * Possibly invoked twice (givingUpDelivery() calls it indirectly as well)
    * We don't shutdown the corresponding queue.
    */
    void shutdown();

   /**
    * For logging
    */
    String getId();

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state as a XML ASCII string
    */
    String toXml(String extraOffset);

   /**
    * Inhibits/activates the delivery of asynchronous dispatches of messages.
    * @param dispatcherActive
    */
    void setDispatcherActive(boolean dispatcherActive);

   /**
    *
    * @return true if the dispacher is currently activated, i.e. if it is
    * able to deliver asynchronousy messages from the callback queue.
    */
    boolean isDispatcherActive();

    ArrayList filterDistributorEntries(ArrayList entries, Throwable ex);

    boolean pingCallbackServer(boolean sync, boolean connectionIsDown);
    
    
}
