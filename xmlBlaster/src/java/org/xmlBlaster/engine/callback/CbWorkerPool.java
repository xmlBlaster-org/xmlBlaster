/*------------------------------------------------------------------------------
Name:      CbWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback.
Version:   $Id: CbWorkerPool.java,v 1.2 2001/02/14 21:36:28 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.xmlBlaster.util.Log;
import org.jutils.runtime.Sleeper;


/**
 * Pool of threads doing a callback.
 */
public class CbWorkerPool
{
   public final String ME = "CbWorkerPool";
   public final int MAX_CB_THREADS;
   private int numActiveWorkers = 0;
   private final long SLEEP_PERIOD = 10L;
   private final ThreadGroup threadGroup;

   /**
    * @param maxWorkers Maximum allowed callback threads
    */
   public CbWorkerPool(int maxWorkers)
   {
      this.MAX_CB_THREADS = maxWorkers;
      this.threadGroup = new ThreadGroup("CallbackWorkerThreadGroup");
   }

   public CbWorker reserve()
   {
      int counter = 0;
      while (true) {
         if (numActiveWorkers < MAX_CB_THREADS) {
            // Not completely thread save, but this is not crucial here
            numActiveWorkers++;
            return new CbWorker();
         }
         if (counter == 0 || ((counter * SLEEP_PERIOD) % 10000) == 0)
            Log.warn(ME, "No callback thread available.");
         Sleeper.sleep(SLEEP_PERIOD);
         counter++;
      }
   }

   public void release(CbWorker cbWorker)
   {
      numActiveWorkers--;
      cbWorker = null;
   }
}

