/*------------------------------------------------------------------------------
Name:      SubscriptionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on subscriptionRemove and subscriptionAdded events
Version:   $Id: I_SubscriptionListener.java,v 1.3 2004/01/30 19:25:45 laghi Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on subscriptionRemove and subscriptionAdded events.
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Id: I_SubscriptionListener.java,v 1.3 2004/01/30 19:25:45 laghi Exp $
 * @author Marcel Ruff
 */
public interface I_SubscriptionListener extends java.util.EventListener {
    /**
     * Invoked on successful subscription login
     */
    public void subscriptionAdd(SubscriptionEvent e) throws org.xmlBlaster.util.XmlBlasterException;

    /**
     * Invoked when subscription does a logout
     */
    public void subscriptionRemove(SubscriptionEvent e) throws org.xmlBlaster.util.XmlBlasterException;
}
