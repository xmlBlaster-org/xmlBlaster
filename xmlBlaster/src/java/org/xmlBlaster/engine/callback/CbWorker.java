/*------------------------------------------------------------------------------
Name:      CbWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbWorker.java,v 1.2 2001/02/14 21:36:28 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.jutils.runtime.Sleeper;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * Queueing messages to send back to a client.
 */
public class CbWorker extends Thread
{
   public final String ME = "CbWorker";
   public boolean keepRunning = true;
   public CbWorker()
   {
      if (Log.CALL) Log.call(ME, "Entering Constructor");
   }

   public void initialize()
   {
      Log.info(ME, "Initialized");
   }

   private void doWork() throws InterruptedException
   {
      Log.info(ME, "Working ...");
      Sleeper.sleep(5000L);
      Log.info(ME, "Work done");
   }

   public void run()
   {
      while (keepRunning) {
         try {
            try { this.wait(); } catch (InterruptedException e) { }
            Log.info(ME, "Doing a new job ...");
            doWork();
         }
         catch (InterruptedException e) { // e.g. stop a blocking socket with interrupt()
            Log.warn(ME, "Thread is interrupted during its work: " + e.toString());
         }
      }
   }
}

