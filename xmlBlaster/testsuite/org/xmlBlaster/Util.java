/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some helper methods for test clients
Version:   $Id: Util.java,v 1.3 2000/06/18 15:22:02 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.Log;


/**
 * Some helper methods for test clients
 */
public class Util
{
   private final static String ME = "Util";


   /**
    * Stop execution for some given milliseconds
    * @param millis amount of milliseconds to wait
    */
   public static void delay(long millis)
   {
      try {
          Thread.currentThread().sleep(millis);
      }
      catch( InterruptedException i)
      {}
   }


   /**
    * Stop execution until a key is hit
    * @param text This text is shown on command line
    */
   public static void ask(String text)
   {
      Log.plain(ME, "################### " + text + ": Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }


}


