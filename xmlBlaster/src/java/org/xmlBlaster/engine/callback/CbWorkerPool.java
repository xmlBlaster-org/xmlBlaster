/*------------------------------------------------------------------------------
Name:      CbWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback.
Version:   $Id: CbWorkerPool.java,v 1.6 2002/05/26 16:32:01 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.jutils.runtime.Sleeper;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;


/**
 * Pool of threads doing a callback.
 */
public class CbWorkerPool
{
   public final String ME = "CbWorkerPool";
   private final Global glob;
   private final LogChannel log;
   private final PooledExecutor pool;
   private int maximumPoolSize;
   private int minimumPoolSize;
   private int createThreads;

   /**
    * @param maxWorkers Maximum allowed callback threads
    */
   public CbWorkerPool(Global glob)
   {
      this.glob = glob;
      this.log = glob.getLog("cb");
      this.pool = new PooledExecutor(new LinkedQueue());

      maximumPoolSize = glob.getProperty().get("cb.maximumPoolSize", 200);
      minimumPoolSize = glob.getProperty().get("cb.minimumPoolSize", 10);
      createThreads = glob.getProperty().get("cb.createThreads", minimumPoolSize);

      pool.setMaximumPoolSize(maximumPoolSize);
      pool.setMinimumPoolSize(minimumPoolSize);
      pool.createThreads(createThreads);
      pool.setKeepAliveTime(-1); // Threads live forever
      pool.waitWhenBlocked();
   }

   public final void execute(MsgQueue queue, java.lang.Runnable command) throws XmlBlasterException
   {
      try {
         queue.setCbWorkerIsActive(true);
         pool.execute(command);
      }
      catch (Throwable e) {
         queue.setCbWorkerIsActive(false);
         log.error(ME, "Callback failed: " + e.toString());
         throw new XmlBlasterException(ME, "Callback failed: " + e.toString());
      }
   }

   public String getStatistic()
   {
      return "Active threads=" + pool.getPoolSize() + " of max=" + pool.getMaximumPoolSize();
   }

   public void shutdownAfterProcessingCurrentlyQueuedTasks()
   {
      pool.shutdownAfterProcessingCurrentlyQueuedTasks();
   }
}

