/*------------------------------------------------------------------------------
Name:      PoolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic handling of a pool of limited resources
Version:   $Id: PoolManager.java,v 1.5 2000/06/01 13:29:02 ruff Exp $
           $Source: /opt/cvsroot/xmlBlaster/src/java/org/xmlBlaster/util/Attic/PoolManager.java,v $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;


/**
 * A little framework to handle a pool of limited resources.
 * <p />
 * You can use this pool as a base class for handling of your limited resources
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
 *      |     +<- reserve() --+        +<-preReserve() --+      |
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
 *      |  +-releaseTimeout() ->+  | +-idleEraseTimeout()->+  |  |
 *      |                          |                          |  |
 *      |                          +-- erase on max cycles -->+  |
 *      |                                                        |
 *      +----------- busyEraseTimeout() since creation --------->+
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
 * You can choose which states you wish for your resource and how timeouts
 * are used to handle state transitions.<br />
 * </p>
 * <p>
 * For example if you want to pool user login sessions and want to do
 * an auto logout after 60 minutes, you would use the releaseTimeout and set it to 60*60*1000.<br />
 * If a user is active you can refresh the session with releaseRefresh().<br />
 * Often you want to use your own generated sessionId as the primary key
 * for this resource, you can pass it as the instanceId argument to reserve(sessionId).
 * </p>
 * <p>
 * If you want to pool JDBC connections, reserve() a connection before you do your query
 * and release() it immediately again. If you
 * want to close connections after peak usage, you could set a idleEraseTimeout,
 * to erase your JDBC connection after some time not used (reducing the current pool size).<br />
 * Not that in this example the connections are anonymous (all are logged in to the database
 * with the same user name), it is not important which you receive.
 * </p>
 *
 *
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.util.ResourceWrapper
 */
public class PoolManager implements I_Timeout
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
   private long releaseTimeout = 0;
   /** Default maximum life span of a resource, on timeout it changes state from 'busy' to 'undef' (it is deleted) */
   private long busyEraseTimeout = 0;
   /** Unique counter to generate IDs */
   private long counter = 1;


   /**
    * Create a new pool instance with the desired behaviour.
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
    * @param poolName       A nice name for this resource
    * @param maxInstances   Max. number of resources in this pool.
    * @param releaseTimeout Max. busy time of this resource in milli seconds<br />
    *                       On timeout it changes state from 'busy' to 'idle'.<br />
    *                       You can overwrite this value for each resource instance<br />
    *                       0 switches it off
    */
   public PoolManager(String poolName, I_PoolManager callback, int maxInstances, long releaseTimeout/*, long busyEraseTimeout*/)
   {/*
    * @param busyEraseTimeout Max. life span of this resource in milli seconds<br />
    *                     On timeout it changes state from 'busy' or 'idle' to 'undef' (it is deleted).<br />
    *                     You can overwrite this value for each resource instance<br />
    *                     0 switches it off
    */
      this.poolName = poolName;  // e.g. "SessionId"
      this.callback = callback;
      this.ME = poolName + "-PoolManager"; // e.g. "SessionId-PoolManager"
      this.setMaxInstances(maxInstances);
      this.setReleaseTimeout(releaseTimeout);
      //this.setBusyEraseTimeout(busyEraseTimeout);
      this.busy = new java.util.Hashtable(maxInstances);
      this.idle = new java.util.Vector(maxInstances);
      if (releaseTimeout > 0)
         Log.info(ME, "Created PoolManager with a maximum of " + maxInstances + " instances and a default recycling-timeout of " + TimeHelper.millisToNice(this.releaseTimeout));
      else
         Log.info(ME, "Created PoolManager with a maximum of " + maxInstances + " instances without auto-recycling.");
   }


   /**
    * Set the maximum pool size.
    * @param maxInstances How many resources are allowed
    */
   private void setMaxInstances(int maxInstances)
   {
      this.maxInstances = maxInstances;
      if (Log.TRACE) Log.trace(ME, "Max. number of resource instances set to " + maxInstances);
   }


   /**
    * On timeout, the resource changes state from 'busy' to 'idle'.<br />
    * @param releaseTimeout Max. busy time of this resource in milli seconds<br />
    *                       On timeout it changes state from 'busy' to 'idle'.<br />
    *                       You can overwrite this value for each resource instance<br />
    *                       0 switches it off
    */
   private void setReleaseTimeout(long releaseTimeout)
   {
      if (releaseTimeout > 0 && releaseTimeout < 100) {
         Log.warning(ME, "Setting minimum release timeout from " + releaseTimeout + " to 100 milli seconds.");
         this.releaseTimeout = 100;
      }
      else
         this.releaseTimeout = releaseTimeout;
      if (Log.TRACE) Log.trace(ME, "Set default life span to " + TimeHelper.millisToNice(this.releaseTimeout));
   }


   /**
    * Set the max. life span of the resources.
    * @param busyEraseTimeout Max. busy time of this resource in milli seconds<br />
    *                       You can overwrite this value for each resource instance<br />
    *                       0 switches it off
    */
    /*
   private void setBusyEraseTimeout(long busyEraseTimeout)
   {
      if (busyEraseTimeout > 0 && busyEraseTimeout < 100) {
         Log.warning(ME, "Setting minimum busyErase timeout from " + busyEraseTimeout + " to 100 milli seconds.");
         this.busyEraseTimeout = 100;
      }
      else
         this.busyEraseTimeout = busyEraseTimeout;
      if (Log.TRACE) Log.trace(ME, "Set default life span to " + TimeHelper.millisToNice(this.busyEraseTimeout));
   }
   */


   /**
    * Get a new resource. 
    * <p />
    * The life span is set to the default value of the pool.
    * <p />
    * The instance Id random and unique generated.
    * @exception XmlBlasterException Error with random generator
    */
   public ResourceWrapper reserve() throws XmlBlasterException
   {
      return reserve(releaseTimeout, "");
   }


   /**
    * Get a new resource.
    * <p />
    * The life span is set to the default value of the pool.
    * <p />
    * @parameter instanceId See description in other reserve() method.
    * @exception XmlBlasterException Error with random generator
    */
   public ResourceWrapper reserve(String instanceId) throws XmlBlasterException
   {
      return reserve(releaseTimeout, instanceId);
   }


   /**
    * Get a new resource.
    *
    * @param releaseTimeout Max. busy time of this resource in milli seconds, only for this current resource<br />
    *                       Overwrite locally the default<br />
    *                       0 switches busy timeout off
    *
    * @param instanceId if given, the delivered ID is used: If in busy found, this is returned else a new is created.<br />
    *             null - The PoolManager generates a simple one (hashCode())<br />
    *             ""   - The PollManager generates a random, unique in universe session ID
    *
    * @return rw The resource handle (always != null, otherwise an exception is thrown)
    *
    * @exception XmlBlasterException Error with random generator
    */
   synchronized public ResourceWrapper reserve(long localReleaseTimeout, String instanceId) throws XmlBlasterException
   {
      ResourceWrapper rw = null;
      if (instanceId != null && instanceId.length() > 0) {
         rw = findSilent(instanceId);
         if (rw != null) {
            if (Log.TRACE) Log.trace(ME, "Reconnected to busy resource '" + instanceId + "' ...");
            return rw;
         }
      }
      if (idle.size() > 0) {
          if (Log.TRACE) Log.trace(ME, "Resource is receycled from idle pool ...");
          rw = (ResourceWrapper)idle.lastElement();
          rw.init(createId(instanceId), rw.getResource(), localReleaseTimeout);
          swap(rw, true);
          if (Log.TRACE) Log.trace(ME, "Access on cached resource '" + rw.getInstanceId() + "' granted");
          dumpState(rw.getInstanceId());
      }
      else if (busy.size() >= maxInstances) {
         Log.error(ME, "Sorry, " + maxInstances + " resources consumed, no more resources available");
         throw new XmlBlasterException("ResourceExhaust", "Sorry, " + maxInstances + " resources consumed, no more resources available");
      }
      else {
         if (Log.TRACE) Log.trace(ME, "Creating new ResourceWrapper instance ...");
         instanceId = createId(instanceId);
         Object resource = callback.toCreate(instanceId);
         rw = new ResourceWrapper(this, instanceId, resource, localReleaseTimeout);
         busy.put(rw.getInstanceId(), rw);
         Log.info(ME, "Granted access to new resource '" + rw.getInstanceId() + "'");
         dumpState(rw.getInstanceId());
      }
      return rw;
   }


    /**
     * Set your resource object.
     * @param rw       The resource handle
     * @param resource Your object
     */
    public void setResource(ResourceWrapper rw, Object resource)
    {
       rw.setResource(resource);
       busy.put(rw.getInstanceId(), rw);
    }


   /**
    * Free a resource explicitly.
    * @param instanceId The unique resource ID
    * @exception XmlBlasterException
    */
   synchronized public void release(String instanceId) throws XmlBlasterException
   {
      swap(findLow(instanceId), false);
      if (Log.TRACE) Log.trace(ME, "Resource <" + instanceId + "> freed explicitly");
      dumpState(instanceId);
   }


   /**
    *  Generate a unique resource ID <br>
    *
    *  @instanceId null   ResourceWrapper generates a simple ID = resource.hashCode()<br />
    *              "..."  Your supplied ID is used<br />
    *              ""     A random ID is generated, unique in universe and over time
    *  @return unique ID or null
    *  @exception XmlBlasterException random generator
    */
   public String createId(String instanceId) throws XmlBlasterException
   {
      if (instanceId == null)
         return null;       // ResourceWrapper generates a simple id = resource.hashCode()

      if (instanceId.length() > 1)
         return instanceId; // User supplied ID (e.g. from Servlet.session)

      try {
         String ip;
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            ip = addr.getHostAddress();
         } catch (Exception e) {
            Log.warning(ME, "Can't determin your IP address");
            ip = "localhost";
         }

         // This is a real random, but probably not necessary here:
         // Random random = new java.security.SecureRandom();
         java.util.Random ran = new java.util.Random();  // is more or less currentTimeMillis

         // Note: We should include the process ID from this JVM on this host to be granted unique

         //   <IP-Address>-<InstanceName>-<TimestampMilliSec>-<RandomNumber>-<LocalCounter>
         return ip + "-" + poolName + "-" + System.currentTimeMillis() + "-" +  ran.nextInt() + "-" + (counter++);
      }
      catch (Exception e) {
         String text = "Can't generate a unique instanceId: " + e.toString();
         Log.error(ME, text);
         throw new XmlBlasterException("ResourceNoId", text);
      }
   }


   /**
    * Synchronized idle - busy swapper.
    */
   private void swap(ResourceWrapper rw, boolean toBusy)
   {
      if (toBusy) {
          idle.removeElementAt(idle.size() - 1);
          callback.idleToBusy(rw.getResource());
          busy.put(rw.getInstanceId(), rw);
      }
      else  { // recycling ...
         busy.remove(rw.getInstanceId());
         callback.busyToIdle(rw.getResource());
         rw.cleanup();
         idle.addElement(rw);
      }
   }


   /**
    * Find a resource in busy list.
    * @param instanceId The unique resource ID
    * @return The handle containing the resource.
    */
   private ResourceWrapper findSilent(String instanceId)
   {
      if (instanceId == null) return null;
      return (ResourceWrapper)busy.get(instanceId);
   }


   /**
    * Find a resource in busy list.
    * @param instanceId The unique resource ID
    * @return Handle containing resource
    * @exception XmlBlasterException "ResourceNotFound"
    */
   private ResourceWrapper findLow(String instanceId) throws XmlBlasterException
   {
      if (instanceId == null) {
         String text = "Your resource ID is null";
         Log.error(ME, text);
         throw new XmlBlasterException("ResourceNotFound", text);
      }
      ResourceWrapper rw = findSilent(instanceId);
      if (rw == null) {
         String text = "Resource '" + instanceId + "' is invalid, timed out?";
         Log.error(ME, text);
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
   public boolean isBusy(String instanceId)
   {
      ResourceWrapper rw = findSilent(instanceId);
      return (rw != null);
   }


   /**
    * Restart countdown for resource life cycle. 
    * <p />
    * Rewind the timeout for 'busy' to 'idle' transition.
    * @param instanceId The unique resource ID
    * @exception XmlBlasterException ResourceNotFound
    */
   public void releaseRefresh(String instanceId) throws XmlBlasterException
   {
      ResourceWrapper rw = findLow(instanceId);
      rw.touch();
      if (Log.TRACE) Log.trace(ME, "Refreshing life of '" + instanceId + "'");
   }


   /**
    * Find a 'busy' resource.
    *
    * @param instanceId The unique resource ID
    * @exception XmlBlasterException ResourceNotFound
    *//*
   public ResourceWrapper find(String instanceId) throws XmlBlasterException
   {
     ResourceWrapper rw = findLow(instanceId);
     if (Log.TRACE) Log.trace(ME, "Erfolgreich mit Resource '" + instanceId + "' wiederverbunden");
     return rw;
   }    */


   /**
    * Find a 'busy' resource and change its timeout life cycle.
    * @param instanceId The unique resource ID
    * @param timeout New timeout (for the receylce thread).
    *                0: No automatic recycling
    * @exception XmlBlasterException ResourceNotFound
    */
   public void setTimeout(String instanceId, long timeout) throws XmlBlasterException
   {
     ResourceWrapper rw = findLow(instanceId);
     rw.setTimeout(timeout*1000);
     if (Log.TRACE) Log.trace(ME, "setTimeout(" + timeout + "sec) for '" + instanceId + "'");
   }


   /**
    * @return Number of 'busy' resources
    */
   public int getNumBusy()
   {
      return busy.size();
   }


   /**
    * @return Number of 'idle' resources
    */
   public int getNumIdle()
   {
      return idle.size();
   }


   /**
    * Cleanup everything.
    */
   public void cleanup()
   {
      Log.info(ME, "Freeing all resources, " + busy.size() + " are busy and " + idle.size() + " idle.");
      for (int ii=0; ii<idle.size(); ii++)
         callback.toErased(((ResourceWrapper)idle.elementAt(ii)).getResource());
      idle.removeAllElements();
      java.util.Enumeration e = busy.elements();
      for (; e.hasMoreElements() ;)
         callback.toErased(((ResourceWrapper)e.nextElement()).getResource());
      busy.clear();
      if (Log.TRACE) Log.trace(ME, busy.size() + " busy resources and " + idle.size() + " are idle but allocated.");
   }


   /**
    * Cleanup.
    */
   protected void finalize() throws Throwable
   {
      if (Log.CALLS) Log.calls(ME, "Entering finalize() ...");
      cleanup();
   }


   /**
    * Dump the current state of this pool.
    */
   public void dumpState(String instanceId)
   {
      if (Log.TRACE) {
         Log.trace(ME,  "Overview for '" + instanceId + "':" +
                        " BUSY=" + busy.size() +
                        " IDLE=" + idle.size() +
                        " MAX_AVAILABLE=" + (maxInstances-busy.size()));
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <p />
    * @return internal state of this PoolManager as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <p />
    * @param extraOffset indenting of tags for nice output
    * @return internal state of this PoolManager as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer buf = new StringBuffer();
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      buf.append(offset).append("<").append(ME).append(" maxInstances='").append(maxInstances);
      buf.append("' releaseTimeout='").append(releaseTimeout);
      buf.append("' busyEraseTimeout='").append(busyEraseTimeout).append("'>");

      buf.append(offset).append("   <busy num='").append(busy.size()).append(">");
      for (Enumeration e = busy.elements() ; e.hasMoreElements() ;) {
         ResourceWrapper rw = (ResourceWrapper)e.nextElement();
         buf.append(rw.toXml("   "));
      }
      buf.append(offset).append("   </busy>");

      buf.append(offset).append("   <idle num='").append(idle.size()).append(">");
      for (int ii=0; ii<idle.size(); ii++) {
         ResourceWrapper rw = (ResourceWrapper)idle.elementAt(ii);
         buf.append(rw.toXml("   "));
      }
      buf.append(offset).append("   </idle>");

      buf.append(offset).append("</" + ME + ">");
      return buf.toString();
   }


   /**
    * Recycle resource after timeout.
    * <p />
    * This method is a callback through interface I_Timeout.
    * @parameter userData The ResourceWrapper object or receycle
    */
   public void timeout(Object userData)
   {
      if (Log.CALLS) Log.calls(ME, "Entering timeout() ...");
      ResourceWrapper rw = (ResourceWrapper)userData;
      Log.warning(ME, "Resource '" + rw.getInstanceId() + "' is receycled, was not in use since " + TimeHelper.millisToNice(rw.elapsed()));
      swap(rw, false);
      dumpState(rw.getInstanceId());
   }


   /**
    * For testing only.
    * <p />
    * Invoke: java org.xmlBlaster.util.PoolManager
    */
   public static void main(String[] args) {
      final String ME = "TestPool";
      Log.setLogLevel(args); // initialize log level and xmlBlaster.property file

      /**
       * This class is usually a UserSession object or a JDBC connection object
       * or whatever resource you want to handle
       */
      class TestResource {
         String name;
         String instanceId;
         boolean isBusy;
         boolean isErased = false;
         public TestResource(String name, String instanceId, boolean isBusy) {
            this.name = name;
            this.instanceId = instanceId;
            this.isBusy = isBusy;
         }
      }

      /**
       * This class does the resource pooling for TestResource,
       * with the help of PoolManager
       */
      class TestPool implements I_PoolManager {
         private int counter=0;
         PoolManager poolManager;
         TestPool() {
            poolManager = new PoolManager(ME, this, 3, 2000);
         }

         // These four methods are callbacks from PoolManager ...
         public void idleToBusy(Object resource) {
            TestResource rr = (TestResource)resource;
            Log.info(ME, "Entering idleToBusy(" + rr.name + ") ...");
            rr.isBusy = true;  // you could do some re-initialization here ...
         }
         public void busyToIdle(Object resource) {
            TestResource rr = (TestResource)resource;
            Log.info(ME, "Entering busyToIdle(" + rr.name + ") ...");
            rr.isBusy = false; // you could do some coding here ...
         }
         public Object toCreate(String instanceId) throws XmlBlasterException {
            TestResource rr = new TestResource("TestResource-" + (counter++), instanceId, true);
            Log.info(ME, "Entering toCreate(instanceId='" + instanceId + "', " + rr.name + ") ...");
            return rr;
         }
         public void toErased(Object resource) {
            TestResource rr = (TestResource)resource;
            Log.info(ME, "Entering toErased(" + rr.name + ") ...");
            rr.isErased = true;
         }

         // These methods are used by your application to get a recource ...
         TestResource reserve() { return reserve(""); }
         TestResource reserve(String instanceId) {
            Log.info(ME, "Entering reserve(" + instanceId + ") ...");
            try {
               ResourceWrapper rw = (ResourceWrapper)poolManager.reserve(instanceId);
               TestResource rr = (TestResource)rw.getResource();
               rr.instanceId = rw.getInstanceId(); // remember the generated unique id
               return rr;
            }
            catch (XmlBlasterException e) {
               Log.warning(ME, "Caught exception in reserve(): " + e.reason);
               return null;
            }
         }
         void release(TestResource rr) {
            Log.info(ME, "Entering release() ...");
            try {
               poolManager.release(rr.instanceId);
            }
            catch (XmlBlasterException e) {
               Log.warning(ME, "Caught exception in release(): " + e.reason);
            }
         }
      }

      // And now test it ...

      // [1] Test with generated instance ID
      if (true) {
         Log.info(ME, "\nStarting TEST 1 ...");
         TestPool testPool = new TestPool();
         TestResource r0 = testPool.reserve();
         TestResource r1 = testPool.reserve();
         TestResource r2 = testPool.reserve();
         testPool.reserve();
         if (testPool.poolManager.getNumBusy() != 3 || testPool.poolManager.getNumIdle() != 0)
            Log.panic(ME, "TEST 1.1 FAILED: Wromg number of busy/idle resources");
         testPool.release(r0);
         if (testPool.poolManager.getNumBusy() != 2 || testPool.poolManager.getNumIdle() != 1)
            Log.panic(ME, "TEST 1.2 FAILED: Wromg number of busy/idle resources");
         testPool.reserve();
         testPool.release(r2);
         Log.plain(ME, testPool.poolManager.toXml());

         // The resources are swapped to idle in 2 seconds, lets wait 3 seconds ...
         try { Thread.currentThread().sleep(3000); } catch( InterruptedException i) {}
         if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 3)
            Log.panic(ME, "TEST 1.3 FAILED: Wromg number of busy/idle resources");
         Log.plain(ME, testPool.poolManager.toXml());
         testPool.reserve();
         if (testPool.poolManager.getNumBusy() != 1 || testPool.poolManager.getNumIdle() != 2)
            Log.panic(ME, "TEST 1.4 FAILED: Wromg number of busy/idle resources");

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         Log.plain(ME, testPool.poolManager.toXml());
      }


      // [2] Test with supplied instance ID
      if (true) {
         Log.info(ME, "\nStarting TEST 2 ...");
         TestPool testPool = new TestPool();
         TestResource r0 = testPool.reserve("ID-0");
         TestResource r1 = testPool.reserve("ID-1");
         TestResource r2 = testPool.reserve("ID-2");
         r2 = testPool.reserve("ID-2");
         r2 = testPool.reserve("ID-2");
         r2 = testPool.reserve("ID-2");
         if (testPool.poolManager.getNumBusy() != 3 || testPool.poolManager.getNumIdle() != 0)
            Log.panic(ME, "TEST 2.1 FAILED: Wromg number of busy/idle resources");
         testPool.reserve("ID-3");
         testPool.release(r0);
         if (testPool.poolManager.getNumBusy() != 2 || testPool.poolManager.getNumIdle() != 1)
            Log.panic(ME, "TEST 2.2 FAILED: Wromg number of busy/idle resources");
         testPool.reserve("ID-4");
         testPool.release(r2);
         Log.plain(ME, testPool.poolManager.toXml());

         // The resources are swapped to idle in 2 seconds, lets wait 3 seconds ...
         try { Thread.currentThread().sleep(3000); } catch( InterruptedException i) {}
         if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 3)
            Log.panic(ME, "TEST 2.3 FAILED: Wromg number of busy/idle resources");
         Log.plain(ME, testPool.poolManager.toXml());
         testPool.reserve("ID-5");
         if (testPool.poolManager.getNumBusy() != 1 || testPool.poolManager.getNumIdle() != 2)
            Log.panic(ME, "TEST 2.4 FAILED: Wromg number of busy/idle resources");

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {}
         Log.plain(ME, testPool.poolManager.toXml());
      }
   }
}

