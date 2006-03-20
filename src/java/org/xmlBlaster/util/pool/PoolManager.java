/*------------------------------------------------------------------------------
Name:      PoolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.pool;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.def.ErrorCode;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;


/**
 * A little framework to handle a pool of limited resources.
 * <p />
 * You can use this pool as a base class handling of your limited resources
 * like a 'JDBC connection pool' or a 'thread pool'.
 * <p />
 * The important attributes of a resource are gathered in the <code>ResourceWrapper</code> class.
 * <p />
 * You can easily handle bigger number of resources with good performance.
 * <p />
 * To find out how to use it see the TestPool example in the main() method of this class.
 * <p />
 * <pre>
 *    State chart of resource handling:
 *
 *      +<------- reserve() if(numIdle==0) ---------------------+
 *      |                                                       |
 *      |     +<- reserve() --+        +<-preReserve() *-+      |
 *      |     | if(numIdle>0) |        |                 |      |
 *      |     |               |        |                 |      |
 *    #########               ##########                 ##########
 *   #         #             #          #               #          #
 *   #  busy   #             #  idle    #               #  undef   #
 *   #         #             #          #               #          #
 *    #########               ##########                 ##########
 *      |  |  |               | |  | | |                 | |  |  |
 *      |  |  |   Explicit    | |  | | |   Explicit      | |  |  |
 *      |  |  +-- release() ->+ |  | | +-- erase() ----->+ |  |  |
 *      |  |                    |  | |                     |  |  |
 *      |  +busyToIdleTimeout()>+  | +idleToEraseTimeout()>+  |  |
 *      |                          |                          |  |
 *      |                          +-- erase on max cycles *->+  |
 *      |                                                        |
 *      +--------- busyToEraseTimeout() since creation *-------->+
 *
 * </pre>
 * There are three states:
 * <ul>
 *    <li>busy: The resource is in use
 *    </li>
 *    <li>idle: The resource is allocated and ready but not in use
 *    </li>
 *    <li>undef: The resource is not yet allocated or it is erased again
 *    </li>
 * </ul>
 * <p>
 * Note that state transitions marked with '*' are not yet implemented.
 * If you need one of them, code it and contribute it please.
 * <p>
 * You can choose which states you wish for your resource and how timeouts
 * are used to handle state transitions.<br />
 * </p>
 * <p>
 * For example if you want to pool user login sessions and want to do
 * an auto logout after 60 minutes, you would use the busyToIdleTimeout and set it to 60*60*1000.<br />
 * If a user is active you can refresh the session with busyRefresh().<br />
 * Often you want to use your own generated sessionId as the primary key
 * for this resource, you can pass it as the instanceId argument to reserve(sessionId).<br />
 * (See example [2] in this main() method)
 * </p>
 * <p>
 * If you want to pool JDBC connections, reserve() a connection before you do your query
 * and release() it immediately again. If you
 * want to close connections after peak usage, you could set a idleToEraseTimeout,
 * to erase your JDBC connection after some time not used (reducing the current pool size).<br />
 * Note that in this example the connections are anonymous (all are logged in to the database
 * with the same user name), it is not important which you receive.<br />
 * (See example [1] in this main() method)
 * </p>
 * <p>
 * For an implementation example see TestPoolManager.java
 * </p>
 *
 * <p>This code is derived from the org.jutils.pool package.</p>
 * @author "Marcel Ruff" <ruff@swand.lake.de>
 * @see org.xmlBlaster.util.pool.ResourceWrapper
 * @see org.xmlBlaster.test.classtest.TestPoolManager
 */
public final class PoolManager
{
   /** Nice, unique name for logging output */
   private String ME = "PoolManager";
   /** A nice name for the generated id */
   private String poolName = "Resource";
   /** The callback into the using application, which needs to implement the interface I_PoolManager */
   private I_PoolManager callback = null;
   /** Holds busy resources */
   private java.util.Hashtable busy = null;
   /** Holds free resources */
   private java.util.Vector idle = null;
   /** Default maximum pool size (number of resources) */
   private int maxInstances = 100;
   /** Default maximum busy time of a resource, on timeout it changes state from 'busy' to 'idle' */
   private long busyToIdleTimeout = 0;
   /* Default maximum life span of a resource, on timeout it changes state from 'busy' to 'undef' (it is deleted) */
   //private long busyToEraseTimeout = 0;
   /** Default maximum idle span of a resource, on timeout it changes state from 'idle' to 'undef' (it is deleted) */
   private long idleToEraseTimeout = 0;
   /** Unique counter to generate IDs */
   private long counter = 1;
   /** Use this constant with the reserve() method, the PoolManager uses the hashCode() of your object */
   public final static String USE_HASH_CODE = "h";
   /** Use this constant with the reserve() method, the PoolManager uses the toString() of your object, if no toString() exists the object reference is used, e.g. "oracle.jdbc.driver.OracleConnection@443226" */
   public final static String USE_OBJECT_REF = "o";
   /** Use this constant with the reserve() method, the PoolManager generates a random, unique in universe session ID */
   public final static String GENERATE_RANDOM = "r";
   /** Triggers transitions */
   private Timeout transitionTimer;


   private final Object meetingPoint = new Object();

   /**
   * Create a new pool instance with the desired behavior.
   * <p />
   * Implementation:<br />
   * There are two list in this class
   * <ul>
   *    <li>busy: Holds resources which are currently busy</li>
   *    <li>idle: Holds resources in the free pool, waiting to be reused</li>
   * </ul>
   * If you ask for a new resource, this will move into the busy list<br />
   * Is a resource released or a specified timeout occurred, it is moved into the idle list.
   * <p />
   * @param poolName       A nice name for this pool manager instance.
   * @param callback       The interface 'I_PoolManager' callback
   * @param maxInstances   Max. number of resources in this pool.
   * @param busyToIdleTimeout Max. busy time of this resource in milli seconds<br />
   *                       On timeout it changes state from 'busy' to 'idle'.<br />
   *                       You can overwrite this value for each resource instance<br />
   *                       0 switches it off<br />
   *                       You get called back through I_PoolManager.busyToIdle() on timeout
   *                       allowing you to code some specific handling.
   * @param idleToEraseTimeout Max. idle time span of this resource in milli seconds<br />
   *                     On timeout it changes state from 'idle' to 'undef' (it is deleted).<br />
   *                     You can overwrite this value for each resource instance<br />
   *                     0 switches it off<br />
   *                     You get called back through I_PoolManager.toErased() on timeout
   *                     allowing you to code some specific handling.
   */
   public PoolManager(String poolName, I_PoolManager callback, int maxInstances, long busyToIdleTimeout, long idleToEraseTimeout) {
      this.poolName = poolName; // e.g. "SessionId"
      this.callback = callback;
      this.ME = poolName + "-PoolManager"; // e.g. "SessionId-PoolManager"
      this.setMaxInstances(maxInstances);
      this.setBusyToIdleTimeout(busyToIdleTimeout);
      this.setIdleToEraseTimeout(idleToEraseTimeout);
      this.busy = new java.util.Hashtable(maxInstances);
      this.idle = new java.util.Vector(maxInstances);
      /*
        if (busyToIdleTimeout > 0 || idleToEraseTimeout > 0) {
       System.err.println(ME + ": Created PoolManager with a maximum of " + maxInstances + " instances" +
        " and a default busy recycling-timeout of " + TimeHelper.millisToNice(this.busyToIdleTimeout) +
        " and a default idle recycling-timeout of " + TimeHelper.millisToNice(this.idleToEraseTimeout));
        }
        else
       System.err.println(ME + ": Created PoolManager with a maximum of " + maxInstances + " instances without auto-recycling.");
        */
   }

   public Timeout getTransistionTimer() {
      if (this.transitionTimer == null) {
         synchronized(this) {
            if (this.transitionTimer == null)
               this.transitionTimer = new Timeout("XmlBlaster.PoolManagerTimer");
         }
      }
      return this.transitionTimer;
   }

   /**
   * Set the maximum pool size.
   * @param maxInstances How many resources are allowed
   */
   private void setMaxInstances(int maxInstances) {
      this.maxInstances = maxInstances;
   }

   /**
   * On timeout, the resource changes state from 'busy' to 'idle'.<br />
   * @param busyToIdleTimeout Max. busy time of this resource in milli seconds<br />
   *                       On timeout it changes state from 'busy' to 'idle'.<br />
   *                       You can overwrite this value for each resource instance<br />
   *                       0 switches it off
   */
   private void setBusyToIdleTimeout(long busyToIdleTimeout) {
      if (busyToIdleTimeout > 0 && busyToIdleTimeout < 100) {
         // System.err.println(ME + ": Setting minimum release timeout from " + busyToIdleTimeout + " to 100 milli seconds.");
         this.busyToIdleTimeout = 100;
      }
      else
         this.busyToIdleTimeout = busyToIdleTimeout;
   }

   /**
   * Set the max. life span of the resources.
   * @param idleToEraseTimeout Max. idle time of this resource in milli seconds<br />
   *                       You can overwrite this value for each resource instance<br />
   *                       0 switches it off
   */
   private void setIdleToEraseTimeout(long idleToEraseTimeout) {
      if (idleToEraseTimeout > 0 && idleToEraseTimeout < 100) {
         // System.err.println(ME + ": Setting minimum idleErase timeout from " + idleToEraseTimeout + " to 100 milli seconds.");
         this.idleToEraseTimeout = 100;
      }
      else
         this.idleToEraseTimeout = idleToEraseTimeout;
   }

   /**
   * Get a new resource.
   * <p />
   * The life span is set to the default value of the pool.
   * <p />
   * The instance Id is random and unique generated.
   * @exception XmlBlasterException Error with random generator
   */
   public ResourceWrapper reserve() throws XmlBlasterException {
      return reserve(busyToIdleTimeout, idleToEraseTimeout, GENERATE_RANDOM);
   }

   /**
   * Get a new resource.
   * <p />
   * The life span is set to the default value of the pool.
   * <p />
   * @param instanceId See description in other reserve() method.
   * @exception XmlBlasterException Error with random generator
   */
   public ResourceWrapper reserve(String instanceId) throws XmlBlasterException {
      return reserve(busyToIdleTimeout, idleToEraseTimeout, instanceId);
   }

   /**
   * Get a new resource.
   *
   * @param localBusyToIdleTimeout Max. busy time of this resource in milli seconds,
   *                       only for this current resource<br />
   *                       Overwrite locally the default<br />
   *                       0 switches busy timeout off
   *
   * @param localIdleToEraseTimeout Max. idle time of this resource in milli seconds,
   *                       only for this current resource<br />
   *                       Overwrite locally the default<br />
   *                       0 switches idle timeout off
   *
   * @param instanceId If given and string length > 1, the delivered ID is used:
   *             If in busy list found, this is returned, else a new is created.<br />
   *             USE_HASH_CODE   - The PoolManager generates a simple one (hashCode())<br />
   *             USE_OBJECT_REF  - The object reference is used<br />
   *             GENERATE_RANDOM - The PoolManager generates a random, unique in universe session ID
   *
   * @return rw The resource handle (always != null)
   *
   * @exception XmlBlasterException Error with random generator
   */
   public ResourceWrapper reserve(long localBusyToIdleTimeout, long localIdleToEraseTimeout, String instanceId) throws XmlBlasterException {
      synchronized (meetingPoint) {
         ResourceWrapper rw = null;
         if (instanceId != null && instanceId.length() > 1) {
            rw = findBusySilent(instanceId);
            if (rw != null) {
               // System.out.println(ME + ": Reconnected to busy resource '" + instanceId + "' ...");
               return rw;
            }
         }
         if (idle.size() > 0) {
            // System.out.println(ME + ": Resource is recycled from idle pool ...");
            rw = (ResourceWrapper) idle.lastElement();
            rw.init(createId(instanceId), rw.getResource(), localBusyToIdleTimeout, localIdleToEraseTimeout);
            swap(rw, true);
            // System.out.println(ME + ": Access on cached resource '" + rw.getInstanceId() + "' granted");
            // dumpState(rw.getInstanceId());
         }
         else
            if (busy.size() >= maxInstances) {
               // CAUTION: ErrorCode.RESOURCE_EXHAUST is a well defined string which may not be changed, it is used from the caller to identify the situation
               XmlBlasterException e = new XmlBlasterException("ResourceExhaust", "Sorry, " + maxInstances + " resources consumed, no more resources available");
               e.changeErrorCode(ErrorCode.RESOURCE_EXHAUST);
               throw e;
            }
            else {
               instanceId = createId(instanceId);
               Object resource = callback.toCreate(instanceId);
               rw = new ResourceWrapper(this, instanceId, resource, localBusyToIdleTimeout, idleToEraseTimeout);
               rw.toBusy();
               busy.put(rw.getInstanceId(), rw);
               // System.out.println(ME + ": Granted access to new resource '" + rw.getInstanceId() + "'");
               // dumpState(rw.getInstanceId());
            }
         return rw;
      }
   }

   /**
   * Release a resource explicitly from 'busy' into the 'idle' pool.
   * @param instanceId The unique resource ID
   * @exception XmlBlasterException
   */
   public void release(String instanceId) throws XmlBlasterException {
      synchronized (meetingPoint) {
         swap(findLow(instanceId), false);
      }
      //System.out.println(ME + ": Resource <" + instanceId + "> released explicitly");
      // dumpState(instanceId);
   }

   /**
   *  Generate a unique resource ID <br>
   *
   *  @instanceId USE_HASH_CODE=null   ResourceWrapper generates a simple ID = resource.hashCode()<br />
   *              "..."                Your supplied ID is used<br />
   *              GENERATE_RANDOM=""   A random ID is generated, unique in universe and over time
   *  @return unique ID or null
   *  @exception XmlBlasterException random generator
   */
   private String createId(String instanceId) throws XmlBlasterException {
      if (instanceId == null || instanceId.equals(GENERATE_RANDOM) == false)
         return instanceId; // ResourceWrapper generates a simple id

      try {
         String ip;
         try {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            ip = addr.getHostAddress();
         }
         catch (Exception e) {
            // System.err.println(ME + ": Can't determin your IP address");
            ip = "localhost";
         }

         // This is a real random, but probably not necessary here:
         // Random random = new java.security.SecureRandom();
         java.util.Random ran = new java.util.Random(); // is more or less currentTimeMillis

         // Note: We should include the process ID from this JVM on this host to be granted unique

         //   <IP-Address>-<InstanceName>-<TimestampMilliSec>-<RandomNumber>-<LocalCounter>
         return ip + "-" + poolName + "-" + System.currentTimeMillis() + "-" + ran.nextInt() + "-" + (counter++);
      }
      catch (Exception e) {
         String text = "Can't generate a unique instanceId: " + e.toString();
         throw new XmlBlasterException("ResourceNoId", text);
      }
   }

   /**
   * Idle - busy swapper.
   */
   private void swap(ResourceWrapper rw, boolean toBusy) {
      if (rw == null) {
         System.err.println("##########INTERNAL PROBLEM IN PollManager");
         return;
      }
      if (toBusy) {
         idle.removeElementAt(idle.size() - 1);
         busy.put(rw.getInstanceId(), rw);
         rw.toBusy();
         callback.idleToBusy(rw.getResource());
         //System.err.println("Swapped to busy:" + rw.getInstanceId());
      }
      else { // recycling ...
         busy.remove(rw.getInstanceId());
         idle.addElement(rw);
         rw.toIdle();
         callback.busyToIdle(rw.getResource());
         //System.err.println("Swapped to idle:" + rw.getInstanceId());
      }
   }

   /**
   * Find a resource in busy list.
   * @param instanceId The unique resource ID
   * @return The handle containing the resource.
   */
   private ResourceWrapper findBusySilent(String instanceId) {
      if (instanceId == null)
         return null;
      return (ResourceWrapper) busy.get(instanceId);
   }

   /**
   * Find a resource in idle list.
   * <p />
   * Note that this is currently a linear search (not high performing).
   * @param instanceId The unique resource ID
   * @return The handle containing the resource.
   */
   private ResourceWrapper findIdleSilent(String instanceId) {
      if (instanceId == null)
         return null;
      for (int ii = 0; ii < idle.size(); ii++) {
         ResourceWrapper rw = (ResourceWrapper) idle.elementAt(ii);
         if (rw.getInstanceId().equals(instanceId))
            return rw;
      }
      return null;
   }

   /**
   * Find a resource in busy list.
   * @param instanceId The unique resource ID
   * @return Handle containing resource
   * @exception XmlBlasterException "ResourceNotFound"
   */
   private ResourceWrapper findLow(String instanceId) throws XmlBlasterException {
      if (instanceId == null) {
         String text = "Your resource ID is null";
         throw new XmlBlasterException("ResourceNotFound", text);
      }
      ResourceWrapper rw = findBusySilent(instanceId);
      if (rw == null) {
         String text = "Resource '" + instanceId + "' is invalid, timed out?";
         throw new XmlBlasterException("ResourceNotFound", text);
      }
      return rw;
   }

   /**
   * Test if the resource is busy.
   *
   * @param instanceId The unique resource ID
   * @return true - is in 'busy' state<br />
   *         false - is in 'idle' state or unknown
   */
   public boolean isBusy(String instanceId) {
      ResourceWrapper rw = findBusySilent(instanceId);
      return (rw != null);
   }

   /**
   * Restart countdown for resource life cycle.
   * <p />
   * Rewind the timeout for 'busy' to 'idle' transition.
   * @param instanceId The unique resource ID
   * @exception XmlBlasterException ResourceNotFound
   */
   public void busyRefresh(String instanceId) throws XmlBlasterException {
      synchronized (meetingPoint) {
         ResourceWrapper rw = findLow(instanceId);
         rw.touchBusy();
      }
   }

   /**
   * Number of resources in the 'busy' list.
   * @return Number of 'busy' resources
   */
   public int getNumBusy() {
      return busy.size();
   }

   /**
   * Number of resources in the 'idle' list.
   * @return Number of 'idle' resources
   */
   public int getNumIdle() {
      return idle.size();
   }

   /**
   * Cleanup.
   */
   protected void finalize() throws Throwable {
      destroy();
   }

   /**
   * Dump the current state of this pool.
   */
   public String getState() {
      return "Overview for '" + ME + "':" + " BUSY=" + busy.size() + " IDLE=" + idle.size() + " MAX_AVAILABLE=" + (maxInstances - busy.size());
   }

   /**
   * Dump state of this object into a XML ASCII string.
   * <p />
   * @return internal state of this PoolManager as a XML ASCII string
   */
   public final String toXml() {
      return toXml((String) null);
   }

   /**
   * Dump state of this object into a XML ASCII string.
   * <p />
   * @param extraOffset indenting of tags for nice output
   * @return internal state of this PoolManager as a XML ASCII string
   */
   public final String toXml(String extraOffset) {
      StringBuffer buf = new StringBuffer();
      String offset = "\n";
      if (extraOffset == null)
         extraOffset = "";
      offset += extraOffset;
      synchronized (meetingPoint) {
         buf.append(offset).append("<").append(ME).append(" maxInstances='").append(maxInstances);
         buf.append("' busyToIdleTimeout='").append(busyToIdleTimeout);
         buf.append("' idleToEraseTimeout='").append(idleToEraseTimeout).append("'>");
         buf.append(offset).append("   <busy num='").append(busy.size()).append("'>");
         for (Enumeration e = busy.elements(); e.hasMoreElements();) {
            ResourceWrapper rw = (ResourceWrapper) e.nextElement();
            buf.append(rw.toXml(extraOffset + "   "));
         }
         buf.append(offset).append("   </busy>");
         buf.append(offset).append("   <idle num='").append(idle.size()).append("'>");
         for (int ii = 0; ii < idle.size(); ii++) {
            ResourceWrapper rw = (ResourceWrapper) idle.elementAt(ii);
            buf.append(rw.toXml(extraOffset + "   "));
         }
         buf.append(offset).append("   </idle>");
         buf.append(offset).append("</" + ME + ">");
      }
      return buf.toString();
   }

   /**
   * Recycle busy resource after timeout.
   * <p />
   * This method is a callback from ResourceWrapper
   * @parameter userData The ResourceWrapper object
   */
   void timeoutBusyToIdle(ResourceWrapper rw) {
      // System.out.println(ME + ": Resource '" + rw.getInstanceId() + "' is recycled from 'busy' state, was not in use since " + TimeHelper.millisToNice(rw.busyElapsed()));
      synchronized (meetingPoint) {
         swap(rw, false);
      }
      // dumpState(rw.getInstanceId());
   }

   /**
   * Erase an idle resource after timeout.
   * <p />
   * This method is a callback from ResourceWrapper
   * @parameter userData The ResourceWrapper object
   */
   void timeoutIdleToErase(ResourceWrapper rw) {
      // System.out.println(ME + ": Resource '" + rw.getInstanceId() + "' is erased from 'idle' state after timeout");
      erase(rw);
   }

   /**
   * Explicitly remove a resource.
   * <p />
   * It is erased if it is found in the idle list or even when it is busy.
   * @param instanceId The unique resource ID
   */
   public void erase(String instanceId) {
      if (instanceId == null || instanceId.length() < 1)
         return;
      synchronized (meetingPoint) {
         ResourceWrapper rw = findBusySilent(instanceId);
         if (rw == null) {
            rw = findIdleSilent(instanceId);
         }
         erase(rw);
      }
   }

   /**
   * Remove a resource.
   * <p />
   * Remove it from the 'idle' or the 'busy' list.
   * @param rw The resource wrapper object
   */
   private void erase(ResourceWrapper rw) {
      if (rw == null)
         return;
      synchronized (meetingPoint) {
         if (busy.get(rw.getInstanceId()) != null) {
            swap(rw, false); // move to idle ... calls busyToIdle() callback
            //busy.remove(rw.getInstanceId());
         }
         idle.remove(rw);
         callback.toErased(rw.getResource());
         rw.destroy();
      }
      // dumpState(rw.getInstanceId());
   }

   /**
   * Cleanup everything.
   */
   public void destroy() {
      // System.out.println(ME + ": Freeing all resources, " + busy.size() + " are busy and " + idle.size() + " idle.");
      synchronized (meetingPoint) {
         java.util.Enumeration e = busy.elements();
         for (; e.hasMoreElements();) {
            erase((ResourceWrapper) e.nextElement());
         }
         busy.clear();
         for (int ii = 0; ii < idle.size(); ii++) {
            ResourceWrapper rw = (ResourceWrapper) idle.elementAt(ii);
            callback.toErased(rw.getResource());
            rw.destroy();
         }
         idle.removeAllElements();
      }
   }
}
