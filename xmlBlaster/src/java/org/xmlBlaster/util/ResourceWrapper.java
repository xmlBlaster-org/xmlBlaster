/*------------------------------------------------------------------------------
Name:      ResourceWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Container for your resource
Version:   $Id: ResourceWrapper.java,v 1.1 2000/05/27 14:19:45 ruff Exp $
           $Source: /opt/cvsroot/xmlBlaster/src/java/org/xmlBlaster/util/Attic/ResourceWrapper.java,v $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Container holds infos about a resource.
 * <p />
 * All ResourceWrapper are handled in a pool, the 'PoolManager' class.
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.util.PoolManager
 */
public class ResourceWrapper
{
   /** Unique identifier */
   private String instanceId;
   /** The resource itself (not interpreted in this context). It is supplied by you. */
   private Object resource;
   /** Max live span of instance since lastAccess in milliseconds */
   private long timeout;
   /** current time in milliseconds since January 1, 1970 UTC. */
   private long lastAccess;
   /** current time in milliseconds since January 1, 1970 UTC. */
   private long creationTime;
   public final static String INVALID_KEY = "RESOURCE_IS_IDLE";


   /**
    * Create a new wrapper for a user supplied resource.
    * @param instanceId The unique ID<br />
    *                 If 'null': use ref.hashCode()
    * @param resource Your resource
    * @param timeout  The max. life span for this resource
    */
   public ResourceWrapper(String instanceId, Object resource, long timeout)
   {
      init(instanceId, resource, timeout);
   }


   /**
    * Create a new wrapper for a resource with infinite lifespan. 
    * <p />
    * You need to call resource(yourResource) after constructing.
    */
   public ResourceWrapper()
   {
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
      this.lastAccess = this.creationTime;
      this.instanceId = instanceId;
      this.resource = resource;
      this.timeout = timeout;
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
      return (System.currentTimeMillis() - lastAccess) > timeout;
   }


   /**
    * How long to my death. 
    * @return Milliseconds to timeout, or 0 for infinite life
    */
   public long spanOfTimeToDeath()
   {
      if (timeout <= 0) return 0;
      return timeout - (System.currentTimeMillis() - lastAccess);
   }


   /**
    * Restart count down. 
    */
   public void touch()
   {
      lastAccess = System.currentTimeMillis();
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
   public void resetInstanceId()
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
      return timeout;
   }


   /**
    * Set life span timeout. 
    * @param timeout New timeout in milliseconds
    */
   public void setTimeout(long timeout)
   {
      this.timeout = timeout;
   }


   /**
    * Access last access on this resource. 
    * @return Time in milliseconds since midnight, January 1, 1970 UTC
    */
   public long getLastAccess()
   {
      return lastAccess;
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
