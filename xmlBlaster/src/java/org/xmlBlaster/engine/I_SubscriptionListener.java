/*------------------------------------------------------------------------------
Name:      SubscriptionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on subscriptionRemove and subscriptionAdded events
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on subscriptionRemove and subscriptionAdded events.
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Id$
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
