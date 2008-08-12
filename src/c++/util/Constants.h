/*-----------------------------------------------------------------------------
Name:      Constants.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding some constants
-----------------------------------------------------------------------------*/

#ifndef _UTIL_CONSTANTS_H
#define _UTIL_CONSTANTS_H

#include <util/xmlBlasterDef.h>
#include <string>



namespace org { namespace xmlBlaster {
  namespace util { namespace Constants {

/**
 * Holding some Constants. 
 * <p />
 * The natural place for those constants would be the xmlBlaster.idl, 
 * this way the constants from java would have been exported automatically
 * to C++ as well. But we want to be independent from CORBA.
 * @see xmlBlaster/src/java/org/xmlBlaster/util/def/Constants.java
 */
   //class Dll_Export Constants {

   //public:

   extern Dll_Export  const char * DEFAULT_SECURITYPLUGIN_TYPE;
   extern Dll_Export  const char * DEFAULT_SECURITYPLUGIN_VERSION;

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
    *  #                          Marcel Ruff <xmlBlaster@marcelruff.info> February 2002
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
   extern Dll_Export long XMLBLASTER_OID_ROOT[];

//   enum MessagePriority {
      /**
       * The minimum priority of a message (0).
       */
//      MIN_PRIORITY = 0,

      /**
       * The lower priority of a message (2).
       */
//      LOW_PRIORITY = 3,

      /**
       * The default priority of a message (5).
       */
//      NORM_PRIORITY = 5,

      /**
       * The higher priority of a message (7).
       */
//      HIGH_PRIORITY = 7,

      /**
       * The maximum priority of a message (9).
       */
//      MAX_PRIORITY = 9
//   };


   /**
    * Parses given std::string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @param defaultPriority Value to use if not parseable
    * @return The int value for the message priority
    */
   
   /*const int getPriority(std::string prio, int defaultPriority);*/

   // Status id, on error usually an exception is thrown so we don't need "ERROR":

   /** The returned message status if OK */
   extern Dll_Export const char * STATE_OK;
   extern Dll_Export const char * RET_OK;

   /** The returned message status if message timeout occured (but not erased) */
   extern Dll_Export const char * STATE_TIMEOUT;
   /** The returned message status if message is explicitly erased by a call to erase() */
   extern Dll_Export const char * STATE_ERASED;
   /** The returned message status if message couldn't be forwarded to the master cluster node */
   extern Dll_Export const char * STATE_FORWARD_ERROR;

   /** Additional info for state. 
       The returned message status if message couldn't be forwarded to the master cluster node but
       is in the tail back queue to be delivered on reconnect or on client side message
       recording.
   */
   extern Dll_Export const char * INFO_QUEUED;

   // See org.xmlBlaster.engine.queuemsg.ServerEntryFactory.java ENTRY_TYPE_MSG_RAW
   // for persistency serialization
   extern Dll_Export const std::string ENTRY_TYPE_MSG_RAW; // "MSG_RAW"; msgUnit is dumped as specified in the protocol.socket requirement (see C persistent queue)


   /** Type of a message queue */
   extern Dll_Export const char * RELATING_CALLBACK;
   /** Type of a message queue */
   extern Dll_Export const char * RELATING_SUBJECT;
   /** Type of a message queue */
   extern Dll_Export const char * RELATING_UNRELATED;
   /** Type of a message queue  on client side */
   extern Dll_Export const std::string RELATING_CLIENT;
   /** Type of a history message queue containing references on messages */
   extern Dll_Export const char * RELATING_HISTORY;
   /** Message cache */
   extern Dll_Export const char * RELATING_MSGUNITSTORE;
   /** Persistency for topics */
   extern Dll_Export const char * RELATING_TOPICSTORE;

   /** message queue onOverflow handling, default is blocking until queue takes messages again */
   extern Dll_Export const char * ONOVERFLOW_BLOCK;
   /** message queue onOverflow handling */
   extern Dll_Export const char * ONOVERFLOW_DEADLETTER;
   /** message queue onOverflow handling */
   extern Dll_Export const char * ONOVERFLOW_DISCARD;
   /** message queue onOverflow handling */
   extern Dll_Export const char * ONOVERFLOW_DISCARDOLDEST;
   /** message queue onOverflow handling */
   extern Dll_Export const char * ONOVERFLOW_EXCEPTION;
   /** message queue onOverflow handling */
   extern Dll_Export const char * ONOVERFLOW_DEADMESSAGE;


   /** If callback fails more often than is configured the login session is destroyed */
   extern Dll_Export const char * ONEXHAUST_KILL_SESSION;

   /** JDBC access messages */
   extern Dll_Export const char * JDBC_OID;

   /** message queue onOverflow handling */
   extern Dll_Export const char * OID_DEAD_LETTER;

   /** XmlKey queryType enum */
   extern Dll_Export const char * XPATH;
   extern Dll_Export const char * EXACT;
//   extern Dll_Export const char * DOMAIN; // doesn't compile with g++ 3.1.1
   extern Dll_Export const char * D_O_M_A_I_N;
   extern Dll_Export const char * REGEX;
   
   extern Dll_Export const char * XPATH_URL_PREFIX;
   extern Dll_Export const char * EXACT_URL_PREFIX;
   extern Dll_Export const char * DOMAIN_URL_PREFIX;

   extern Dll_Export const char * SOCKET;
   extern Dll_Export const char * IOR;
   extern Dll_Export const char * EMAIL;
   extern Dll_Export const char * XML_RPC;

   extern Dll_Export const char * RAM;
   extern Dll_Export const char * CACHE;
   extern Dll_Export const char * ODBC;
   extern Dll_Export const char * SQLITE;

   extern Dll_Export const char * OFFSET;
   extern Dll_Export const char * INDENT;

   extern Dll_Export const char * ENCODING_BASE64;
   extern Dll_Export const char * ENCODING_NONE;

   extern Dll_Export const char * TYPE_STRING; //< is default, "String"
   extern Dll_Export const char * TYPE_BLOB;
   extern Dll_Export const char * TYPE_BOOLEAN;
   extern Dll_Export const char * TYPE_BYTE;
   extern Dll_Export const char * TYPE_DOUBLE;
   extern Dll_Export const char * TYPE_FLOAT;
   extern Dll_Export const char * TYPE_INT;
   extern Dll_Export const char * TYPE_SHORT;
   extern Dll_Export const char * TYPE_LONG;



   extern Dll_Export const org::xmlBlaster::util::Timestamp THOUSAND;
   extern Dll_Export const org::xmlBlaster::util::Timestamp MILLION;
   extern Dll_Export const org::xmlBlaster::util::Timestamp BILLION;

   /** Prefix to create a sessionId */
   extern Dll_Export const char* SESSIONID_PREFIX;
   extern Dll_Export const char* SUBSCRIPTIONID_PREFIX;
   extern Dll_Export const char* const SUBSCRIPTIONID_PtP;

   /** If subscription ID is given by client, e.g. "__subId:/node/heron/client/joe/3/34"
     * see Requirement engine.qos.subscribe.id
     */
   extern Dll_Export const char* SUBSCRIPTIONID_CLIENT_PREFIX;
   extern Dll_Export const char* INTERNAL_OID_PREFIX_FOR_PLUGINS;
   extern Dll_Export const char* INTERNAL_OID_ADMIN_CMD;
   extern Dll_Export const char* INTERNAL_OID_PREFIX_FOR_CORE;
   extern Dll_Export const char* INTERNAL_OID_PREFIX;
   extern Dll_Export const char* INTERNAL_OID_CLUSTER_PREFIX;

   /**
    * Object keys for lifetime manager, needed for registration
    */
   extern Dll_Export const char* XB_GLOBAL_KEY;
   extern Dll_Export const char* XB_XMLPARSERFACTORY_KEY;
   extern Dll_Export const char* JMS_PREFIX;

   /** Request/Reply pattern */
   extern Dll_Export const char * JMS_REPLY_TO;
   
   extern Dll_Export const char* UPDATE_BULK_ACK;

   extern Dll_Export const char* JMSX_GROUP_ID;
   extern Dll_Export const char* JMSX_GROUP_SEQ;
   extern Dll_Export const char* JMSX_GROUP_EOF;
   extern Dll_Export const char* JMSX_GROUP_EX;

   extern Dll_Export const char* FILENAME_ATTR;
	extern Dll_Export const char* TIMESTAMP_ATTR;
	extern Dll_Export const char* FILE_DATE;
	
//};


}}}} // namespace 

#endif


