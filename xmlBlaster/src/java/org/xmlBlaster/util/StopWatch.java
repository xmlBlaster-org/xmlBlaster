/*------------------------------------------------------------------------------
Name:      StopWatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: StopWatch.java,v 1.6 2000/05/27 11:57:44 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Measure the elapsed time.
 * <p />
 * Use this helper class if you want to measure elapsed time in some code fragment
 */
public class StopWatch
{
   private long startTime;
   private long stopTime;

   public StopWatch()
   {
      restart();
   }


   /**
    * Return the elapsed milliseconds since creation or since the last restart().
    * <p />
    * @return elapsed Milliseconds since creation or restart()
    */
   public final long elapsed()
   {
      if (stopTime == -1)
         return System.currentTimeMillis() - startTime;

      return stopTime - startTime;
   }


   /**
    * Returns a nice string with elapsed time.
    * Resets the stop watch.
    * @return The elapsed time in a nice formatted string
    */
   public final String nice()
   {
      String str = toString();

      restart();

      return str;
   }


   /**
    * Nicely formatted output containing elapsed time since
    * Construction or since last restart().
    * <p />
    * @return The elapsed time in a nice formatted string
    */
   public final String toString()
   {
      return TimeHelper.millisToNice(elapsed());
   }


   /**
    * Reset and start the stop watch for a new measurement cycle.
    * <p />
    */
   public final void restart()
   {
      startTime = System.currentTimeMillis();
      stopTime = -1;
   }


   /**
    * Only for testing.
    * <p />
    * Invoke:    java org.xmlBlaster.util.StopWatch
    */
   public static void main(String args[]) throws Exception
   {
      String me = "StopWatch-Tester";

      StopWatch stop = new StopWatch();

      double val = 1.;
      for (int ii=0; ii<100000; ii++)
      {
         val *= val;
      }

      Log.info(me, "Time for 100000 loops = " + stop.elapsed() + " Millisec");
      Log.info(me, "Time for 100000 loops = " + stop.nice());

      Log.exit(me, "Good bye");
   }
}


