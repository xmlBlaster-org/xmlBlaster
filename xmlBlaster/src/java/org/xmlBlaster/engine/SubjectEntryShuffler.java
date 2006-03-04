/*------------------------------------------------------------------------------
Name:      SubjectEntryShuffler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;

import java.util.HashSet;
import java.util.Set;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.authentication.SubjectInfo;

import EDU.oswego.cs.dl.util.concurrent.Channel;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;

/**
 * SubjectEntryShuffler
 * 
 * @author <a href="mailto:mr@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class SubjectEntryShuffler implements Runnable {

   private final static String ME = "SubjectEntryShuffler";
   private Global global;
   private static Logger log = Logger.getLogger(SubjectEntryShuffler.class.getName());
   
   private Channel channel;
   private Set set;
   
   /**
    * The constructor starts the thread as a daemon and waits for
    * shuffle invocations.
    */
   SubjectEntryShuffler(Global global) {
      this.global = global;

      if (log.isLoggable(Level.FINER)) this.log.finer("constructor");
      this.channel = new LinkedQueue();
      this.set = new HashSet();
      Thread thread = new Thread(this, "XmlBlaster."+ME);
      thread.setDaemon(true);
      thread.start(); 
   }

   /**
    * shuffles the entries from the SubjectQueue to a SessionQueue in 
    * an own thread.
    * @param info
    */
   public void shuffle(SubjectInfo info) {
      if (log.isLoggable(Level.FINER)) this.log.finer("shuffle SubjectInfo '" + info.getId() + "'");
      try {
         synchronized(this.set) {
            if (this.set.contains(info)) return;
            this.set.add(info);
            this.channel.put(info);
         }
         if (log.isLoggable(Level.FINER)) this.log.finer("shuffle SubjectInfo '" + info.getId() + "' put has returned");
      }
      catch (InterruptedException ex) {
         // Is OK on shutdown
         log.warning("shuffle InterruptedException occured " + ex.getMessage());
         ex.printStackTrace();
      }
      catch (Throwable ex) {
         log.severe("shuffle a Throwable occured " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   /**
    * @see java.lang.Runnable#run()
    */
   public void run() {
      log.info("run: shuffling Thread started");
      while (true) {
         try {
            SubjectInfo info = (SubjectInfo)this.channel.take();
            synchronized(this.set) {
               this.set.remove(info);
            }

            if (log.isLoggable(Level.FINE)) this.log.fine("run: shuffling for subject '" + info.getId() + "' starts");
            info.forwardToSessionQueue();
            if (log.isLoggable(Level.FINE)) this.log.fine("run: shuffling for subject '" + info.getId() + "' completed");
         }
         catch (InterruptedException ex) {
            log.severe("run InterruptedException occured " + ex.getMessage());
            ex.printStackTrace();
         }
         catch (Throwable ex) {
            log.severe("run a Throwable occured " + ex.getMessage());
            ex.printStackTrace();
         }
         
      }
   }
   

}
