/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ConnectionProblems.h,v 1.1 2002/12/28 18:49:50 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Callback the client from XmlBlasterConnection if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @version $Revision: 1.1 $
Author:    xmlBlaster@marcelruff.info
 * @author <a href='xmlBlaster@marcelruff.info'>Marcel Ruff</a>
 * @author <a href='laghi@swissinfo.org'>Michele Laghi</a>
 */

#ifndef _CLIENT_ICONNECTIONPROBLEMS_H
#define _CLIENT_ICONNECTIONPROBLEMS_H

class I_ConnectionProblems
{
public:
   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode if the connection was established.
    */
   virtual void reConnected() = 0;


   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode if the connection was lost.
    */
   virtual void lostConnection() = 0;
};

#endif


