/*------------------------------------------------------------------------------
Name:      I_Driver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_Driver.java,v 1.6 2002/04/08 17:09:27 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;


/**
 * This interface hides the real protocol used to invoke xmlBlaster.
 * <p>
 *
 * @version $Revision: 1.6 $
 * @author ruff@swand.lake.de
 */
public interface I_Driver
{
   // These are not needed, because it is completely generic (examples only)
   /* "IOR" */
   //public final String CORBA_ID = "IOR";
   /* EMAIL */
   //public final String EMAIL_ID = "EMAIL";
   /* XML-RPC */
   //public final String XMLRPC_ID = "XML-RPC";


   /** Get a human readable name of this driver */
   public String getName();

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return e.g. "IOR" "EMAIL" "XML-RPC" depending on driver
    */
   public String getProtocolId();

   /**
    * Return the address how to access this driver. 
    * @return e.g. "http:/www.mars.universe:8080/RPC2" or "IOR:000034100..."
    */
   public String getRawAddress();

   /**
    * Start xmlBlaster access through this protocol.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   public void init(Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException;


   /**
    * Shut down the driver.
    * <p />
    */
   public void shutdown();


   /**
    * The startup usage text.
    * <p />
    */
   public String usage();
}

