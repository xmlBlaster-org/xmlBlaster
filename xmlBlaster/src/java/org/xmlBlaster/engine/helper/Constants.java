/*------------------------------------------------------------------------------
Name:      Constants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Global;


/**
 * Holding some Constants
 * See xmlBlaster/src/c++/util/Constants.h
 */
public class Constants
{
   private static final String ME = "Constants";

   public static final String DEFAULT_SECURITYPLUGIN_TYPE = "htpasswd";
   public static final String DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

   public final static long MINUTE_IN_MILLIS = 1000L*60;
   public final static long HOUR_IN_MILLIS = MINUTE_IN_MILLIS*60;
   public final static long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
   public final static long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;


   /**
    * The IANA registered xmlBlaster port,
    * see <a href="http://www.iana.org/assignments/port-numbers">IANA Port Numbers</a>
    * and <a href="http://andrew.triumf.ca/cgi-bin/port">Network Service Query</a>.
    * <pre>
    *  xmlblaster      3412/tcp   xmlBlaster
    *  xmlblaster      3412/udp   xmlBlaster
    *  #                          Marcel Ruff <ruff@swand.lake.de> February 2002
    * </pre>
    */
   public static final int XMLBLASTER_PORT = 3412;

   /**
    * The xmlBlaster SNMP node 11662 registered at IANA. 
    * <p />
    * XmlBlaster has a registered SNMP number under iso.org.dod.internet.private.enterprise (1.3.6.1.4.1),
    * our tree leaf is 1.3.6.1.4.1.11662
    * @return 11662
    * @see <a href="http://www.iana.org/assignments/enterprise-numbers" target="others">PRIVATE ENTERPRISE NUMBERS</a>
    */
   public static final int XMLBLASTER_SNMP = 11662;

   /**
    * The xmlBlaster SNMP node 1.3.6.1.4.1.11662 as registered at IANA. 
    * @return a long array containing the SNMP hierarchy to xmlBlaster
    */
   public static final long[] XMLBLASTER_OID_ROOT = { 1, 3, 6, 1, 4, 1, Constants.XMLBLASTER_SNMP }; // 11662

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
    * The higher priority of a message (7).
    */
   public final static int HIGH_PRIORITY = 7;

   /**
    * The maximum priority of a message (9).
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
               Global.instance().getLog("core").warn(ME, "Wrong format of <priority>" + prio +
                    "</priority>, expected a number between (inclusiv) 0 - 9, setting to message priority to "
                    + defaultPriority);
         }
      }
      if (defaultPriority < Constants.MIN_PRIORITY || defaultPriority > Constants.MAX_PRIORITY) {
          Global.instance().getLog("core").warn(ME, "Wrong message defaultPriority=" + defaultPriority + " given, setting to NORM_PRIORITY");
          return Constants.NORM_PRIORITY;
      }
      return defaultPriority;
   }


   // Status id, on error usually an exception is thrown so we don't need "ERROR":

   /** The returned message status if OK */
   public final static String STATE_OK = "OK";
   public final static String RET_OK = "<qos><state id='" + Constants.STATE_OK + "'/></qos>";
   
   /** The returned message status if message is stale (that is old but not erased yet) */
   //public final static String STATE_STALE = "STALE"; // needs to be implemented as another message timer TODO!!!
   //public final static String RET_STALE = "<qos><state id='" + Constants.STATE_STALE + "'/></qos>";
   
   /** The returned message status if message timeout occured (but not erased) */
   public final static String STATE_TIMEOUT = "TIMEOUT";
   public final static String RET_TIMEOUT = "<qos><state id='" + Constants.STATE_TIMEOUT + "'/></qos>";
   
   /** The returned message status if message is explicitly erased by a call to erase() */
   public final static String STATE_ERASED = "ERASED";
   public final static String RET_ERASED = "<qos><state id='" + Constants.STATE_ERASED + "'/></qos>";

   /** The returned message status if message couldn't be forwarded to the master cluster node */
   public final static String STATE_FORWARD_ERROR = "FORWARD_ERROR";
   public final static String RET_FORWARD_ERROR = "<qos><state id='" + Constants.STATE_FORWARD_ERROR + "'/></qos>";

   /** Additional info for state. 
       The returned message status if message couldn't be forwarded to the master cluster node but
       is in the tail back queue to be delivered on reconnect or on client side message
       recording.
   */
   public final static String INFO_QUEUED = "QUEUED";

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
   public final static String ONOVERFLOW_DISCARD = "discard";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_DISCARDOLDEST = "discardOldest";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_EXCEPTION = "exception";

   /** If callback fails more often than is configured the login session is destroyed */
   public final static String ONEXHAUST_KILL_SESSION = "killSession";


   /** Praefix to create a sessionId */
   public final static String SESSIONID_PRAEFIX = "sessionId:";
   public final static String SUBSCRIPTIONID_PRAEFIX = "__subId:";
   /** If subscription ID is given by client, e.g. "__subId:/node/heron/client/joe/3/34"
     * see Requirement engine.qos.subscribe.id
     */
   public final static String SUBSCRIPTIONID_CLIENT_PRAEFIX = "__subId:/node/";

   public final static String INTERNAL_OID_PRAEFIX = "__sys__";
   public final static String INTERNAL_OID_CLUSTER_PRAEFIX = INTERNAL_OID_PRAEFIX +"cluster";  // "__sys__cluster"

   /** JDBC access messages */
   public final static String JDBC_OID = INTERNAL_OID_PRAEFIX + "jdbc";

   /** message queue onOverflow handling */
   public final static String OID_DEAD_LETTER = INTERNAL_OID_PRAEFIX + "deadLetter";

   // action key --- xmlBlaster supported method names used to ckeck access rights, for raw socket messages etc.
   /** The get() method */
   public static final String         GET = "get";
   /** The erase() method */
   public static final String       ERASE = "erase";
   /** The publish() method */
   public static final String     PUBLISH = "publish";
   /** The publishArr() method */
   public static final String     PUBLISH_ARR = "publishArr";
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
          Constants.PUBLISH_ARR.equals(method) ||
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

   /** For xml key attribute, contentMimeExtended="1.0" */
   public static final String DEFAULT_CONTENT_MIME_EXTENDED = "1.0";

   /** XmlKey queryType enum */
   public static final String XPATH = "XPATH";
   public static final String EXACT = "EXACT";
   public static final String DOMAIN = "DOMAIN";
   public static final String REGEX = "REGEX";
}

