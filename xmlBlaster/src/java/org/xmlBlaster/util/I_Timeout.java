/*------------------------------------------------------------------------------
Name:      I_Timeout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Version:   $Id: I_Timeout.java,v 1.2 2000/05/26 20:47:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * Interface you need to implement to receive timeout notifications
 */
public interface I_Timeout
{
   /**
    * You will be notified about the timeout through this method.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public void timeout(Object userData);
}
