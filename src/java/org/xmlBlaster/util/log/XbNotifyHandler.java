package org.xmlBlaster.util.log;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Dispatch logging output to registered listeners. 
 * As java.util.logging framework supports max. one Filter per Logger/Handler
 * we dispatch it further here.
 * <p/>
 * Note:
 * You may not directly add a Filter to java.util.logging.Logger/Handler as this
 * would destroy our registration.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XbNotifyHandler extends Handler {
   private static XbNotifyHandler theXbNotifyHandler;
   private final String id;
   private static long instanceCounter;
   private Set errorListenerSet = new HashSet();
   private I_LogListener[] errorCache;
   private Set warnListenerSet = new HashSet();
   private I_LogListener[] warnCache;
   private Set allListenerSet = new HashSet();
   private I_LogListener[] allCache;
   private final I_LogListener[] emptyArr = new I_LogListener[0];
   private boolean hasAllListener;
   
   public XbNotifyHandler() {
      this("default-"+instanceCounter++);
   }
   
   public XbNotifyHandler(String id) {
      synchronized (XbNotifyHandler.class) {
         theXbNotifyHandler = this;
      }
      this.id = id;
   }
   
   public static XbNotifyHandler instance() {
      if (theXbNotifyHandler == null) {
         synchronized (XbNotifyHandler.class) {
            if (theXbNotifyHandler == null) {
               theXbNotifyHandler = new XbNotifyHandler();
            }
         }
      }
      return theXbNotifyHandler;  
   }

   public void close() throws SecurityException {
   }

   public void flush() {
   }

   /* Redirect logging to our listeners.  (non-Javadoc)
    * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
    */
   public void publish(LogRecord record) {
      //System.out.println("[XbNotifyHandler-"+this.id+"] " + record.getLevel() + " " + record.getMessage());
      int level = record.getLevel().intValue();
      if (Level.WARNING.intValue() == level) {
         I_LogListener[] arr = getWarnListeners();
         for (int i=0; i<arr.length; i++) {
            arr[i].log(record);
         }
      }
      else if (Level.SEVERE.intValue() == level) {
         I_LogListener[] arr = getErrorListeners();
         for (int i=0; i<arr.length; i++) {
            arr[i].log(record);
         }
      }
      if (this.hasAllListener) {
         I_LogListener[] arr = getAllListeners();
         for (int i=0; i<arr.length; i++) {
            arr[i].log(record);
         }
      }
   }

   /**
    * Register a listener. 
    * This listener may NOT use logging himself to avoid recursion
    * If this set already contains the specified element, the call leaves this set unchanged and returns false
    * @param level to add, Level.SEVERE.intValue() | Level.WARNING.intValue()
    * @param logNotification The interface to send the logging
    * @return true if the given logNotification is added
    */
   public synchronized boolean register(int level, I_LogListener logNotification) {
      boolean ret = false;
      if (Level.WARNING.intValue() == level) {
         ret = this.warnListenerSet.add(logNotification);
         this.warnCache = null;
      }
      else if (Level.SEVERE.intValue() == level) {
         ret = this.errorListenerSet.add(logNotification);
         this.errorCache = null;
      }
      else if (Level.ALL.intValue() == level) {
         ret = this.allListenerSet.add(logNotification);
         this.allCache = null;
         this.hasAllListener = true;
      }
      return ret;
   }

   /**
    * Remove the listener. 
    * @param level Which levels you want to remove. Level.SEVERE.intValue() | Level.WARNING.intValue()
    * @return true if the set contained the specified element.
    */
   public synchronized boolean unregister(int level, I_LogListener logNotification) {
      boolean ret = false;
      if (Level.WARNING.intValue() == level) {
         ret = this.warnListenerSet.remove(logNotification);
         this.warnCache = null;
      }
      else if (Level.SEVERE.intValue() == level) {
         ret = this.errorListenerSet.remove(logNotification);
         this.errorCache = null;
      }
      else if (Level.ALL.intValue() == level) {
         ret = this.allListenerSet.remove(logNotification);
         this.allCache = null;
         if (this.allListenerSet.size() == 0)
            this.hasAllListener = false;
      }
      return ret;
   }

   /**
    * Get a snapshot of warn listeners. 
    */
   public I_LogListener[] getWarnListeners() {
      if (this.warnCache == null) {
         synchronized (this) {
            if (this.warnCache == null) {
               this.warnCache = (I_LogListener[])this.warnListenerSet.toArray(new I_LogListener[this.warnListenerSet.size()]);
            }
         }
      }
      return this.warnCache;
   }

   /**
    * @return Returns the id.
    */
   public String getId() {
      return this.id;
   }

   /**
    * Get a snapshot of error listeners. 
    */
   public I_LogListener[] getErrorListeners() {
      if (this.errorCache == null) {
         synchronized (this) {
            if (this.errorCache == null) {
               this.errorCache = (I_LogListener[])this.errorListenerSet.toArray(new I_LogListener[this.errorListenerSet.size()]);
            }
         }
      }
      return this.errorCache;
   }

   /**
    * Get a snapshot of all listeners. 
    */
   public I_LogListener[] getAllListeners() {
      if (!this.hasAllListener) return emptyArr;
      if (this.allCache == null) {
         synchronized (this) {
            if (this.allCache == null) {
               this.allCache = (I_LogListener[])this.allListenerSet.toArray(new I_LogListener[this.allListenerSet.size()]);
            }
         }
      }
      return this.allCache;
   }

   public static void main(String[] args) {
   }
}
