/*------------------------------------------------------------------------------
Name:      ExecutorBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
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
}

