/*------------------------------------------------------------------------------
Name:      I_PoolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Version:   $Id: I_PoolManager.java,v 1.1 2000/05/31 21:33:07 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * Interface you need to implement to receive state transition callbacks from the PoolManager.
 */
public interface I_PoolManager
{
   /**
    */
   public void toIdle(Object resource);


   /**
    * @return A new Resource which will be managed
    */
   public Object toCreate() throws XmlBlasterException;


   /**
    */
   public void toErased(Object resource);
}
