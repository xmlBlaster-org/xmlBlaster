/*------------------------------------------------------------------------------
Name:      MessageEraseListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on messageErase() events
Version:   $Id: MessageEraseListener.java,v 1.1 1999/11/26 09:09:40 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on message erase() events.
 * <p>
 * The events are fired by the RequestBroker object.
 *
 * @version $Id: MessageEraseListener.java,v 1.1 1999/11/26 09:09:40 ruff Exp $
 * @author Marcel Ruff
 */
public interface MessageEraseListener extends java.util.EventListener {
    /**
     * Invoked on message erase() invocation
     */
    public void messageErase(MessageEraseEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;
}
