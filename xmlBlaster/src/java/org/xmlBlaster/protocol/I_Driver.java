/*------------------------------------------------------------------------------
Name:      I_Driver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_Driver.java,v 1.5 2002/03/17 07:29:03 ruff Exp $
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
 * @version $Revision: 1.5 $
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

