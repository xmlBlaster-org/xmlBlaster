/*------------------------------------------------------------------------------
Name:      Embedded.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.memoryleak;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.runtime.ThreadLister;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.engine.runlevel.RunlevelManager;

import org.xmlBlaster.test.Util;


/**
 * Here we start stop an embeded xmlBlaster instance to test memory an thread consumption. 
 */
public class Embedded
{
   private static String ME = "Embedded";
   private final Global glob;
   private static Logger log = Logger.getLogger(Embedded.class.getName());
   private int serverPort = 7615;

   /**
    * Constructs the Embedded object.
    */
   public Embedded(Global glob) {
      this.glob = glob;

      Thread.currentThread().setName("EmbeddedTest.MainThread");
   }

   /**
    * Loop with start and stop embeded xmlBlaster. 
    */
   protected void testLoop() {
      boolean interactive = this.glob.getProperty().get("interactive", false);
      long sleep = this.glob.getProperty().get("sleep", 1000L);

      int n = 100;
      for(int i=0; i<n; i++) {

         if (interactive) {
            log.info("Hit a key to start embedded xmlBlaster #" + (i+1) + "/" + n);
            try { System.in.read(); } catch(java.io.IOException e) {}
         }
         else {
            log.info("********* Start embedded xmlBlaster #" + (i+1) + "/" + n);
         }

         if (i==0) {
            //System.gc();
            log.info("Threads before starting #" + (i+1) + " num=" + ThreadLister.countThreads());
            ThreadLister.listAllThreads(System.out);
         }

         //this.glob.init(Util.getOtherServerPorts(serverPort));
         EmbeddedXmlBlaster embeddedXmlBlaster = EmbeddedXmlBlaster.startXmlBlaster(this.glob);
         
         log.info("Threads with alive server #" + (i+1) + " num=" + ThreadLister.countThreads());
         ThreadLister.listAllThreads(System.out);

         if (interactive) {
            log.info("Hit a key to stop embedded xmlBlaster #" + (i+1) + "/" + n);
            try { System.in.read(); } catch(java.io.IOException e) {}
         }
         else {
            log.info("********* Stop embedded xmlBlaster #" + (i+1) + "/" + n);
         }

         embeddedXmlBlaster.stopServer(true);
         embeddedXmlBlaster = null;
         //Util.resetPorts();

         log.info("Threads after stopping #" + (i+1) + " num=" + ThreadLister.countThreads());
         ThreadLister.listAllThreads(System.out);

         if (!interactive) {
            try { Thread.currentThread().sleep(sleep); } catch( InterruptedException e) {}
         }
      }
      log.info("Done");
   }
   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.memoryleak.Embedded -interactive true
    * <pre>
    */
   public static void main(String args[]) {
      Embedded embedded = new Embedded(new Global(args));
      embedded.testLoop();
   }
}

