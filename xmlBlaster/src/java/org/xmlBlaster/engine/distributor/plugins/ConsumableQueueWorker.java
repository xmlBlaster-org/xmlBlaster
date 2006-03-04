/*------------------------------------------------------------------------------
Name:      ConsumableQueueWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.distributor.plugins;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * ConsumableQueueWorker processes the distribution of messages on 
 * topics which have a ConsumableQueue plugin defined.
 * This runs in its own thread, so when it is stared, the invoker 
 * thread can return without waiting for all messages to be processed.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ConsumableQueueWorker implements Runnable {

   private final static String ME = "ConsumableQueueWorker";
   // private Global global;
   private static Logger log = Logger.getLogger(ConsumableQueueWorker.class.getName());
   private ConsumableQueuePlugin consumableQueuePlugin;
   

   public ConsumableQueueWorker(Logger log, ConsumableQueuePlugin consumableQueuePlugin) {
      this.log = log;
      if (log.isLoggable(Level.FINER)) log.finer("constructor");
      this.consumableQueuePlugin = consumableQueuePlugin;
   }


   public void run() {
      if (log.isLoggable(Level.FINER)) log.finer("run");
      this.consumableQueuePlugin.processHistoryQueue();
   }

}
