/*------------------------------------------------------------------------------
Name:      ExecutorBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   ExecutorBase base class for Executor, defines common constants
Version:   $Id: ExecutorBase.java,v 1.2 2002/02/16 16:33:12 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;


/**
 * Defines some constants
 */
public interface ExecutorBase
{
   /** Default port of xmlBlaster socket server is 7607 */
   public static final int DEFAULT_SERVER_PORT = 7607;
   /** Used for execute() */
   public final boolean WAIT_ON_RESPONSE = true;
   /** Used for execute() */
   public final boolean ONEWAY = false;
   /** Temporary send update() WITHOUT waiting on response,
    *  Change this as soon as the new callback framework is running
    */
   public final boolean updateIsOneway = true;
}

