/*------------------------------------------------------------------------------
 Name:      TopicAccessor.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;

import edu.emory.mathcs.backport.java.util.concurrent.BlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.LinkedBlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton in ServerScope to access a TopicHandler instance.
 * <p>
 * Used to guarantee single threaded access to a TopicHandler instance.
 * Only well defined methods allow dirty reads from other threads simultaneously,
 * further we have a pattern to dispatch the topicHandler access to a worker thread. 
 * @see <a
 *      href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">The
 *      engine.message.lifecylce requirement</a>
 * @see org.xmlBlaster.test.topic.TestTopicLifeCycle
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class TopicAccessor {
   private final ServerScope serverScope;

   private static Logger log = Logger.getLogger(TopicAccessor.class.getName());

   /**
    * Map for TopicContainer.
    * <p>
    * key = oid value from <key oid="..."> (== topicHandler.getUniqueKey())
    * value = TopicContainer instance
    */
   private final Map topicHandlerMap = new HashMap();

   /**
    * For listeners who want to be informed about topic creation / deletion
    * events.
    */
   private final Set topicListenerSet = new TreeSet();

   private BlockingQueue blockingQueue;

   /*
    * For listeners who want to be informed about topic creation / deletion
    * events.
    */
   public TopicAccessor(ServerScope serverScope) {
      this.serverScope = serverScope;

      // Support async execution of tasks
      // this.blockingQueue = new SynchronousQueue(); // has no capacity but
      // blocks until other thread takes it out
      final int capacity = 10000;
      this.blockingQueue = new LinkedBlockingQueue(capacity);
      Consumer c = new Consumer(this.blockingQueue);
      Thread t = new Thread(c);
      t.setName("XmlBlaster.Consumer");
      t.setDaemon(true);
      t.start();
   }

   /**
    * Access a topicHandler by its unique oid.
    * <p />
    * You need to call release(topicHandler) after usage!
    * 
    * @param oid
    *           topicHandler.getUniqueKey()
    * @return The topicHandler instance or null, in case of null you don't need
    *         to release anything
    */
   public TopicHandler access(String oid) {
      TopicContainer tc = accessTopicContainer(oid);
      if (tc == null)
         return null;
      return tc.lock(); // Here the calling threads block until its their turn
   }
   
   /**
    * The topicHandler is not locked, use for read only access only and when you know what you are doing.
    * @param oid
    *           topicHandler.getUniqueKey()
    * @return The topicHandler instance or null, you don't need to release anything
    */
   public TopicHandler accessDirtyRead(String oid) {
      TopicContainer tc = accessTopicContainer(oid);
      if (tc == null)
         return null;
      return tc.getTopicHandler();
   }

   // Is NOT locked!
   private TopicContainer accessTopicContainer(String oid) {
      Object obj;
      synchronized (this.topicHandlerMap) {
         obj = this.topicHandlerMap.get(oid);
      }
      if (obj == null) { // Normal case if topic is new created (by subscribe)
         if (log.isLoggable(Level.FINE))
            log.fine("key oid " + oid + " is unknown, topicHandler == null");
         if (log.isLoggable(Level.FINEST))
            Thread.dumpStack();
         return null;
      }
      return (TopicContainer) obj;
   }

   /**
    * Return the topicHandler which you previously got with access(String oid).
    * 
    * @param topicHandler
    *           Currently logs severe if null
    */
   public void release(TopicHandler topicHandler) {
      if (topicHandler == null) {
         log.severe("Unexpected topicHandler == null");
         Thread.dumpStack();
         return;
      }
      Object obj;
      synchronized (this.topicHandlerMap) {
         obj = this.topicHandlerMap.get(topicHandler.getUniqueKey());
      }
      if (obj == null) { // Happens for example in RequestBroler.erase() which
         // triggers a toDead() which cleans up the
         // TopicContainer already
         if (log.isLoggable(Level.FINE)) log.fine("key oid "
               + topicHandler.getUniqueKey()
               + " is unknown, topicHandler == null");
         if (log.isLoggable(Level.FINEST))
            Thread.dumpStack();
         return;
      }
      TopicContainer tc = (TopicContainer) obj;
      tc.unlock();
   }

   /**
    * Access a topicHandler by its unique oid or create it if not known.
    * <p />
    * You need to call release(topicHandler) after usage.
    * 
    * @param sessionInfo
    *           Can be null if called by a subscription
    * @param oid
    *           topicHandler.getUniqueKey()
    * @return The topicHandler instance but never null
    */
   public TopicHandler findOrCreate(SessionInfo sessionInfo, String oid)
         throws XmlBlasterException {
      TopicContainer tc = null;
      Object oldOne;
      synchronized (this.topicHandlerMap) {
         oldOne = this.topicHandlerMap.get(oid);
         if (oldOne == null) {
            TopicHandler topicHandler = new TopicHandler(this.serverScope, sessionInfo, oid);
            tc = new TopicContainer(topicHandler);
            this.topicHandlerMap.put(topicHandler.getUniqueKey(), tc);
         } else {
            tc = (TopicContainer) oldOne;
         }
      }
      TopicHandler topicHandler = tc.lock();
      
      if (topicHandler == null) { // try again recursive
         log.warning("Trying again to get a TopicHandler '"+oid+"': " + sessionInfo.toXml());
         return findOrCreate(sessionInfo, oid);
      }
      
      // old, pre 1.3 behaviour:
      // if (obj == null && sessionInfo != null) { // is new created for a
      // publish(), but not when created for a subscribe
      if (oldOne == null) { // is new created
         fireTopicEvent(topicHandler); // is locked!
      }
      return topicHandler;
   }

   /**
    * Remove the given topic
    * 
    * @param topicHandler
    */
   public void erase(String oid) throws XmlBlasterException {
      TopicContainer tc = accessTopicContainer(oid);
      if (tc == null)
         return;
      Object obj;
      TopicHandler topicHandler = tc.lock();
      try {
         fireTopicEvent(topicHandler); // is locked!
         synchronized (this.topicHandlerMap) {
            obj = this.topicHandlerMap.remove(oid);
         }
         if (obj == null) {
            log.severe("topicHandler '" + oid + "' was not found in map");
            Thread.dumpStack();
         }
      } finally {
         tc.erase(); // unlocks all locks
      }
   }

   /**
    * Treat as read only! For class internal use only.
    * 
    * @return A current snapshot of all topics (never null)
    */
   private TopicHandler[] getTopicHandlerArr() {
      synchronized (this.topicHandlerMap) {
         int len = this.topicHandlerMap.size();
         TopicHandler[] handlers = new TopicHandler[len];
         Iterator it = this.topicHandlerMap.values().iterator();
         int i = 0;
         while (it.hasNext()) {
            handlers[i] = ((TopicContainer) it.next()).getTopicHandler();
            i++;
         }
         return handlers;
      }
   }

   /**
    * Access oid array
    * 
    * @return A string array of all topicHandler.getUniqueKey()
    */
   public String[] getTopics() {
      synchronized (this.topicHandlerMap) {
         int len = this.topicHandlerMap.size();
         String[] handlers = new String[len];
         Iterator it = this.topicHandlerMap.values().iterator();
         int i = 0;
         while (it.hasNext()) {
            handlers[i] = ((TopicContainer) it.next()).getTopicHandler()
                  .getUniqueKey();
            i++;
         }
         return handlers;
      }
   }

   /**
    * Access the number of known topics.
    * 
    * @return Number of registered topics
    */
   public int getNumTopics() {
      synchronized (this.topicHandlerMap) {
         return this.topicHandlerMap.size();
      }
   }
   
   /**
    * Called from SessionPersistencePlugin after all sessions / subscriptions are
    * alive after a server startup.
    * <p/>
    * The topic destroy timers where inhibited and can now be activated
    */
   public void spanTopicDestroyTimeout() {
      String[] oids = getTopics();
      // Other topics which are created in this sync gap don't need it
      // as they are not from persistence store
      for (int i=0; i<oids.length; i++) {
         TopicHandler topicHandler = access(oids[i]);
         if (topicHandler == null) continue;
         try {
            topicHandler.startDestroyTimer();
         }
         finally {
            release(topicHandler);
         }
      }
   }


   /**
    * Access the message meat without a lock.
    * 
    * @param topicId
    *           The topic oid
    * @param msgUnitWrapperUniqueId
    *           The message instance id
    * @return null if not found
    * @throws XmlBlasterException
    */
   public MsgUnitWrapper lookupDirtyRead(String topicId,
         long msgUnitWrapperUniqueId) throws XmlBlasterException {
      TopicHandler topicHandler = getTopicHandlerDirtyRead(topicId);
      if (topicHandler == null)
         return null;
      return topicHandler.getMsgUnitWrapper(msgUnitWrapperUniqueId);
   }

   public void changeDirtyRead(MsgUnitWrapper msgUnitWrapper)
         throws XmlBlasterException {
      TopicHandler topicHandler = getTopicHandlerDirtyRead(msgUnitWrapper
            .getKeyOid());
      if (topicHandler == null)
         return;
      topicHandler.change(msgUnitWrapper);
   }

   private TopicHandler getTopicHandlerDirtyRead(String topicId) {
      TopicContainer tc = accessTopicContainer(topicId);
      if (tc == null)
         return null;
      return tc.getTopicHandler(); // no lock ...
   }

   public boolean hasMsgDistributorPluginDirtyRead(String topicId) {
      TopicHandler topicHandler = getTopicHandlerDirtyRead(topicId);
      if (topicHandler == null)
         return false;
      return topicHandler.getMsgDistributorPlugin() != null;
   }
   
   /**
    * @param topicId key oid
    * @return Never null
    */
   public final SubscriptionInfo[] getSubscriptionInfoArrDirtyRead(String topicId) {
      TopicHandler topicHandler = getTopicHandlerDirtyRead(topicId);
      if (topicHandler == null)
         return new SubscriptionInfo[0];
      return topicHandler.getSubscriptionInfoArr();
   }

   /**
    * Dump all TopicHandler to xml. This is implemented as dirty read to gain
    * performance
    * 
    * @param extraOffset
    * @return The markup of all TopicHandlers
    * @throws XmlBlasterException
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      // Dirty read: if it makes sync problems we need to lookup each
      // TopicHandler seperately over getTopics()
      StringBuffer sb = new StringBuffer(10000);
      if (extraOffset == null)
         extraOffset = "";

      TopicHandler[] topicHandlerArr = getTopicHandlerArr();

      for (int ii = 0; ii < topicHandlerArr.length; ii++) {
         sb.append(topicHandlerArr[ii].toXml(extraOffset + Constants.INDENT));
      }

      return sb.toString();
   }

   /**
    * Helper class to hold the TopicHandler and some additonal locking information. 
    * 
    * @author marcel
    */
   private final class TopicContainer {
      private TopicHandler topicHandler;

      private final boolean fairness = false;

      private final ReentrantLock lock = new ReentrantLock(fairness);

      public TopicContainer(TopicHandler topicHandler) {
         this.topicHandler = topicHandler;
      }

      public TopicHandler getTopicHandler() {
         return this.topicHandler;
      }

      public void erase() {
         if (this.topicHandler == null)
            return;
         synchronized (this.lock) {
            this.topicHandler = null;
            int c = this.lock.getHoldCount();
            for (int i = 0; i < c; i++)
               this.lock.unlock();
         }
      }

      public TopicHandler lock() {
         if (this.topicHandler == null)
            return null;
         this.lock.lock();
         TopicHandler th = this.topicHandler;
         if (th == null) {
            this.lock.unlock();
            return null;
         }
         return th;
      }

      public void unlock() {
         synchronized (this.lock) {
            if (this.lock.getHoldCount() > 0) // returns 0 if we are not the
               // holder
               this.lock.unlock(); // IllegalMonitorStateException if our
            // thread is not the holder of the lock,
            // never happens because of above if()
         }
      }
   } // class TopicContainer

   /**
    * Queue request for later execution, to be outside of sync-locks
    * 
    * @param msgUnitWrapper
    */
   public void entryDestroyed_scheduleForExecution(MsgUnitWrapper msgUnitWrapper) {
      try {
         this.blockingQueue.put(msgUnitWrapper);
      } catch (InterruptedException e) {
      }
   }

   /**
    * Called by msgUnitWrapper.toDestroyed():
    *   this.glob.getTopicAccessor().entryDestroyed_scheduleForExecution(this);
    * Is currently switched off (not used)
    * @author mr@marcelruff.info
    */
   private class Consumer implements Runnable {
      private final BlockingQueue queue;

      Consumer(BlockingQueue q) {
         this.queue = q;
      }

      public void run() {
         try {
            while (true) {
               consume(queue.take());
            }
         } catch (InterruptedException ex) {
            log.severe("TopicAccessor: Unexpected problem: " + ex.toString());
         }
      }

      void consume(Object x) {
         MsgUnitWrapper msgUnitWrapper = (MsgUnitWrapper) x;
         TopicHandler topicHandler = access(msgUnitWrapper.getKeyOid());
         if (topicHandler == null)
            return; // Too late
         try {
            log.severe("DEBUG ONLY: Executing now entry destroyed");
            topicHandler.entryDestroyed(msgUnitWrapper);
         }
         finally {
            release(topicHandler);
         }
      }
   }

   /**
    * Adds the specified Topic listener to receive creation/destruction events
    * of Topics.
    * <p>
    * Note that the fired event holds a locked topicHandler, you shouldn't spend
    * too much time with it to allow other threads to do their work as well
    * 
    * @param l
    *           Your listener implementation
    */
   public void addTopicListener(I_TopicListener l) {
      if (l == null) {
         throw new IllegalArgumentException(
               "TopicAccessor.addTopicListener: the listener is null");
      }
      synchronized (this.topicListenerSet) {
         this.topicListenerSet.add(l);
      }
   }

   /**
    * Removes the specified listener.
    * 
    * @param l
    *           Your listener implementation
    */
   public void removeTopicListener(I_TopicListener l) {
      if (l == null) {
         throw new IllegalArgumentException(
               "TopicAccessor.removeTopicListener: the listener is null");
      }
      synchronized (this.topicListenerSet) {
         this.topicListenerSet.remove(l);
      }
   }

   /**
    * Access a current snapshot of all listeners.
    * 
    * @return The array of registered listeners
    */
   public synchronized I_TopicListener[] getRemotePropertiesListenerArr() {
      synchronized (this.topicListenerSet) {
         return (I_TopicListener[]) this.topicListenerSet
               .toArray(new I_TopicListener[this.topicListenerSet.size()]);
      }
   }

   /**
    * Is fired on topic creation or destruction.
    * <p>
    * Does never throw any exception
    * 
    * @param topicHandler
    *           The locked! handler
    */
   private void fireTopicEvent(TopicHandler topicHandler) {
      try {
         I_TopicListener[] arr = getRemotePropertiesListenerArr();
         for (int i = 0; i < arr.length; i++) {
            try {
               I_TopicListener l = arr[i];
               TopicEvent event = new TopicEvent(topicHandler);
               l.changed(event);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }
}
