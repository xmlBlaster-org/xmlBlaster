/*------------------------------------------------------------------------------
Name:      Constants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.enum;


/**
 * Holding some Constants
 * See xmlBlaster/src/c++/util/Constants.h
 * <p>
 * Probably we should change the code to use the 
 * <a href="http://developer.java.sun.com/developer/JDCTechTips/2001/tt0807.html">enum pattern</a>
 * for some of the constants in this class
 * </p>
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
    * The native authentication instance of the xmlBlaster server is available
    * under this key in Global.instance().getProperties(). 
    * <pre>
    * </pre>
    */
   public final static String I_AUTHENTICATE_PROPERTY_KEY = "/xmlBlaster/I_Authenticate";


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
   public static final int XMLBLASTER_PORT = 3412;

   /**
    * The xmlBlaster SNMP node 11662 registered at IANA. 
    * <p />
    * XmlBlaster has a registered SNMP number under iso.org.dod.internet.private.enterprise (1.3.6.1.4.1),
    * our tree leaf is 1.3.6.1.4.1.11662<br />
    * Returns 11662
    * @see <a href="http://www.iana.org/assignments/enterprise-numbers" target="others">PRIVATE ENTERPRISE NUMBERS</a>
    */
   public static final int XMLBLASTER_SNMP = 11662;

   /**
    * The xmlBlaster SNMP node 1.3.6.1.4.1.11662 as registered at IANA. 
    * <br />
    * Returns a long array containing the SNMP hierarchy to xmlBlaster
    */
   public static final long[] XMLBLASTER_OID_ROOT = { 1, 3, 6, 1, 4, 1, Constants.XMLBLASTER_SNMP }; // 11662

   // Status id, on error usually an exception is thrown so we don't need "ERROR":

   /** The returned message status if OK */
   public final static String STATE_OK = "OK";
   public final static String RET_OK = "<qos><state id='" + Constants.STATE_OK + "'/></qos>";
   
   public final static String STATE_WARN = "WARNING";
   public final static String RET_WARN = "<qos><state id='" + Constants.STATE_WARN + "'/></qos>";

   /** The returned message status if message is stale (that is old but not erased yet) */
   //public final static String STATE_STALE = "STALE"; // needs to be implemented as another message timer TODO!!!
   //public final static String RET_STALE = "<qos><state id='" + Constants.STATE_STALE + "'/></qos>";
   
   /** The returned message status if message timeout occurred (but not erased) */
   public final static String STATE_TIMEOUT = "TIMEOUT";
   public final static String RET_TIMEOUT = "<qos><state id='" + Constants.STATE_TIMEOUT + "'/></qos>";
   
   /** The returned message status if message is expired (timeout occurred and is erased) */
   public final static String STATE_EXPIRED = "EXPIRED";
   public final static String RET_EXPIRED = "<qos><state id='" + Constants.STATE_EXPIRED + "'/></qos>";
   
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

   /** Type of a message callback queue */
   public final static String RELATING_CALLBACK = "callback";
   /** Type of a message callback queue */
   public final static String RELATING_SUBJECT = "subject";
   /** Type of a message queue  on client side */
   public final static String RELATING_CLIENT = "connection";
   /** Type of a history message queue containing references on messages */
   public final static String RELATING_HISTORY = "history";
   /** MessageUnit cache */
   public final static String RELATING_MSGUNITSTORE = "msgUnitStore";
   /** Topics persistence */
   public final static String RELATING_TOPICSTORE = "topicStore";

   /* message queue onOverflow handling, blocking until queue takes messages again (client side) */
   public final static String ONOVERFLOW_BLOCK = "block";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_DEADMESSAGE = "deadMessage";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_DISCARD = "discard";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_DISCARDOLDEST = "discardOldest";
   /** message queue onOverflow handling */
   public final static String ONOVERFLOW_EXCEPTION = "exception";

   /** If callback fails more often than is configured the login session is destroyed */
   public final static String ONEXHAUST_KILL_SESSION = "killSession";


   /** Prefix to create a sessionId */
   public final static String SESSIONID_PREFIX = "sessionId:";
   public final static String SUBSCRIPTIONID_PREFIX = "__subId:";
   public final static String SUBSCRIPTIONID_PtP = SUBSCRIPTIONID_PREFIX+"PtP";
   /** If subscription ID is given by client, e.g. "__subId:/node/heron/client/joe/3/34"
     * see Requirement engine.qos.subscribe.id
     */
   public final static String SUBSCRIPTIONID_CLIENT_PREFIX = "__subId:/node/";

   public final static String INTERNAL_OID_PREFIX_FOR_PLUGINS = "_";
   public final static String INTERNAL_OID_ADMIN_CMD = "__cmd:";
   public final static String INTERNAL_OID_PREFIX_FOR_CORE = "__";
   public final static String INTERNAL_OID_PREFIX = "__sys__";  // Should be replaced by INTERNAL_OID_PREFIX_FOR_CORE in future
   public final static String INTERNAL_OID_CLUSTER_PREFIX = INTERNAL_OID_PREFIX +"cluster";  // "__sys__cluster"

   /** JDBC access messages */
   public final static String JDBC_OID = INTERNAL_OID_PREFIX + "jdbc";

   /** message queue onOverflow handling */
   public final static String OID_DEAD_LETTER = INTERNAL_OID_PREFIX + "deadMessage";

   /** For xml key attribute, contentMimeExtended="1.0" */
   public static final String DEFAULT_CONTENT_MIME_EXTENDED = "1.0";

   public static final String INDENT = " ";
   public static final String OFFSET = "\n" + INDENT;


   /** XmlKey queryType enum */
   public static final String XPATH = "XPATH";
   public static final String EXACT = "EXACT";
   public static final String DOMAIN = "DOMAIN";
   public static final String REGEX = "REGEX";
}

