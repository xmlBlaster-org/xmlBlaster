/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ConnectionProblems.java,v 1.2 2000/06/18 15:21:58 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;


/**
 * Callback the client from CorbaConnection if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @version $Revision: 1.2 $
 * @author $Author: ruff $
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

