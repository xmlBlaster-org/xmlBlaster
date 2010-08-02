/**
 * 
 */
package org.xmlBlaster.util;

/**
 * @author xmlblast
 *
 */
public interface I_TimeoutManager {

   /**
    * Get number of current used timers.
    * 
    * @return The number of active timers
    */
   public abstract int getSize();

   /**
    * Add a listener which gets informed after 'delay' milliseconds.
    * <p />
    * After the timeout happened, you are not registered any more. If you want
    * to cycle timeouts, you need to register again.
    * <p />
    * 
    * @param listener
    *           Your callback handle (you need to implement this interface).
    * @param delay
    *           The timeout in milliseconds. You can pass 0L and the Timeout
    *           thread will fire immediately, this can be useful to dispatch a
    *           task to the timeoutlistener
    * @param userData
    *           Some arbitrary data you supply, it will be routed back to you
    *           when the timeout occurs through method I_Timeout.timeout().
    * @return A handle which you can use to unregister with
    *         removeTimeoutListener().
    */
   public abstract Timestamp addTimeoutListener(I_Timeout listener, long delay,
         Object userData);

   /**
    * Refresh a listener before the timeout happened.
    * <p />
    * NOTE: The returned timeout handle is different from the original one.
    * <p />
    * 
    * NOTE: If you are not sure if the key has elapsed already try this:
    * 
    * <pre>
    * timeout.removeTimeoutListener(timeoutHandle);
    * timeoutHandle = timeout.addTimeoutListener(this, &quot;1000L&quot;, &quot;UserData&quot;);
    * </pre>
    * 
    * @param key
    *           The timeout handle you received by a previous
    *           addTimeoutListener() call.<br />
    *           It is invalid after this call.
    * @param delay
    *           The timeout in milliseconds measured from now.
    * @return A new handle which you can use to unregister with
    *         removeTimeoutListener()
    * @exception XmlBlasterException
    *               if key is null or unknown or invalid because timer elapsed
    *               already
    */
   public abstract Timestamp refreshTimeoutListener(Timestamp key, long delay)
         throws XmlBlasterException;

   /**
    * Checks if key is null -> addTimeoutListener else refreshTimeoutListener()
    * in a thread save way. <br />
    * Note however that your passed key is different from the returned key and
    * you need to synchronize this call to avoid having a stale key (two threads
    * enter this method the same time, the key gets invalid by the first thread
    * and the second passed a stale key as the first thread has not yet returned
    * to update 'key')
    */
   public abstract Timestamp addOrRefreshTimeoutListener(I_Timeout listener,
         long delay, Object userData, Timestamp key) throws XmlBlasterException;

   /**
    * Remove a listener before the timeout happened.
    * <p />
    * 
    * @param key
    *           The timeout handle you received by a previous
    *           addTimeoutListener() call.
    */
   public abstract void removeTimeoutListener(Timestamp key);

   /**
    * Is this handle expired?
    * <p />
    * 
    * @param key
    *           The timeout handle you received by a previous
    *           addTimeoutListener() call<br />
    * @return true/false
    */
   public abstract boolean isExpired(Timestamp key);

   /**
    * How long to my timeout.
    * <p />
    * 
    * @param key
    *           The timeout handle you received by a previous
    *           addTimeoutListener() call.
    * @return Milliseconds to timeout, or -1 if not known.
    */
   public abstract long spanToTimeout(Timestamp key);

   /**
    * How long am i running.
    * <p />
    * 
    * @param key
    *           The timeout handle you received by a previous
    *           addTimeoutListener() call.
    * @return Milliseconds since creation, or -1 if not known.
    */
   public abstract long elapsed(Timestamp key);

   /**
    * Access the end of life span.
    * <p />
    * 
    * @param key
    *           The timeout handle you received by a previous
    *           addTimeoutListener() call.
    * @return Time in milliseconds since midnight, January 1, 1970 UTC or -1 if
    *         not known.
    */
   public abstract long getTimeout(Timestamp key);

   /**
    * Reset all pending timeouts.
    */
   public abstract void removeAll();

   /**
    * Reset and stop the Timeout manager thread.
    */
   public abstract void shutdown();

}