/*------------------------------------------------------------------------------
Name:      CbWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback. 
Version:   $Id: CbWorkerPool.java,v 1.1 2000/12/29 14:46:22 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.jutils.runtime.Sleeper;
import java.util.*;


/**
 * Pool of threads doing a callback. 
 */
public class CbWorkerPool
{
   public final String ME = "CbWorkerPool";
   public final int MAX_CB_THREADS;
   private int numActiveWorkers = 0;
   private final long SLEEP_PERIOD = 10L;

   /**
    * @param maxWorkers Maximum allowed callback threads
    */
   public CbWorkerPool(int maxWorkers)
   {
      this.MAX_CB_THREADS = maxWorkers;
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

