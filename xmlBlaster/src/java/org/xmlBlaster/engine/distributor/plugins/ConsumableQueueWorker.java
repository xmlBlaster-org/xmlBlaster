/*------------------------------------------------------------------------------
Name:      ConsumableQueueWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.distributor.plugins;

import org.jutils.log.LogChannel;

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
   private LogChannel log;
   private ConsumableQueuePlugin consumableQueuePlugin;
   

   public ConsumableQueueWorker(LogChannel log, ConsumableQueuePlugin consumableQueuePlugin) {
      this.log = log;
      if (this.log.CALL) log.call(ME, "constructor");
      this.consumableQueuePlugin = consumableQueuePlugin;
   }


   public void run() {
      if (this.log.CALL) log.call(ME, "run");
      this.consumableQueuePlugin.processHistoryQueue();
   }

}
