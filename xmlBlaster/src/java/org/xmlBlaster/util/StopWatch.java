/*------------------------------------------------------------------------------
Name:      StopWatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.1 $  $Date: 1999/11/08 12:58:17 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Measure the elapsed time
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
    * @return elapsed Millisekonds since creation or restart()
    */
   public final long elapsed()
   {
      if (stopTime == -1)
         stopTime = System.currentTimeMillis();
   
      return stopTime - startTime;
   }

   public final String elapsedNice()
   {
      long millis = elapsed();
      long seconds = millis / 1000;
      long sec = (seconds % 3600) % 60;
      long min = (seconds % 3600) / 60;
      long hour = seconds / 3600;
      String str = "" + hour + " h " + min + " min " + sec + " sec " + millis % 1000 + " millis";
      return str;
   }

   public final void restart()
   {
      startTime = System.currentTimeMillis();
      stopTime = -1;
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.util.StopWatch
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

      Log.info(me, "Time for 100000 loops = " + stop.elapsedNice());
      Log.info(me, "Time for 100000 loops = " + stop.elapsed() + " Millisec");

      Log.exit(me, "Good bye");
   }

}


