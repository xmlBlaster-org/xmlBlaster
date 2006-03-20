/*------------------------------------------------------------------------------
Name:      SubscriptionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on subscriptionRemove and subscriptionAdded events
Version:   $Id: I_SubscriptionListener.java 13230 2005-02-13 16:36:13Z laghi $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on subscriptionRemove and subscriptionAdded events.
 * <p>
 * The events are fired by the RequestBroker instance.
 *
 * @author Marcel Ruff
 */
public interface I_TopicListener extends java.util.EventListener {
   /**
    * Invoked on topic lifecycle change. 
    */
   public void changed(TopicEvent e) throws org.xmlBlaster.util.XmlBlasterException;
}
