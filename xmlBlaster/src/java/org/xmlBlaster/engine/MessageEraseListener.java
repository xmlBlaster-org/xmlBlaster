/*------------------------------------------------------------------------------
Name:      MessageEraseListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on messageErase() events
Version:   $Id: MessageEraseListener.java,v 1.3 2000/06/13 13:03:59 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on message erase() events.
 * <p>
 * The events are fired by the RequestBroker object.
 *
 * @version $Id: MessageEraseListener.java,v 1.3 2000/06/13 13:03:59 ruff Exp $
 * @author Marcel Ruff
 */
public interface MessageEraseListener extends java.util.EventListener {
    /**
     * Invoked on message erase() invocation
     */
    public void messageErase(MessageEraseEvent e) throws org.xmlBlaster.util.XmlBlasterException;
}
