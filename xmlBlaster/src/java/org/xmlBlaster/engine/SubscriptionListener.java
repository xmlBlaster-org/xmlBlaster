/*------------------------------------------------------------------------------
Name:      SubscriptionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on subscriptionRemove and subscriptionAdded events
Version:   $Id: SubscriptionListener.java,v 1.2 2000/01/13 06:18:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on subscriptionRemove and subscriptionAdded events.
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Id: SubscriptionListener.java,v 1.2 2000/01/13 06:18:25 ruff Exp $
 * @author Marcel Ruff
 */
public interface SubscriptionListener extends java.util.EventListener {
    /**
     * Invoked on successful subscription login
     */
    public void subscriptionAdd(SubscriptionEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;

    /**
     * Invoked when subscription does a logout
     */
    public void subscriptionRemove(SubscriptionEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;
}
