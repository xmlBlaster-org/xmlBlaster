/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some helper methods for test clients
Version:   $Id: Util.java,v 1.1 1999/12/08 12:16:18 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;


/**
 * Some helper methods for test clients
 */
public class Util
{
   private final static String ME = "Util";

   public static void delay(long millis)
   {
      try {
          Thread.currentThread().sleep(millis);
      }
      catch( InterruptedException i)
      {}
   }


   public static void ask(String text)
   {
      Log.plain(ME, "################### " + text + ": Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }


}


