/*------------------------------------------------------------------------------
Name:      ConsumableQueueWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.distributor.plugins;

import org.jutils.log.LogChannel;

/**
 * ConsumableQueueWorker
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
