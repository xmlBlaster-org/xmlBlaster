package org.xmlBlaster.test.classtest.queue;

// import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.DummyEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.jutils.log.LogChannel;
import java.sql.SQLException;
import org.xmlBlaster.util.enum.PriorityEnum;
import java.util.ArrayList;
import org.xmlBlaster.util.queue.I_Queue;

class QueueThread extends Thread {

   private static final String ME = "QueueThread";

   private I_Queue queue = null;
   private int  sweeps = 5;
   private LogChannel log = null;
   private String name = null;
   private int sizeOfMsg = 0;
   private Global glob = null;

   public static int counter = 0;
   private ArrayList entryList;

   public QueueThread(Global glob, String name, I_Queue queue, LogChannel log, int sweeps, int sizeOfMsg)
   throws XmlBlasterException, SQLException {
      super();
      this.glob = glob;
      this.log = this.glob.getLog("queuethread");
      this.name = name;
      this.queue = queue;
      this.sweeps = sweeps;
      this.sizeOfMsg = sizeOfMsg;
//      this.log = log;
      this.counter++;
   }

   protected void runPut() {
      this.entryList = new ArrayList(this.sweeps);
      for (int i=0; i < this.sweeps; i++) {
         log.trace(this.name, "runPut sweep " + i + " entered");
         try {
            this.log.trace(this.name, "runPut sweep: " + i + " still running: " + this.counter);
            DummyEntry entry = new DummyEntry(this.glob, PriorityEnum.NORM_PRIORITY, this.queue.getStorageId(), this.sizeOfMsg, true);
            this.entryList.add(entry);
            this.queue.put(entry, false);
            this.log.trace(this.name, "after invocation");
         }
         catch (Exception ex) {
            log.error(this.name, "exception in thread " + ex.getMessage());
            ex.printStackTrace();
         }
      }
   }


   protected void runPeekAllRemoveOneByOne() {
      try {
         // peek all messages one single sweep ...
         queue.peek(this.sweeps, -1L);

         // remove all messages one by one ..
         for (int j=0; j < this.sweeps; j++) {
            queue.removeRandom(((DummyEntry)entryList.get(j)));
         }
      }
      catch (Exception ex) {
         log.error(this.name, "exception in thread " + ex.getMessage());
         ex.printStackTrace();
      }

   }



   public void run() {
      log.call(ME, "run method entered");
      runPut();
      this.counter--;
   }
}
