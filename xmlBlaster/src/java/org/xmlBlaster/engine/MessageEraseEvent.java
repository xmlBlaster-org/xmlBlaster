/*------------------------------------------------------------------------------
Name:      MessageEraseEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The erase() event object
Version:   $Id: MessageEraseEvent.java,v 1.4 2002/03/13 16:41:11 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.SessionInfo;

/**
 * An event which indicates that the given message will be erased.
 * <p />
 * This is triggered usually when the erase() method is called from a client.
 * <p />
 * This EventObject carries the MessageUnitHandler reference inside.
 *
 * @version $Id: MessageEraseEvent.java,v 1.4 2002/03/13 16:41:11 ruff Exp $
 * @author Marcel Ruff
 */
public class MessageEraseEvent extends java.util.EventObject
{
   private SessionInfo sessionInfo;


   /**
    * Constructs a MessageEraseEvent object.
    *
    * @param source the MessageEraseInfo object
    */
   public MessageEraseEvent(SessionInfo sessionInfo, MessageUnitHandler messageHandler)
   {
       super(messageHandler);
       this.sessionInfo = sessionInfo;
   }

   /**
    * Returns the originator of the event.
    *
    * @return the MessageUnitHandler object that originated the event
    */
   public MessageUnitHandler getMessageUnitHandler() {
       return (MessageUnitHandler)source;
   }

   /**
    * Returns the originator of the event.
    *
    * @return the SessionInfo object that originated the event
    */
   public SessionInfo getSessionInfo() {
       return sessionInfo;
   }
}
