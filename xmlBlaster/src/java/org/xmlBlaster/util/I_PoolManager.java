/*------------------------------------------------------------------------------
Name:      I_PoolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Version:   $Id: I_PoolManager.java,v 1.3 2000/06/01 16:46:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * Interface you need to implement to receive state transition callbacks from the PoolManager.
 * @see PoolManager
 * @see ResourceWrapper
 */
public interface I_PoolManager
{
   /**
    * Is invoked on state transition from 'idle' to 'busy'. 
    * <p />
    * You can code here some specific behavior.
    */
   public void idleToBusy(Object resource);


   /**
    * Is invoked on state transition from 'busy' to 'idle'. 
    * <p />
    * You can code here some specific behavior.
    */
   public void busyToIdle(Object resource);


   /**
    * Is invoked when a new resource is needed. 
    * <p />
    * You need to create a new resource instance (e.g. a JDBC connection)
    * and return it.
    * @return A new Resource which will be managed
    */
   public Object toCreate(String instanceId) throws XmlBlasterException;


   /**
    * Is invoked when a resource is destroyed explicitly or by timeout. 
    * <p />
    * You may want to do some specific coding here, for example
    * logout from a JDBC connection.
    */
   public void toErased(Object resource);
}
