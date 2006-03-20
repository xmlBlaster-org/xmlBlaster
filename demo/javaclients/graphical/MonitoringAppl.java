/*------------------------------------------------------------------------------
Name:      MonitoringAppl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package javaclients.graphical;

import java.io.File;
import java.util.HashMap;

import javax.swing.JFileChooser;

import CH.ifa.draw.framework.DrawingView;
import CH.ifa.draw.samples.javadraw.JavaDrawApp;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MonitoringAppl extends JavaDrawApp {

   private Global global;
   private static Logger log = Logger.getLogger(MonitoringAppl.class.getName());
   private String ME = "MonitoringAppl";
   private HashMap subscribers;
   private int     count;
   private JFileChooser chooser;


   public MonitoringAppl(Global global) {
      super("MonitoringAppl");
      this.global = global;

      this.subscribers = new HashMap();
      this.count = 1;
   }


   public void fireViewDestroyingEvent(DrawingView view) {
      if (log.isLoggable(Level.FINER)) this.log.finer("fireViewDestroyingEvent(view)");
      MonitorSubscriber subscriber = (MonitorSubscriber)this.subscribers.remove(view);
      if (subscriber != null) subscriber.stop();
      super.fireViewDestroyingEvent(view);
   }
   
   public JFileChooser createOpenFileChooser() {
      if (log.isLoggable(Level.FINER)) this.log.finer("createOpenFileChooser");
      if (this.chooser == null) {
         this.chooser = super.createOpenFileChooser();
         this.chooser.setCurrentDirectory(new File("."));
      }
      return this.chooser;
   }

   public void fireViewCreatedEvent(DrawingView view) {
      super.fireViewCreatedEvent(view);
      MonitorSubscriber subscriber = new MonitorSubscriber(this.global, view);
      String name = "drawing" + this.count++;
      if (subscriber.start(name))
         this.subscribers.put(view, subscriber);
   }


   public void exit() {
      if (log.isLoggable(Level.FINER)) this.log.finer("exit");
      if (this.subscribers.size() > 0) {
         try {
            MonitorSubscriber[] subs = (MonitorSubscriber[])this.subscribers.values().toArray(new MonitorSubscriber[this.subscribers.size()]);
            for (int i=0; i < subs.length; i++) subs[i].stop();
         }
         catch (Throwable ex) {
            ex.printStackTrace();
         }
      }
      this.subscribers.clear();
      super.exit();
   }

   //-- main -----------------------------------------------------------

   public static void main(String[] args) {

      Global glob = new Global();
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java javaclients.graphical.MonitoringAppl -chatMaster true\n");
         System.exit(1);
      }

      MonitoringAppl window = new MonitoringAppl(glob);
      window.open();
   }

}




