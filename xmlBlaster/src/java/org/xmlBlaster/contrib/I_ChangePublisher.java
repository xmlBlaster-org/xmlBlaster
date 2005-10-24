/*------------------------------------------------------------------------------
Name:      I_ChangePublisher.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.util.Map;


/**
 * Interface to hide the publisher destination. 
 * <p>
 * A typical plugin publishes the changes to xmlBlaster MoM
 * but could be any other data sink like JMS or file system as well.
 * </p>
 * @author Marcel Ruff
 */
public interface I_ChangePublisher extends java.util.EventListener, I_ContribPlugin {
   /**
    * After creation this method is called.
    * The plugin must register itself into global scope with
    * <tt>info.setObject("org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher", this)</tt>
    * to be available for others.  
    * @param info The configuration environment
    * @throws Exception MoM specific
    */
   void init(I_Info info) throws Exception;
   
   /**
    * Send the message to the MoM. 
    * @param changeKey Can be used to create the topic name
    * @param message The message content to send
    * @param attrMap An optional map with attributes or null
    * @return A unique identifier of the sent message
    * @throws Exception On sending problems
    */
   String publish(String changeKey, byte[] message, Map attrMap) throws Exception;

   /**
    * Register for alerts when the data source has changed. 
    * <p>
    * This funtionality is plugin depending, for example the  
    * xmlBlaster plugin has a configuration option to subscribe on a
    * alert topic and listens if somebody publishes a message to it.
    * If such a message arrives we trigger a new database poll.
    * </p> 
    * @param update The callback interface to receive the notification
    * @param attrs extra parameters to pass for the registration. For example
    * if one implementation wants to do a specific extra subscription it would
    * pass the quality of service in the attributes.
    * 
    * @return true if a notification is available (is configured)
    * @throws Exception The MoM specific exception
    */
   boolean registerAlertListener(I_Update update, Map attrs) throws Exception;
   
   /**
    * Cleanup resources. 
    * <p>
    * Can be called multiple times if instance is reused from different plugins.
    * </p>  
    */
   void shutdown();
}
