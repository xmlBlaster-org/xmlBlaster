/*------------------------------------------------------------------------------
Name:      I_RetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface handling the returned QoS (quality of service)
Version:   $Id: I_RetQos.java,v 1.2 2002/12/20 15:28:58 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;


/**
 * All returned QoS of xmlBlaster should at least contain these informations. 
 */
public interface I_RetQos
{
   /**
    * @return "OK", "EXPIRED" etc. but never null
    * @see org.xmlBlaster.util.enum.Constants
    */
   public String getStateId();

   /**
    * Additional structured information about a state. 
    * @return "QUEUED" or "QUEUED[bilbo]" or null if not known
    * @see org.xmlBlaster.util.enum.Constants
    */
   public String getStateInfo();
}
