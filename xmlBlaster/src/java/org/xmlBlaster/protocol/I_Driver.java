/*------------------------------------------------------------------------------
Name:      I_Driver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_Driver.java,v 1.2 2000/06/05 10:46:31 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;


/**
 * This interface hides the real protocol used to invoke xmlBlaster.
 * <p>
 *
 * @version $Revision: 1.2 $
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
    * @param args The command line parameters
    */
   public void init(String args[], Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException;


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

