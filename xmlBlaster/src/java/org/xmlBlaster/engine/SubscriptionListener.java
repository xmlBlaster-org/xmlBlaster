/*------------------------------------------------------------------------------
Name:      SubscriptionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on subscriptionRemove and subscriptionAdded events
Version:   $Id: SubscriptionListener.java,v 1.1 1999/11/18 16:59:56 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on subscriptionRemove and subscriptionAdded events. 
 * <p>
 * The events are fired by the Authenticate object.
 *
 * @version $Id: SubscriptionListener.java,v 1.1 1999/11/18 16:59:56 ruff Exp $
 * @author Marcel Ruff
 */
public interface SubscriptionListener extends java.util.EventListener {
    /**
     * Invoked on successfull subscription login
     */
    public void subscriptionAdd(SubscriptionEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;

    /**
     * Invoked when subscription does a logout
     */    
    public void subscriptionRemove(SubscriptionEvent e) throws org.xmlBlaster.serverIdl.XmlBlasterException;
}
