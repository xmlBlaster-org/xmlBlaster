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

   /* The priority by which it will be invoked. Lower numbers are invoked first on subscribe and last on unsubscribe */
   public final static Integer PRIO_01 = new Integer(1);
   public final static Integer PRIO_05 = new Integer(5);
   public final static Integer PRIO_10 = new Integer(10);
   
   /**
    * The priority by which it will be invoked. Lower numbers are invoked first on subscribe and last on unsubscribe.
    * @return
    */
   public Integer getPriority();
   
   /**
    * Invoked on successful subscription login
    */
   public void subscriptionAdd(SubscriptionEvent e) throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * Invoked when subscription does a logout
    */
   public void subscriptionRemove(SubscriptionEvent e) throws org.xmlBlaster.util.XmlBlasterException;
}
