/*------------------------------------------------------------------------------
Name:      MessageEraseEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The erase() event object
Version:   $Id: MessageEraseEvent.java,v 1.1 1999/11/29 07:07:59 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * An event which indicates that a new message erase() occurred
 * It carries the MessageUnitHandler reference inside
 *
 * @version $Id: MessageEraseEvent.java,v 1.1 1999/11/29 07:07:59 ruff Exp $
 * @author Marcel Ruff
 */
public class MessageEraseEvent extends java.util.EventObject
{
   private ClientInfo clientInfo;


   /**
    * Constructs a MessageEraseEvent object.
    *
    * @param source the MessageEraseInfo object
    */
   public MessageEraseEvent(ClientInfo clientInfo, MessageUnitHandler messageHandler)
   {
       super(messageHandler);
       this.clientInfo = clientInfo;
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
    * @return the ClientInfo object that originated the event
    */
   public ClientInfo getClientInfo() {
       return clientInfo;
   }
}
