/*------------------------------------------------------------------------------
Name:      Embeeded.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.memoryleak;

import org.jutils.log.LogChannel;
import org.jutils.runtime.ThreadLister;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.engine.RunlevelManager;

import org.xmlBlaster.test.Util;


/**
 * Here we start stop an embeeded xmlBlaster instance to test memory an thread consumption. 
 */
public class Embeeded
{
   private static String ME = "Embeeded";
   private final Global glob;
   private final LogChannel log;
   private int serverPort = 7615;

   /**
    * Constructs the Embeeded object.
    */
   public Embeeded(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(null);
      Thread.currentThread().setName("EmbeededTest.MainThread");
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void testLoop() {
      boolean interactive = this.glob.getProperty().get("interactive", false);
      long sleep = this.glob.getProperty().get("sleep", 1000L);

      int n = 100;
      for(int i=0; i<n; i++) {

         if (interactive) {
            log.info(ME, "Hit a key to start embeeded xmlBlaster #" + (i+1) + "/" + n);
            try { System.in.read(); } catch(java.io.IOException e) {}
         }
         else {
            log.info(ME, "********* Start embeeded xmlBlaster #" + (i+1) + "/" + n);
         }

         if (i==0) {
            //System.gc();
            log.info(ME, "Threads before starting #" + (i+1) + " num=" + ThreadLister.countThreads());
            ThreadLister.listAllThreads(System.out);
         }

         //this.glob.init(Util.getOtherServerPorts(serverPort));
         EmbeddedXmlBlaster embeddedXmlBlaster = EmbeddedXmlBlaster.startXmlBlaster(this.glob);
         
         log.info(ME, "Threads with alive server #" + (i+1) + " num=" + ThreadLister.countThreads());
         ThreadLister.listAllThreads(System.out);

         if (interactive) {
            log.info(ME, "Hit a key to stop embeeded xmlBlaster #" + (i+1) + "/" + n);
            try { System.in.read(); } catch(java.io.IOException e) {}
         }
         else {
            log.info(ME, "********* Stop embeeded xmlBlaster #" + (i+1) + "/" + n);
         }

         embeddedXmlBlaster.stopServer(true);
         embeddedXmlBlaster = null;
         //Util.resetPorts();

         log.info(ME, "Threads after stopping #" + (i+1) + " num=" + ThreadLister.countThreads());
         ThreadLister.listAllThreads(System.out);

         if (!interactive) {
            try { Thread.currentThread().sleep(sleep); } catch( InterruptedException e) {}
         }
      }
      log.info(ME, "Done");
   }
   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.memoryleak.Embeeded -interactive true
    * <pre>
    */
   public static void main(String args[]) {
      Embeeded embeeded = new Embeeded(new Global(args));
      embeeded.testLoop();
   }
}

