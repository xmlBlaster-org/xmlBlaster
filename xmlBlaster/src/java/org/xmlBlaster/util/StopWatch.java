package org.xmlBlaster.util;

import java.text.NumberFormat;
import java.text.DecimalFormat;

/*------------------------------------------------------------------------------
Name:      StopWatch.java
Project:   jutils.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Handling the Client data
Version:   $Id: StopWatch.java 5104 2002-05-27 15:23:47Z ruff $
------------------------------------------------------------------------------*/
/**
 * Measure the elapsed time.
 * <p />
 * Use this helper class if you want to measure elapsed time in some code fragment
 * <p />
 * Example:
 * <pre>
 * </pre>
 * @author Marcel Ruff
 */
public class StopWatch
{
   private long startTime;
   private long stopTime;

   public StopWatch() {
      restart();
   }

   /**
   * Return the elapsed milliseconds since creation or since the last restart().
   * <p />
   * @return elapsed Milliseconds since creation or restart()
   */
   public final long elapsed() {
      if (stopTime == -1)
         return System.currentTimeMillis() - startTime;
      return stopTime - startTime;
   }


   /**
    * Stop the watch
    */
   public final void stop() {
      if (stopTime == -1)
         stopTime = System.currentTimeMillis();
   }


   /**
   * Returns a nice string with elapsed time.
   * Resets the stop watch.
   * @return The elapsed time in a nice formatted string
   */
   public final String nice() {
      String str = toString();
      restart();
      return str;
   }

   /**
   * Returns a nice string with elapsed time per iteration.
   * Stops the StopWatch, but does not reset the stop watch.
   * @return The elapsed time in a nice formatted string
   */
   public final String nice(long iterations) {
      stop();
      long elapsed = elapsed();
      double perMilli = (double)iterations / (double)elapsed;
      DecimalFormat df = ((DecimalFormat)NumberFormat.getInstance());
      if (perMilli > 10000.) {
         df.applyPattern("########");
         return Timestamp.millisToNice(elapsed) + " for " + iterations + " iterations -> " + df.format(perMilli/1000) + " iterations/microsecond";
      }
      else if (perMilli > 100.)
         df.applyPattern("########");
      else if (perMilli > 10.)
         df.applyPattern("######.#");
      else if (perMilli > 0.1) {
         df.applyPattern("######.#");
         return Timestamp.millisToNice(elapsed) + " for " + iterations + " iterations -> " + df.format(perMilli*1000) + " iterations/second";
      }
      else {
         df.applyPattern("##########");
         return Timestamp.millisToNice(elapsed) + " for " + iterations + " iterations -> " + df.format(perMilli*1000) + " iterations/second";
      }
      return Timestamp.millisToNice(elapsed) + " for " + iterations + " iterations -> " + df.format(perMilli) + " iterations/millisecond";
   }

   /**
   * Returns a nice string with elapsed time.
   * @param restart true: Resets the stop watch.
   * @return The elapsed time in a nice formatted string
   */
   public final String nice(boolean restart) {
      String str = toString();
      if (restart)
         restart();
      return str;
   }

   /**
   * Nicely formatted output containing elapsed time since
   * Construction or since last restart().
   * <p />
   * @return The elapsed time in a nice formatted string
   */
   public final String toString() {
      return Timestamp.millisToNice(elapsed());
   }

   /**
   * Reset and start the stop watch for a new measurement cycle.
   * <p />
   */
   public final void restart() {
      startTime = System.currentTimeMillis();
      stopTime = -1;
   }

   /**
   * Only for testing.
   * <p />
   * Invoke:    java org.xmlBlaster.util.StopWatch
   */
   public static void main(String args[]) throws Exception {
      StopWatch stop = new StopWatch();
      double val = 1.;
      int num = 100000;
      for (int ii = 0; ii < num; ii++) {
         val *= val;
      }
      stop.stop();
      System.out.println("Time for 100000 loops = " + stop.elapsed() + " Millisec");
      System.out.println("Time for 100000 loops = " + stop.nice(false));
      System.out.println("Time for 100000 loops = " + stop.nice(num));

      stop.restart();
      try { Thread.sleep(4000L); } catch( InterruptedException i) {}
      System.out.println("4000 -> " + stop.nice(1));
      System.out.println("4000 -> " + stop.nice(false));
   }
}
