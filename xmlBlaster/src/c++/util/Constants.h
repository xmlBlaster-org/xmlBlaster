/*-----------------------------------------------------------------------------
Name:      Constants.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding some constants
-----------------------------------------------------------------------------*/

#ifndef _UTIL_CONSTANTS_H
#define _UTIL_CONSTANTS_H


using namespace std;

namespace org { namespace xmlBlaster {
namespace util {

/**
 * Holding some Constants. 
 * <p />
 * The natural place for those constants would be the xmlBlaster.idl, 
 * this way the constants from java would have been exported automatically
 * to C++ as well. But we want to be independent from CORBA.
 * @see xmlBlaster/src/java/org/xmlBlaster/engine/helper/Constants.java
 */
class Constants {

   public:

   static const char * const DEFAULT_SECURITYPLUGIN_TYPE;
   static const char * const DEFAULT_SECURITYPLUGIN_VERSION;

   enum {
      MINUTE_IN_MILLIS = 1000L*60,
      HOUR_IN_MILLIS = MINUTE_IN_MILLIS*60,
      DAY_IN_MILLIS = HOUR_IN_MILLIS * 24,
      WEEK_IN_MILLIS = DAY_IN_MILLIS * 7
   };


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
   enum {
      XMLBLASTER_PORT = 3412
   };

   /**
    * The xmlBlaster SNMP node 11662 registered at IANA. 
    * <p />
    * XmlBlaster has a registered SNMP number under iso.org.dod.internet.private.enterprise (1.3.6.1.4.1),
    * our tree leaf is 1.3.6.1.4.1.11662
    * @return 11662
    * @see <a href="http://www.iana.org/assignments/enterprise-numbers" target="others">PRIVATE ENTERPRISE NUMBERS</a>
    */
   enum {
      XMLBLASTER_SNMP = 11662
   };

   /**
    * The xmlBlaster SNMP node 1.3.6.1.4.1.11662 as registered at IANA. 
    * @return a long array containing the SNMP hierarchy to xmlBlaster
    */
   static const long XMLBLASTER_OID_ROOT[];

   enum MessagePriority {
      /**
       * The minimum priority of a message (0).
       */
      MIN_PRIORITY = 0,

      /**
       * The lower priority of a message (2).
       */
      LOW_PRIORITY = 3,

      /**
       * The default priority of a message (5).
       */
      NORM_PRIORITY = 5,

      /**
       * The higher priority of a message (7).
       */
      HIGH_PRIORITY = 7,

      /**
       * The maximum priority of a message (9).
       */
      MAX_PRIORITY = 9
   };


   /**
    * Parses given string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @param defaultPriority Value to use if not parseable
    * @return The int value for the message priority
    */
   /*
   static const int getPriority(string prio, int defaultPriority) const {
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
   */

   // Status id, on error usually an exception is thrown so we don't need "ERROR":

   /** The returned message status if OK */
   static const char * const STATE_OK;
   static const char * const RET_OK;

   /** The returned message status if message timeout occured (but not erased) */
   static const char * const STATE_TIMEOUT;
   /** The returned message status if message is explicitly erased by a call to erase() */
   static const char * const STATE_ERASED;
   /** The returned message status if message couldn't be forwarded to the master cluster node */
   static const char * const STATE_FORWARD_ERROR;

   /** Additional info for state. 
       The returned message status if message couldn't be forwarded to the master cluster node but
       is in the tail back queue to be delivered on reconnect or on client side message
       recording.
   */
   static const char * const INFO_QUEUED;

   /** Type of a message queue */
   static const char * const RELATING_SESSION;
   /** Type of a message queue */
   static const char * const RELATING_SUBJECT;
   /** Type of a message queue */
   static const char * const RELATING_UNRELATED;

   /** message queue onOverflow handling, default is blocking until queue takes messages again */
   static const char * const ONOVERFLOW_BLOCK;
   /** message queue onOverflow handling */
   static const char * const ONOVERFLOW_DEADLETTER;
   /** message queue onOverflow handling */
   static const char * const ONOVERFLOW_DISCARD;
   /** message queue onOverflow handling */
   static const char * const ONOVERFLOW_DISCARDOLDEST;
   /** message queue onOverflow handling */
   static const char * const ONOVERFLOW_EXCEPTION;

   /** If callback fails more often than is configured the login session is destroyed */
   static const char * const ONEXHAUST_KILL_SESSION;


   /** Praefix to create a sessionId */
   static const char * const SESSIONID_PRAEFIX;
   static const char * const SUBSCRIPTIONID_PRAEFIX;

   static const char * const INTERNAL_OID_PRAEFIX;
   static const char * const INTERNAL_OID_CLUSTER_PRAEFIX;

   /** JDBC access messages */
   static const char * const JDBC_OID;

   /** message queue onOverflow handling */
   static const char * const OID_DEAD_LETTER;

   /** XmlKey queryType enum */
   static const char * const XPATH;
   static const char * const EXACT;
   //static const char * const DOMAIN; // doesn't compile with g++ 3.1.1
   static const char * const REGEX;

};

}}}; // namespace 

#endif


