/*------------------------------------------------------------------------------
Name:      Constants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;


/**
 * Holding some Constants
 */
public class Constants
{
   private static final String ME = "Constants";

   public final static long MINUTE_IN_MILLIS = 1000L*60;
   public final static long HOUR_IN_MILLIS = MINUTE_IN_MILLIS*60;
   public final static long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
   public final static long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;



   /**
    * The minimum priority of a message (0).
    */
   public final static int MIN_PRIORITY = 0;

   /**
    * The lower priority of a message (2).
    */
   public final static int LOW_PRIORITY = 3;

   /**
    * The default priority of a message (5).
    */
   public final static int NORM_PRIORITY = 5;

   /**
    * The default priority of a message (5).
    */
   public final static int HIGH_PRIORITY = 7;

   /**
    * The maximum priority of a message.
    */
   public final static int MAX_PRIORITY = 9;


   /**
    * Parses given string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @param defaultPriority Value to use if not parseable
    * @return The int value for the message priority
    */
   public final static int getPriority(String prio, int defaultPriority) {
      if (prio != null) {
         prio = prio.trim();
         try {
            return new Integer(prio).intValue();
         } catch (NumberFormatException e) {
            prio = prio.toUpperCase();
            if (prio.startsWith("MIN"))
               return Constants.MIN_PRIORITY;
            else if (prio.startsWith("LOW"))
               return Constants.LOW_PRIORITY;
            else if (prio.startsWith("NORM"))
               return Constants.NORM_PRIORITY;
            else if (prio.startsWith("HIGH"))
               return Constants.HIGH_PRIORITY;
            else if (prio.startsWith("MAX"))
               return Constants.MAX_PRIORITY;
            else
               Log.warn(ME, "Wrong format of <priority>" + prio +
                    "</priority>, expected a number between (inclusiv) 0 - 9, setting to message priority to "
                    + defaultPriority);
         }
      }
      if (defaultPriority < Constants.MIN_PRIORITY || defaultPriority > Constants.MAX_PRIORITY) {
          Log.warn(ME, "Wrong message defaultPriority=" + defaultPriority + " given, setting to NORM_PRIORITY");
          return Constants.NORM_PRIORITY;
      }
      return defaultPriority;
   }


   /** The returned message status if OK */
   public final static String STATE_OK = "OK";
   /** The returned message status if message is expired (and therefor erased) */
   public final static String STATE_EXPIRED = "EXPIRED";
   /** The returned message status if message is explicitly erased by a call to erase() */
   public final static String STATE_ERASED = "ERASED";

   /** Type of a message queue */
   public final static String RELATING_SESSION = "session";
   /** Type of a message queue */
   public final static String RELATING_SUBJECT = "subject";
   /** Type of a message queue */
   public final static String RELATING_UNRELATED = "unrelated";

   /** message queue onOverflow handling, default is blocking until queue takes messages again */
   public final static String ONOVERFLOW_BLOCK = "block";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_DEADLETTER = "deadLetter";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_DISCARDOLDEST = "discardOldest";

   /** Praefix to create a sessionId */
   public final static String SESSIONID_PRAEFIX = "sessionId:";
   public final static String SUBSCRIPTIONID_PRAEFIX = "subscriptionId:";

   public final static String INTERNAL_OID_PRAEFIX = "__sys__";

   /** message queue onOverflow handling */
   public final static String OID_DEAD_LETTER = INTERNAL_OID_PRAEFIX + "deadLetter";

   // action key --- xmlBlaster supported method names used to ckeck access rights, for raw socket messages etc.
   /** The get() method */
   public static final String         GET = "get";
   /** The erase() method */
   public static final String       ERASE = "erase";
   /** The publish() method */
   public static final String     PUBLISH = "publish";
   /** The publishOneway() method */
   public static final String PUBLISH_ONEWAY = "publishOneway";
   /** The subscribe() method */
   public static final String   SUBSCRIBE = "subscribe";
   /** The unSubscribe() method */
   public static final String UNSUBSCRIBE = "unSubscribe";
   /** The update() method */
   public static final String      UPDATE = "update";
   /** The updateOneway() method */
   public static final String UPDATE_ONEWAY = "updateOneway";
   /** The ping() method */
   public static final String        PING = "ping";
   /** The connect() method */
   public static final String     CONNECT = "connect";
   /** The disconnect() method */
   public static final String  DISCONNECT = "disconnect";
   //public static final String   EXCEPTION = "exception";

   /**
    * Checks if given string is a well known method name. 
    * @param method E.g. "publish", this is checked if a known method
    * @return true if method is known
    */
   public static final boolean checkMethodName(String method) {
      if (Constants.GET.equals(method) ||
          Constants.ERASE.equals(method) ||
          Constants.PUBLISH.equals(method) ||
          Constants.PUBLISH_ONEWAY.equals(method) ||
          Constants.SUBSCRIBE.equals(method) ||
          Constants.UNSUBSCRIBE.equals(method) ||
          Constants.UPDATE.equals(method) ||
          Constants.UPDATE_ONEWAY.equals(method) ||
          Constants.PING.equals(method) ||
          Constants.CONNECT.equals(method) ||
          Constants.DISCONNECT.equals(method))
          return true;
      return false;
   }
}

