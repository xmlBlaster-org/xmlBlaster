/*------------------------------------------------------------------------------
Name:      TopicEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * An event which indicates that a new TopicHandler is created or destroyed. 
 * <p />
 * It carries the locked TopicHandler reference inside.
 *
 * @author xmlBlaster@marcelruff.info
 */
public class TopicEvent extends java.util.EventObject {
   private static final long serialVersionUID = 1L;

   /**
    * Constructs a TopicEvent object.
    *
    * @param source the topicHandler object which changed its state
    */
   public TopicEvent(TopicHandler topicHandler) {
       super(topicHandler);
   }

   /**
    * Returns the originator of the event.
    *
    * @return the TopicHandler which is locked (no other thread has access)
    */
   public TopicHandler getTopicHandler() {
       return (TopicHandler)source;
   }
}
