/*------------------------------------------------------------------------------
Name:      BlockingQueueWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * BlockingQueueWrapper is a wrapper to I_Queue which can be used to perform a
 * blocking peek on an I_Queue.
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class BlockingQueueWrapper implements I_StorageSizeListener {

   private static Logger log = Logger.getLogger(BlockingQueueWrapper.class.getName());
   private long pollIntervalMillis = 1000L;
   private I_Queue queue;
   private boolean isRegistered;
   private boolean waiting;

   public interface I_BlockingQueueCb {
      List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
            I_QueueEntry limitEntry) throws XmlBlasterException;
   }

   /**
    * Constructor
    * @param pollIntervalMillis time in milliseconds to wait before a check about the
    * queue size is done.
    */
   public BlockingQueueWrapper(long pollIntervalMillis) {
      if (pollIntervalMillis > 0L)
         this.pollIntervalMillis = pollIntervalMillis;
      else
         log.warning("The requested pollInterval is negative, will set it to the default value '" + this.pollIntervalMillis + "'");
   }

   public BlockingQueueWrapper() {
      this(1000L);
   }

   public synchronized void init(I_Queue queue_) throws XmlBlasterException {
      if (queue_ == null)
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_CONFIGURATION, "The queue passed is null");
      this.queue = queue_;
   }

   public void clear() {
	   I_Queue q = this.queue;
	   if (q != null)
		   q.clear();
   }

   /**
    * @return null after shutdown
    */
   public I_Queue getQueue() {
	   return this.queue;
   }

   public synchronized void shutdown() {
	  I_Queue q = this.queue;
	  if (q != null) {
		 if (this.isRegistered)
            q.removeStorageSizeListener(this);
	  }
      this.queue = null;

   }

   /**
    * Enforced by I_StorageSizeListener.
    *
    * @param queue
    * @param numEntries
    * @param numBytes
    * @param isShutdown
    */
   public void changed(I_Storage storage, long numEntries, long numBytes, boolean isShutdown) {
      if (this.waiting) { // to optimize performance we check if really needed
         synchronized(this) {
            try {
               this.notify();
            }
            catch (IllegalMonitorStateException ex) {
               if (log.isLoggable(Level.INFO)) {
                  log.warning("A notify occured when the object was not synchronized");
                  ex.printStackTrace();
               }
            }
         }
      }
   }

   /**
    * Blocks until at least numOfEntries are found in the queue, or the timeout has occured. This method can return partial results,
    * i.e. if the requested amout of entries is 10 and the number of entries in the queue is 4 when the timeout occurs, then the
    * four entries found are returned.
    *
    * This method works best if the queue performs its puts without inhibiting queueSizeEvents (the second argument in the put method),
    * since the put would be intercepted directly. However even if the putter decides to inhibit this event, this method will poll
    * according to what specified in the constructor and will work anyway (possibly with a slight offset after the put has occured).
    *
    * @param numOfEntries the number of entries to return (or the maximum number of entries if the timeout occurs).
    * @param timeout The timeout in milliseconds to wait until to return, timeout=0: no blocking, timeout<0:Infinite blocking
    * @return The ArrayList containing the I_Entry entries of the queue found.
    * @throws XmlBlasterException if the queue is null or if the backend queue throws an Exception.
    */
   private final synchronized List<I_Entry> blockingQueueOperation(int numOfEntries, long timeout, int minPrio,
         int maxPrio, I_QueueEntry limitEntry, I_BlockingQueueCb cb) throws XmlBlasterException {
	  I_Queue q = this.queue;
      if (q == null)
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_JDBC_INVALID, "The invoked queue is null (already shutdown ?)");
      //List<I_Entry> ret = q.peek(numOfEntries, -1L);
      // TODO: if numOfEntries == -1 und timeout > 0L we expect at least one
      //if ((ret.size() > 0 && ret.size() >= numOfEntries) || timeout == 0L) // should be sufficient a ==
      //   return ret;

      try {
         this.waiting = true;
         if (!this.isRegistered) {
            q.addStorageSizeListener(this);
            this.isRegistered = true;
         }

         long endTime = System.currentTimeMillis() + timeout;
         long remainingTime = 0L;
         boolean infiniteBlocking = (timeout < 0L);
         while ( (remainingTime=endTime-System.currentTimeMillis()) > 0L || infiniteBlocking) {
            if ((numOfEntries != -1 && q.getNumOfEntries() >= numOfEntries) ||
            		numOfEntries == -1 && q.getNumOfEntries() > 0) {
               return cb.queueOperation(q, numOfEntries, -1L, minPrio, maxPrio, limitEntry);
            }
            long sleepTime = Math.max(remainingTime, this.pollIntervalMillis);
            this.wait(sleepTime);
         }
         return cb.queueOperation(q, numOfEntries, -1L, minPrio, maxPrio, limitEntry);
      }
      catch (InterruptedException ex) {
         ex.printStackTrace();
         return cb.queueOperation(q, numOfEntries, -1L, minPrio, maxPrio, limitEntry);
      }
      finally {
         this.waiting = false;
      }
   }

   public List<I_Entry> blockingPeek(int numOfEntries, long timeout) throws XmlBlasterException {
      return blockingQueueOperation(numOfEntries, timeout,  0, 0, null, new I_BlockingQueueCb() {
         public List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
               I_QueueEntry limitEntry) throws XmlBlasterException {
            return queue.peek(numEntries, numBytes);
         }
      });
   }

   public List<I_Entry> blockingTakeLowest(int numOfEntries, long timeout, I_QueueEntry limitEntry)
         throws XmlBlasterException {
      return blockingQueueOperation(numOfEntries, timeout,  0, 0, limitEntry, new I_BlockingQueueCb() {
         public List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
               I_QueueEntry limitEntry) throws XmlBlasterException {
            boolean leaveOne = false;
            return queue.takeLowest(numEntries, numBytes, limitEntry, leaveOne);
         }
      });
   }

   public List<I_Entry> blockingPeekLowest(int numOfEntries, long timeout, I_QueueEntry limitEntry)
         throws XmlBlasterException {
      return blockingQueueOperation(numOfEntries, timeout,  0, 0, limitEntry, new I_BlockingQueueCb() {
         public List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
               I_QueueEntry limitEntry) throws XmlBlasterException {
            boolean leaveOne = false;
            return queue.peekLowest(numEntries, numBytes, limitEntry, leaveOne);
         }
      });
   }

   public List<I_Entry> blockingTakeWithPriority(int numOfEntries, long timeout, int minPrio, int maxPrio)
         throws XmlBlasterException {
      return blockingQueueOperation(numOfEntries, timeout,  minPrio, maxPrio, null, new I_BlockingQueueCb() {
         public List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
               I_QueueEntry limitEntry) throws XmlBlasterException {
            return queue.takeWithPriority(numEntries, numBytes, minPrio, maxPrio);
         }
      });
   }

   public List<I_Entry> blockingPeekWithPriority(int numOfEntries, long timeout, int minPrio, int maxPrio)
         throws XmlBlasterException {
      return blockingQueueOperation(numOfEntries, timeout,  minPrio, maxPrio, null, new I_BlockingQueueCb() {
         public List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
               I_QueueEntry limitEntry) throws XmlBlasterException {
            return queue.peekWithPriority(numEntries, numBytes, minPrio, maxPrio);
         }
      });
   }

   public List<I_Entry> blockingPeekSamePriority(int numOfEntries, long timeout) throws XmlBlasterException {
      return blockingQueueOperation(numOfEntries, timeout,  0, 0, null, new I_BlockingQueueCb() {
         public List<I_Entry> queueOperation(I_Queue queue, int numEntries, long numBytes, int minPrio, int maxPrio,
               I_QueueEntry limitEntry) throws XmlBlasterException {
            return queue.peekSamePriority(numEntries, numBytes);
         }
      });
   }
   
}
