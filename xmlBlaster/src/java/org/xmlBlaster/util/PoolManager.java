/*------------------------------------------------------------------------------
Name:      PoolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic handling of a pool of limited resources
Version:   $Id: PoolManager.java,v 1.1 2000/05/27 14:19:45 ruff Exp $
           $Source: /opt/cvsroot/xmlBlaster/src/java/org/xmlBlaster/util/Attic/PoolManager.java,v $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * A little framework to handle a pool of limited resources.
 * <p />
 * You can use this pool as a base class for handling of your limited resources
 * like a 'JDBC connection pool' or a 'thread pool'.
 * <p />
 * Implementation:<br />
 * There are two list in this class
 * <ul>
 *    <li>busy: Hold resources which are currently busy</li>
 *    <li>idle: Hold resources which in the free pool and waiting to be reused</li>
 * </ul>
 * If you ask for a new resource, this will move into the busy list<br />
 * Is a resource released or a specified timeout occurred, it is moved into the idle list.
 * <p />
 * The important attributes of a resource are gathered in the <code>ResourceWrapper</code> class.
 * <p />
 * You can easily handle bigger number of resources with good performance.
 * <p />
 * How to use:<br />
 * Extend your spcialized pool from this class and implement some methods:
 * <ul>
 *    <li>In your constructor you need to calls this constructor:<br />
 *        <code>super("NiceName", 100, (long)60*60*6); // Default life cycle is 6 hours</code>
 *    </li>
 *    <li>Ask for a resource
 *    </li>
 *    <li>Release a resource explicitly
 *    </li>
 *    <li>If a timeout releases the resource, you are notified about it<br />
 *        <code>protected void notifyAboutRelease(ResourceWrapper rw)</code>
 *    </li>
 * </ul>
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.util.ResourceWrapper
 */
public class PoolManager extends Thread
{
   private boolean isInitialized = false;
   /** Nice, unique name for logging output */
   private String ME = "PoolManager";
   /** A nice name for the generated id */
   protected String resourceId = "Resource";
   /** Holds busy resources */
   protected java.util.Hashtable busy = null;
   /** Holds free resources */
   protected java.util.Vector idle = null;
   /** Default maximum pool size (number of resources) */
   protected int maxInstances = 100;
   /** Default maximum life cycle of a resource */
   protected long defaultTimeout = 0;
   /** Unique counter to generate IDs */
   private long counter = 1;
   /** Sleep interval for recycle thread */
   private final int SLEEP_PERIOD = 10000;


   /**
    * Create a new pool instance with the desired behaviour.
    * @param resourceId    A nice name for this resource
    * @param maxInstances  Max. number of resources in this pool.
    * @param defaultTimeout  Max. life span of this resource in <b>seconds</b><br>
    *                      You can overwrite this value for each resource instance
    */
   public PoolManager(String resourceId, int maxInstances, long defaultTimeout)
   {
      super(resourceId + "Pool-Thread");
      this.resourceId = resourceId;  // e.g. "SessionId"
      this.ME = resourceId + "Pool"; // e.g. "SessionIdPool"
      this.maxInstances(maxInstances);
      this.defaultTimeout(defaultTimeout);
      this.busy = new java.util.Hashtable(maxInstances);
      this.idle = new java.util.Vector(maxInstances);
      if (defaultTimeout > 0)
         Log.info(ME, "Created PoolManager with a maximum of " + maxInstances + " instances and a default recycling-timeout of " + TimeHelper.millisToNice(this.defaultTimeout * 1000));
      else
         Log.info(ME, "Created PoolManager with a maximum of " + maxInstances + " instances without auto-recycling.");
   }


   /**
    * Set the maximum pool size.
    * @param maxInstances How many resources are allowed
    */
   public void maxInstances(int maxInstances)
   {
      this.maxInstances = maxInstances;
      if (Log.TRACE) Log.trace(ME, "Max. number of resource instances set to " + maxInstances);
   }


   /**
    * Set the max. life span of the resources.
    * @param defaultTimeout  Max. life span in seconds<br />
    *        You may overwrite this value for each resource instance
    */
   public void defaultTimeout(long defaultTimeout)
   {
      this.defaultTimeout = defaultTimeout;
      if (Log.TRACE) Log.trace(ME, "Set default life span to " + TimeHelper.millisToNice(this.defaultTimeout*1000));
   }


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
      return reserve(defaultTimeout, null);
   }


   /**
    * Get a new resource.
    *
    * @param timeout  Max. life span in seconds<br />
    *        You may overwrite this value for each resource instance
    *        0: No auto recycling
    * @instanceId if not null the delivered ID is used, otherwise we generate one
    * @return rw The resource handle<br />
              rw.getResource()==null: This is a new handle, and you need to fill in your resource with setResource(a,b)<br />
              rw.getResource()!=null: A valid resource is recycled
    * @exception XmlBlasterException Error with random generator
    */
   synchronized public ResourceWrapper reserve(long timeout, String instanceId) throws XmlBlasterException
   {
     ResourceWrapper rw = null;
     long milliTimeout = timeout * 1000;
     if (idle.size() > 0) {
         if (Log.TRACE) Log.trace(ME, "Resource is receycled from idle pool ...");
         rw = (ResourceWrapper)idle.lastElement();
         rw.init(createId(instanceId), rw.getResource(), milliTimeout);
         swap(rw, true);
         if (Log.TRACE) Log.trace(ME, "Access on cached resource '" + rw.getInstanceId() + "' granted");
         dumpState(rw.getInstanceId());
     }
     else if (busy.size() >= maxInstances) {
        String text = "";
        Log.error(ME, "Sorry, " + maxInstances + " resources consumed, no more resources available");
        throw new XmlBlasterException("ResourceExhaust", text);
     }
     else  {
        if (Log.TRACE) Log.trace(ME, "Creatuing new ResourceWrapper instance ...");
        instanceId = createId(instanceId);
        rw = new ResourceWrapper(instanceId, null, milliTimeout);
        if (instanceId != null && instanceId.length() > 0) {
           busy.put(instanceId, rw);
           Log.info(ME, "Granted access to new resource '" + rw.getInstanceId() + "'");
           dumpState(rw.getInstanceId());
        }
        else
           ; // this case is for the user to fill with setResource
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
     *  @instanceId null   ResourceWrapper generates ID = resource.hashCode()<br />
     *              "..."  Your supplied ID is used<br />
     *              ""     A random ID is generated
     *  @return unique ID or null
     *  @exception XmlBlasterException random generator
     */
    protected String createId(String instanceId) throws XmlBlasterException
    {
       if (instanceId == null)
          return null;       // ResourceWrapper generates a simple id = resource.hashCode()

       if (instanceId.length() > 1)
          return instanceId; // User supplied ID (e.g. from Servlet.session)

       try { // System.getProperty(os.ip-addr) ??
          java.util.Random ran = new java.util.Random();
          //   <InstanceName>-<TimestampMilliSec>-<RandomNumber>-<LocalCounter>
          return resourceId + "-" + System.currentTimeMillis() + "-" +  ran.nextInt() + "-" + (counter++);
       }
       catch (Exception e) {
          String text = "Can't generate a unique instanceId: " + e.toString();
          Log.error(ME, text);
          throw new XmlBlasterException("ResourceNoId", text);
       }
    }


   /**
    * Synchronized idle - busy swapper.
    * @param instanceId The unique resource ID
    */
   private void swap(ResourceWrapper rw, boolean toBusy)
   {
      if (toBusy) {
          idle.removeElementAt(idle.size() - 1);
          busy.put(rw.getInstanceId(), rw);
      }
      else  { // recycling ...
         busy.remove(rw.getInstanceId());
         notifyAboutRelease(rw);
         rw.resetInstanceId();
         idle.addElement(rw);
      }
   }


   /**
    * Find a resource in busy list.
    * @param instanceId The unique resource ID
    * @return Der handle welcher die Resource beinhaltet
    */
   protected ResourceWrapper findSilent(String instanceId)
   {
      return (ResourceWrapper)busy.get(instanceId);
   }


   /**
    * Find a resource in busy list.
    * @param instanceId The unique resource ID
    * @return Handle containing resource
    * @exception XmlBlasterException "ResourceNotFound"
    */
   public ResourceWrapper findLow(String instanceId) throws XmlBlasterException
   {
      ResourceWrapper rw = findSilent(instanceId);
      if (rw == null) {
         String text = "Resource '" + instanceId + "' is invalid, timed out?";
         Log.error(ME, text);
         throw new XmlBlasterException("ResourceNotFound", text);
      }
      return rw;
   }


   /**
    * Restart countdown for Resourc life cycle.
    *
    * @param instanceId The unique resource ID
    * @exception XmlBlasterException ResourceNotFound
    */
   public void touch(String instanceId) throws XmlBlasterException
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
    */
   public ResourceWrapper find(String instanceId) throws XmlBlasterException
   {
     ResourceWrapper rw = findLow(instanceId);
     if (Log.TRACE) Log.trace(ME, "Erfolgreich mit Resource '" + instanceId + "' wiederverbunden");
     return rw;
   }


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
    * Cleanup everything.
    */
   public void cleanup()
   {
      if (isInitialized) return;
      isInitialized = true;
      Log.info(ME, "Freeing all resources, " + busy.size() + " are busy and " + idle.size() + " idle.");
      for (int ii=0; ii<idle.size(); ii++)
         notifyAboutShutdown((ResourceWrapper)idle.elementAt(ii));
      idle.removeAllElements();
      java.util.Enumeration e = busy.elements();
      for (; e.hasMoreElements() ;)
         notifyAboutShutdown((ResourceWrapper)e.nextElement());
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
    * Start the resource garbage collector thread.
    */
   public void run()
   {
      setPriority(Thread.MIN_PRIORITY);
      Log.info(ME, "Starting resource garbage collector thread.");
      while (true) {
         try {
            sleep(SLEEP_PERIOD); // 10 sec sleep
         } catch (InterruptedException e) {
            Log.error(ME, "PoolManagerThread sleep, got woken up by other thread: " + e.toString());
            continue;
         }
         recycleResources();
      }
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
    * Recycle resource after timeout.
    */
   synchronized private void recycleResources()
   {
      // Log.traceAll(ME, "Entering PoolManagerThread.recycleResources() ...");
      for (java.util.Enumeration e = busy.elements(); e.hasMoreElements() ;) {
         ResourceWrapper rw = (ResourceWrapper)(e.nextElement());
         if (rw.isExpired()) {
            Log.warning(ME, "Resource '" + rw.getInstanceId() + "' is receycled, was not in use since " + TimeHelper.millisToNice(rw.getTimeout()));
            swap(rw, false);
            dumpState(rw.getInstanceId());
         }
      }
   }


   /**
    * Callback when resource is release (recycle automatically, or explicitly released).
    * <p />
    * Your derived class may use this notification
    * @param rw The recyced ResourceWrapper
    */
   protected void notifyAboutRelease(ResourceWrapper rw)
   {
   }


   /**
    * Called from finalize or destroy(), resource will be removed.
    * <p />
    * Your derived class may use this notification, e.g. if the server
    * exists and you want to close a JDBC connection.
    * @param rw The destroyed ResourceWrapper
    */
   protected void notifyAboutShutdown(ResourceWrapper rw)
   {
   }
}

