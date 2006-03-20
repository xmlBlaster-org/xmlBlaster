/*------------------------------------------------------------------------------
Name:      ResourceWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.pool;

import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;


/**
 * Container holds infos about a resource.
 * <p />
 * All ResourceWrapper are handled in a pool, the 'PoolManager' class.
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.util.pool.PoolManager
 */
public class ResourceWrapper implements I_Timeout
{
   /** Nice, unique name for logging output */
   private String ME = "ResourceWrapper";
   /** Unique identifier */
   private String instanceId;
   /** The resource itself (not interpreted in this context). It is supplied by you. */
   private Object resource;
   /** Time in milliseconds since January 1, 1970 UTC. */
   private long creationTime;
   /** Constant to mark busy to idle timer events */
   private static final String BUSY_TO_IDLE_TIMEOUT = "BI";
   /** Constant to mark idle to erase timer events */
   private static final String IDLE_TO_ERASE_TIMEOUT = "IE";
   /** Max live span of instance since lastAccess in milliseconds */
   private long busyToIdleTimeout;
   /** busyToIdleTimeout handle */
   private Timestamp busyToIdleTimeoutHandle;
   /** Synchronization helper */
   private final Object BUSYTOIDLETIMEOUT_MONITOR = new Object();
   /** Max live span until 'idle' to 'erase' transition */
   private long idleToEraseTimeout;
   /** idleToEraseTimeout handle */
   private Timestamp idleToEraseTimeoutHandle;
   /** My manager */
   private PoolManager poolManager;

   /**
   * Create a new wrapper for a user supplied resource.
   *
   * @param poolManager A reference on my manager
   * @param instanceId The unique ID<br />
   *                 If 'null': use ref.hashCode()
   * @param resource Your resource
   * @param busyToIdleTimeout  The max. 'busy' life span for this resource<br />
   *                            0: infinite
   * @param idleToEraseTimeout  The max. 'idle' life span for this resource<br />
   *                            0: infinite
   */
   ResourceWrapper(PoolManager poolManager, String instanceId, Object resource, long busyToIdleTimeout, long idleToEraseTimeout) {
      this.poolManager = poolManager;
      init(instanceId, resource, busyToIdleTimeout, idleToEraseTimeout);
   }

   /**
   * Create a new wrapper for a resource with infinite lifespan.
   * <p />
   * You need to call resource(yourResource) after constructing.
   * @param poolManager A reference on my manager
   */
   ResourceWrapper(PoolManager poolManager) {
      this.poolManager = poolManager;
      init(null, null, 0, 0);
   }

   /**
   * Set resource attributes.
   *
   * @param instanceId The unique identifier<br />
   *                   Resource.hashCode() is used if you pass PoolManager.USE_HASH_CODE (== null)
   * @param resource   Your resource
   * @param busyToIdleTimeout    The max. busy life span.<br />
   *                   0: infinite life span
   * @param idleToEraseTimeout  The max. 'idle' life span for this resource<br />
   *                            0: infinite
   */
   void init(String instanceId, Object resource, long busyToIdleTimeout, long idleToEraseTimeout) {
      this.creationTime = System.currentTimeMillis();
      this.instanceId = instanceId;
      this.resource = resource;
      setBusyToIdle(busyToIdleTimeout);
      setIdleToErase(idleToEraseTimeout);
      if (resource != null) {
         if (this.instanceId == null || this.instanceId.equals(PoolManager.USE_HASH_CODE))
            this.instanceId = "" + resource.hashCode();
         else
            if (this.instanceId.equals(PoolManager.USE_OBJECT_REF))
               this.instanceId = "" + resource; // should not have a toString() method, to get for example: oracle.jdbc.driver.OracleConnection@5ee9f2e1
      }
      //System.out.println("GIVEN:" + instanceId + ":" + this.instanceId);
   }

   /**
   * Entering 'busy' state.
   */
   void toBusy() {
      stopIdleToEraseTimeout();
      startBusyToIdleTimeout();
   }

   /**
   * Entering 'idle' state.
   */
   void toIdle() {
      stopBusyToIdleTimeout();
      startIdleToEraseTimeout();
   }

   /**
   * Set timeout and initialize timer.
   */
   private void setBusyToIdle(long val) {
      stopBusyToIdleTimeout();
      if (val > 0 && val < 100) {
         // Log.warning(ME, "Setting minimum busyToIdleTimeout from " + val + " to 100 milli seconds");
         val = 100;
      }
      this.busyToIdleTimeout = val;
   }

   /**
   * Start the timeout.
   */
   private void startBusyToIdleTimeout() {
      stopBusyToIdleTimeout();
      if (this.busyToIdleTimeout > 0)
         busyToIdleTimeoutHandle = poolManager.getTransistionTimer().addTimeoutListener(this, this.busyToIdleTimeout, BUSY_TO_IDLE_TIMEOUT);
   }

   /**
   * Stop the timeout.
   */
   private void stopBusyToIdleTimeout() {
      if (busyToIdleTimeoutHandle != null) {
         poolManager.getTransistionTimer().removeTimeoutListener(busyToIdleTimeoutHandle);
         busyToIdleTimeoutHandle = null;
      }
   }

   /**
   * Set timeout and initialize timer.
   */
   private void setIdleToErase(long val) {
      stopIdleToEraseTimeout();
      if (val > 0 && val < 100) {
         // Log.warning(ME, "Setting minimum idleToEraseTimeout from " + val + " to 100 milli seconds");
         val = 100;
      }
      this.idleToEraseTimeout = val;
   }

   /**
   * Start the idle to erase timeout.
   */
   private void startIdleToEraseTimeout() {
      stopIdleToEraseTimeout();
      if (this.idleToEraseTimeout > 0)
         idleToEraseTimeoutHandle = poolManager.getTransistionTimer().addTimeoutListener(this, this.idleToEraseTimeout, IDLE_TO_ERASE_TIMEOUT);
   }

   /**
   * Stop the idle to erase timeout.
   */
   private void stopIdleToEraseTimeout() {
      if (idleToEraseTimeoutHandle != null) {
         poolManager.getTransistionTimer().removeTimeoutListener(idleToEraseTimeoutHandle);
         idleToEraseTimeoutHandle = null;
      }
   }

   /**
   * A timeout occurred.
   * <p />
   * This method is a callback through interface I_Timeout.
   * @param The timeout type, BUSY_TO_IDLE_TIMEOUT or IDLE_TO_ERASE_TIMEOUT
   */
   public void timeout(Object obj) {
      //System.out.println("\nTIMEOUT for:" + this.instanceId);
      String type = (String) obj;
      if (type.equals(BUSY_TO_IDLE_TIMEOUT)) {
         busyToIdleTimeoutHandle = null;
         poolManager.timeoutBusyToIdle(this);
      }
      else
         if (type.equals(IDLE_TO_ERASE_TIMEOUT)) {
            idleToEraseTimeoutHandle = null;
            poolManager.timeoutIdleToErase(this);
         }
   }

   /**
   * Is the resource life span expired?
   * @return true/false
   */
   public boolean isBusyExpired() {
      if (busyToIdleTimeout <= 0)
         return false;
      if (busyToIdleTimeoutHandle == null)
         return false;
      return poolManager.getTransistionTimer().isExpired(busyToIdleTimeoutHandle);
   }

   /**
   * How long am i running in busy mode.
   * @return Milliseconds since creation or -1 if not known
   */
   public long busyElapsed() {
      if (busyToIdleTimeout <= 0)
         return -1;
      return System.currentTimeMillis() - creationTime;
   }

   /**
   * How long to my death.
   * @return Milliseconds to idleToEraseTimeout<br />
   *         0 for infinite idle life<br />
   */
   public long spanOfTimeToErase() {
      if (idleToEraseTimeout <= 0)
         return 0;
      if (idleToEraseTimeoutHandle == null)
         return 0;
      return poolManager.getTransistionTimer().spanToTimeout(idleToEraseTimeoutHandle);
   }

   /**
   * How long until i swap from busy to idle.
   * @return Milliseconds to busyToIdleTimeout<br />
   *         0 for infinite life<br />
   *         -1 if busyToIdleTimeout occurred already
   */
   public long spanOfTimeToIdle() {
      if (busyToIdleTimeout <= 0)
         return 0;
      if (busyToIdleTimeoutHandle == null)
         return 0;
      return poolManager.getTransistionTimer().spanToTimeout(busyToIdleTimeoutHandle);
   }

   /**
   * Restart count down in busy mode.
   */
   public void touchBusy() {
      if (busyToIdleTimeoutHandle == null)
         return;
      try {
         synchronized (this.BUSYTOIDLETIMEOUT_MONITOR) {
            busyToIdleTimeoutHandle = poolManager.getTransistionTimer().refreshTimeoutListener(busyToIdleTimeoutHandle, busyToIdleTimeout);
         }
      }
      catch (XmlBlasterException e) {
         // Log.error(ME, e.reason);
      }
   }

   /**
   * Access the unique resource ID.
   * @return The unique ID of this resource
   */
   public String getInstanceId() {
      return instanceId;
   }

   /**
   * Access the resource object.
   * @return Your resource object
   */
   public Object getResource() {
      return resource;
   }

   /**
   * Set your resource object.
   * @param The new resource
   */
   void setResource(Object resource) {
      this.resource = resource;
      if (resource != null) {
         if (this.instanceId == null || this.instanceId.equals(PoolManager.USE_HASH_CODE))
            this.instanceId = "" + resource.hashCode();
         else
            if (this.instanceId.equals(PoolManager.USE_OBJECT_REF))
               this.instanceId = "" + resource; // should not have a toString() method, to get for example: oracle.jdbc.driver.OracleConnection@5ee9f2e1
      }
      System.out.println("AAAAAAAAAAAAAA:" + this.instanceId);
   }

   /**
   * Access the overall busy timeout span of this resource.
   * @return timeout span in milliseconds
   */
   public long getBusyToIdleTimeout() {
      return poolManager.getTransistionTimer().getTimeout(busyToIdleTimeoutHandle);
   }

   /**
   * Access the overall idle timeout span of this resource.
   * @return timeout span in milliseconds
   */
   public long getIdleToEraseTimeout() {
      return poolManager.getTransistionTimer().getTimeout(idleToEraseTimeoutHandle);
   }

   /**
   * Cleanup, reset timer and destroy id.
   */
   public void destroy() {
      if (busyToIdleTimeoutHandle != null) {
         poolManager.getTransistionTimer().removeTimeoutListener(busyToIdleTimeoutHandle);
         busyToIdleTimeoutHandle = null;
      }
      if (idleToEraseTimeoutHandle != null) {
         poolManager.getTransistionTimer().removeTimeoutListener(idleToEraseTimeoutHandle);
         idleToEraseTimeoutHandle = null;
      }
      instanceId = null;
      resource = null;
      poolManager = null;
   }

   /**
   * Access the construction date of this ResourceWrapper.
   * @return Time in milliseconds since midnight, January 1, 1970 UTC
   */
   public long getCreationTime() {
      return creationTime;
   }

   /**
   * Dump state of this object into a XML ASCII string.
   * <p />
   * @return internal state of this ResourceWrapper as a XML ASCII string
   */
   public final String toXml() {
      return toXml((String) null);
   }

   /**
   * Dump state of this object into a XML ASCII string.
   * <p />
   * @param extraOffset indenting of tags for nice output
   * @return internal state of this ResourceWrapper as a XML ASCII string
   */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null)
         extraOffset = "";
      offset += extraOffset;
      sb.append(offset).append("<").append(ME).append(" instanceId='").append(instanceId);
      //sb.append(" name='").append(resource.toString());
      sb.append("'>");
      sb.append(offset).append("   <busyToIdle timeout='").append(busyToIdleTimeout).append("' handle='").append(busyToIdleTimeoutHandle).append("' />");
      sb.append(offset).append("   <idleToErase timeout='").append(idleToEraseTimeout).append("' handle='").append(idleToEraseTimeoutHandle).append("' />");
      sb.append(offset).append("</").append(ME).append("'>");
      return sb.toString();
   }
}
