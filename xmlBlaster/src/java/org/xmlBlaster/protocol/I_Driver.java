/*------------------------------------------------------------------------------
Name:      I_Driver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_Driver.java,v 1.12 2003/05/21 20:21:16 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.qos.AddressServer;


/**
 * This interface hides the real protocol used to invoke xmlBlaster.
 * <p>
 *
 * @version $Revision: 1.12 $
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Driver extends I_Plugin
{
   /** Get a human readable name of this driver */
   public String getName();

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return e.g. "IOR" "EMAIL" "XMLRPC" depending on driver
    */
   public String getProtocolId();

   /**
    * Return the address how to access this driver. 
    * @return e.g. "http:/www.mars.universe:8080/RPC2" or "IOR:000034100..."
    */
   public String getRawAddress();

   /**
    * Activate xmlBlaster access through this protocol so that a client can contact us. 
    */
   public void activate() throws XmlBlasterException;

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public void deActivate() throws XmlBlasterException;

   /**
    * The startup usage text.
    * <p />
    */
   public String usage();
}

