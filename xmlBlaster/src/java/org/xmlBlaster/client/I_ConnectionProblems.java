/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ConnectionProblems.java,v 1.6 2003/03/24 19:59:26 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;


/**
 * Callback the client from XmlBlasterConnection if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @version $Revision: 1.6 $
 * @author $Author: ruff $
 * @deprecated Please use I_ConnectionStateListener and I_XmlBlasterAccess instead
 */
public interface I_ConnectionProblems
{
   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode if the connection was established.
    */
   public void reConnected();


   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode if the connection was lost.
    */
   public void lostConnection();
}

