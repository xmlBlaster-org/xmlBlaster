/*------------------------------------------------------------------------------
Name:      I_ConnectionProblems.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ConnectionProblems.h,v 1.4 2003/01/07 20:41:35 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Callback the client from XmlBlasterConnection if the connection to xmlBlaster is lost
 * or was reestablished (fail save mode).
 * <p>
 * @version $Revision: 1.4 $
Author:    xmlBlaster@marcelruff.info
 * @author <a href='xmlBlaster@marcelruff.info'>Marcel Ruff</a>
 * @author <a href='laghi@swissinfo.org'>Michele Laghi</a>
 */

#ifndef _CLIENT_ICONNECTIONPROBLEMS_H
#define _CLIENT_ICONNECTIONPROBLEMS_H

class I_ConnectionProblems
{
public:

   virtual ~I_ConnectionProblems()
   {
   }

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established. It has a bool return
    * which informs the ConnectionsHandler what to do with the entries in the queue. If you return 'true', 
    * then the queue is flushed (i.e. the contents of the queue are sent to xmlBlaster). If you return 
    * 'false', then the contents of the queue are left untouched. You can then erase all entries manually
    * by invoking the clearQueue() method on the XmlBlasterAccess object or flush them manually by invoking
    * 'flushQueue()' on the XmlBlasterAccess object.
    */
   virtual bool reConnected() = 0;

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was lost (i.e. when the state of the
    * connectionsHandler is going to DEAD).
    */
   virtual void lostConnection() = 0;

   /**
    * This is the callback method invoked from XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection changes to polling modus.
    */
   virtual void toPolling() = 0;

};

#endif


