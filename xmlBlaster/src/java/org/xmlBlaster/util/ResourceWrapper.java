/*------------------------------------------------------------------------------
Name:      ResourceWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Container for your resource
Version:   $Id: ResourceWrapper.java,v 1.2 2000/05/30 14:44:10 ruff Exp $
           $Source: /opt/cvsroot/xmlBlaster/src/java/org/xmlBlaster/util/Attic/ResourceWrapper.java,v $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * Container holds infos about a resource.
 * <p />
 * All ResourceWrapper are handled in a pool, the 'PoolManager' class.
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.util.PoolManager
 */
public class ResourceWrapper
{
   /** Nice, unique name for logging output */
   private String ME = "ResourceWrapper";
   /** Unique identifier */
   private String instanceId;
   /** The resource itself (not interpreted in this context). It is supplied by you. */
   private Object resource;
   /** Max live span of instance since lastAccess in milliseconds */
   private long timeout;
   /** Time in milliseconds since January 1, 1970 UTC. */
   private long creationTime;
   /** timeout handle */
   private Long timeoutHandle;
   /** My manager */
   private PoolManager poolManager;

   public final static String INVALID_KEY = "RESOURCE_IS_IDLE";


   /**
    * Create a new wrapper for a user supplied resource.
    *
    * @param poolManager A reference on my manager
    * @param instanceId The unique ID<br />
    *                 If 'null': use ref.hashCode()
    * @param resource Your resource
    * @param timeout  The max. life span for this resource
    */
   public ResourceWrapper(PoolManager poolManager, String instanceId, Object resource, long timeout)
   {
      this.poolManager = poolManager;
      init(instanceId, resource, timeout);
   }


   /**
    * Create a new wrapper for a resource with infinite lifespan.
    * <p />
    * You need to call resource(yourResource) after constructing.
    * @param poolManager A reference on my manager
    */
   public ResourceWrapper(PoolManager poolManager)
   {
      this.poolManager = poolManager;
      init(null, null, 0);
   }


   /**
    * Set resource attributes.
    *
    * @param instanceId The unique identifier<br />
    *                   Resource.hashCode() is used if you pass null
    * @param resource   Your resource
    * @param timeout    The max. life span.<br />
    *                   0: infinite life span
    */
   public void init(String instanceId, Object resource, long timeout)
   {
      this.creationTime = System.currentTimeMillis();
      this.instanceId = instanceId;
      this.resource = resource;
      this.timeout = timeout;
      if (timeout <= 0)
         timeoutHandle = null;
      else
         timeoutHandle = Timeout.getInstance().addTimeoutListener(poolManager, timeout, this);
      if (this.instanceId == null || this.instanceId.length() < 1)
      if (resource != null)
         this.instanceId = "" + resource.hashCode();
   }


   /**
    * Is the resource life span expired?
    * @return true/false
    */
   public boolean isExpired()
   {
      if (timeout <= 0) return false;
      if (timeoutHandle == null) return false;
      return Timeout.getInstance().isExpired(timeoutHandle);
   }


   /**
    * How long to my death.
    * @return Milliseconds to timeout<br />
    *         0 for infinite life<br />
    *         -1 if timeout occurred already
    */
   public long spanOfTimeToDeath()
   {
      if (timeout <= 0) return 0;
      return Timeout.getInstance().spanOfTimeToDeath(timeoutHandle);
   }


   /**
    * Restart count down.
    */
   public void touch()
   {
      try {
         timeoutHandle = Timeout.getInstance().refreshTimeoutListener(timeoutHandle, timeout);
      }
      catch (XmlBlasterException e) {
         Log.error(ME, e.reason);
      }
   }


   /**
    * Access the unique resource ID.
    * @return The unique ID of this resource
    */
   public String getInstanceId()
   {
      return instanceId;
   }


   /**
    * Invalidate resource ID.
    */
   private void resetInstanceId()
   {
      instanceId = ResourceWrapper.INVALID_KEY;
   }


   /**
    * Acces the resource object.
    * @return Your resource object
    */
   public Object getResource()
   {
      return resource;
   }


   /**
    * Set your resource object.
    * @param The new resource
    */
   void setResource(Object resource)
   {
      this.resource = resource;
      if (this.instanceId == null || this.instanceId.length() < 1)
         this.instanceId = "" + resource.hashCode(); // generate ID if not specified
   }


   /**
    * Access the max. life span of this resource.
    * @return life span in milliseconds
    */
   public long getTimeout()
   {
      return Timeout.getInstance().getTimeout(timeoutHandle);
   }


   /**
    * Cleanup, reset timer and destroy id. 
    */
   public void cleanup()
   {
      if (timeoutHandle != null)
         Timeout.getInstance().removeTimeoutListener(timeoutHandle);
      resetInstanceId();
   }


   /**
    * Set new life span timeout.
    * @param timeout New timeout in milliseconds
    */
   public void setTimeout(long timeout)
   {
      this.timeout = timeout;
      touch();
   }


   /**
    * Access the construction date of this ResourceWrapper.
    * @return Time in milliseconds since midnight, January 1, 1970 UTC
    */
   public long getCreationTime()
   {
      return creationTime;
   }
}
