package org.xmlBlaster.util.log;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * @author Marcel
 *
 */
public class XbHandler extends Handler {
   private final String id;
   private static long instanceCounter;
   private static final String ESC          = "\033[0m"; // Reset color to original values
   private static final String BOLD         = "\033[1m";

   private static final String RED_BLACK    = "\033[31;40m";
   private static final String GREEN_BLACK  = "\033[32;40m";
   private static final String YELLOW_BLACK = "\033[33;40m";
   private static final String BLUE_BLACK   = "\033[34;40m";
   private static final String PINK_BLACK   = "\033[35;40m";
   private static final String LTGREEN_BLACK= "\033[36;40m";
   private static final String WHITE_BLACK  = "\033[37;40m";
   
   public XbHandler() {
      this("default-"+instanceCounter++);
   }
   
   public XbHandler(String id) {
      this.id = id;
   }

   public void close() throws SecurityException {
   }

   public void flush() {
   }

   /* (non-Javadoc)
    * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
    */
   public void publish(LogRecord record) {
      System.out.println("[XbHandler-"+this.id+"] " + RED_BLACK + record.getLevel() + ESC + " " + record.getMessage());
   }

   public static void main(String[] args) {
   }
}
