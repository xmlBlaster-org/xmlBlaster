/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ConnectionProblems.java,v 1.4 2002/12/18 12:28:15 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;


/**
 * Callback the client from XmlBlasterConnection if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @version $Revision: 1.4 $
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

