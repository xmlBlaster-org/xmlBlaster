/*------------------------------------------------------------------------------
Name:      Args.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Evaluate parameters at process startup
Version:   $Id: Args.java,v 1.3 2000/01/19 21:03:48 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Evaluate parameters at process startup (args).
 */
public class Args
{
   private final static String ME = "Args";


   /**
    * Try to find the given key in the args list.
    *
    * @param the argument array
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The String value for the given key
    */
   public final static String getArg(String[] args, String key, String defaultVal)
   {
      if (args == null)
         return defaultVal;

      for (int ii=0; ii<args.length; ii++) {
         if (args[ii].equals(key)) {
            if (ii >= args.length-1) Log.panic(ME, "Please specify a value for parameter " + key );
            return args[++ii];
         }
      }
      return defaultVal;
   }


   /**
    * Try to find the given key in the args list.
    *
    * @param the argument array
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The byte[] value for the given key
    */
   public final static byte[] getArg(String[] args, String key, byte[] defaultVal)
   {
      if (args == null)
         return defaultVal;

      for (int ii=0; ii<args.length; ii++) {
         if (args[ii].equals(key)) {
            if (ii >= args.length-1) Log.panic(ME, "Please specify a value for parameter " + key );
            return args[++ii].getBytes();
         }
      }
      return defaultVal;
   }


   /**
    * Try to find the given key in the args list.
    *
    * @param the argument array
    * @param key the key to look for
    * @param defaultVal the default value to return if key is not found
    * @return The int value for the given key
    */
   public final static int getArg(String[] args, String key, int defaultVal)
   {
      if (args == null)
         return defaultVal;

      for (int ii=0; ii<args.length; ii++) {
         if (args[ii].equals(key)) {
            if (ii >= args.length-1) Log.panic(ME, "Please specify a value for parameter " + key );
            String str = args[++ii];
            if (str == null)
               return defaultVal;
            try {
               return Integer.parseInt(str);
            } catch (Exception e) {
               return defaultVal;
            }
         }
      }
      return defaultVal;
   }


   /**
    * Try to find the given arg.
    *
    * @param the argument array
    * @param arg The argument to find (without a value)
    * @return true found it
    */
   public final static boolean getArg(String[] args, String arg)
   {
      if (args == null)
         return false;

      for (int ii=0; ii<args.length; ii++) {
         if (args[ii].equals(arg)) {
            return true;
         }
      }
      return false;
   }

}


