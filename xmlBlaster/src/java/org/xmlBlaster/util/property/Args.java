package org.xmlBlaster.util.property;

import org.xmlBlaster.util.XmlBlasterException;

/*------------------------------------------------------------------------------
Name:      Args.java
Project:   jutils.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Evaluate parameters at process startup
Version:   $Id: Args.java 4746 2002-04-25 10:44:46Z ruff $
------------------------------------------------------------------------------*/

/**
 * Evaluate parameters at process startup (args).
 * <p />
 * JDK 1.1 or higher only.
 * <p />
 * Example:
 * <pre>
 * If you call your application e.g. like that:
 *
 *    java myapp -file myfile.txt
 *
 * you can access the paramater:
 *
 *    String fileName = Args.getArg(args, "-file", "dummy.txt");
 *
 * (dummy.txt would be the fallback if no -file ... is given)
 * </pre>
 *
 * @author Marcel Ruff
 */
public class Args
{
   private final static String ME = "Args";
   private static String[] args = null;

   /**
   * Try to find the given key in the args list.
   *
   * @param the argument array
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The String value for the given key
   */
   public final static String getArg(String[] args, String key, String defaultVal) throws XmlBlasterException {
      if (args == null)
         return defaultVal;
      for (int ii = 0; ii < args.length; ii++) {
         if (args[ii] == null) {
            throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for args[" + ii + "], null is not allowed");
         }
         if (args[ii].equals(key)) {
            if (ii >= args.length - 1)
               throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for parameter " + key);
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
   public final static byte[] getArg(String[] args, String key, byte[] defaultVal) throws XmlBlasterException {
      if (args == null)
         return defaultVal;
      for (int ii = 0; ii < args.length; ii++) {
         if (args[ii] == null) {
            throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for args[" + ii + "], null is not allowed");
         }
         if (args[ii].equals(key)) {
            if (ii >= args.length - 1)
               throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for parameter " + key);
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
   public final static int getArg(String[] args, String key, int defaultVal) throws XmlBlasterException {
      if (args == null)
         return defaultVal;
      for (int ii = 0; ii < args.length; ii++) {
         if (args[ii] == null) {
            throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for args[" + ii + "], null is not allowed");
         }
         if (args[ii].equals(key)) {
            if (ii >= args.length - 1)
               throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for parameter " + key);
            String str = args[++ii];
            if (str == null)
               return defaultVal;
            try {
               return Integer.parseInt(str);
            }
            catch (Exception e) {
               return defaultVal;
            }
         }
      }
      return defaultVal;
   }

   /**
   * Try to find the given key in the args list.
   * <p />
   * true for one of "true", "yes", "1", "ok" <br />
   * false for "false", "0", "no" <br />
   * else the defaultVal
   * @param the argument array
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The boolean value for the given key
   */
   public final static boolean getArg(String[] args, String key, boolean defaultVal) throws XmlBlasterException {
      if (args == null)
         return defaultVal;
      for (int ii = 0; ii < args.length; ii++) {
         if (args[ii] == null) {
            throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for args[" + ii + "], null is not allowed");
         }
         if (args[ii].equals(key)) {
            if (ii >= args.length - 1)
               throw new XmlBlasterException(ME + ".InvalidKey", "Please specify a value for parameter " + key);
            String str = args[++ii];
            if (str == null)
               return defaultVal;
            try {
               return Property.toBool(str);
            }
            catch (Exception e) {
               return defaultVal;
            }
         }
      }
      return defaultVal;
   }

   /**
   * Try to find the given arg without a value (flag parameter).
   *
   * @param the argument array
   * @param arg The argument to find (without a value)
   * @return true found it
   */
   public final static boolean getArg(String[] args, String arg) {
      if (args == null)
         return false;
      for (int ii = 0; ii < args.length; ii++) {
         if (args[ii] == null) {
            return false;
         }
         if (args[ii].equals(arg)) {
            return true;
         }
      }
      return false;
   }

   /*
   * sets the args as static
   */
   public final static void setArg (String[] myargs) {
      args = myargs;
   }

   /*
   * gets the arg from static arg as string
   */
   public final static String getArg (String arg, String defVal) throws XmlBlasterException {
      if (args == null)
         return defVal;
      return getArg (args, arg, defVal);
   }

   /*
   * gets the arg from static arg as integger
   */
   public final static int getArg (String arg, int defVal) throws XmlBlasterException {
      if (args == null)
         return defVal;
      return getArg (args, arg, defVal);
   }
   /*
   * gets the arg from static arg as boolean
   */
   public final static boolean getArg (String arg, boolean defVal) throws XmlBlasterException {
      if (args == null)
         return defVal;
      return getArg (args, arg, defVal);
   }

   /**
   * For testing only
   * <p />
   * java org.jutils.init.Args
   */
   public static void main(String args[]) {
      String ME = "Property";
      {
         String[] arr = new String[2];
         arr[0] = "-hello";
         arr[1] = "1";
         try {
            System.out.println("-hello=" + Args.getArg(arr, arr[0], false));
         }
         catch (Exception e) {
            System.err.println(e.toString());
         }
      }
      {
         String[] arr = new String[2];
         arr[0] = "hello";
         arr[1] = "1";
         try {
            System.out.println("hello=" + Args.getArg(arr, arr[0], false));
         }
         catch (Exception e) {
            System.err.println(e.toString());
         }
      }
      {
         String[] arr = new String[2];
         arr[0] = null;
         arr[1] = null;
         try {
            Args.getArg(arr, arr[0], false);
            System.err.println("ERROR: Not excpected");
         }
         catch (Exception e) {
            System.out.println("OK, expected exception for null args: " + e.toString());
            e.printStackTrace();
         }
      }
   }
}
