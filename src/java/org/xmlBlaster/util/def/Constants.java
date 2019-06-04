/*------------------------------------------------------------------------------
Name:      Constants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.def;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Holding some Constants
 * See xmlBlaster/src/c++/util/Constants.h
 * <p>
 * Probably we should change the code to use the
 * <a href="http://developer.java.sun.com/developer/JDCTechTips/2001/tt0807.html">enum pattern</a>
 * for some of the constants in this class
 * </p>
 */
public class Constants {
   public static final String UTF8_ENCODING="UTF-8";

   public static final String DEFAULT_SECURITYPLUGIN_TYPE = "htpasswd";
   public static final String DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

   public final static long MINUTE_IN_MILLIS = 1000L*60;
   public final static long HOUR_IN_MILLIS = MINUTE_IN_MILLIS*60;
   public final static long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
   public final static long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;

   public final static String EMAIL_TRANSFER_ENCODING = "Content-Transfer-Encoding";
   public final static String ENCODING_BASE64 = "base64";
   public final static String ENCODING_FORCE_PLAIN = "forcePlain";
   public final static String ENCODING_QUOTED_PRINTABLE = "quoted-printable";
   public final static String ENCODING_NONE = null;

   public final static String TYPE_STRING = "String"; // is default, same as ""
   public final static String TYPE_BLOB = "byte[]";
   /* See JMS types */
   public final static String TYPE_BOOLEAN = "boolean";
   public final static String TYPE_BYTE = "byte";
   public final static String TYPE_DOUBLE = "double";
   public final static String TYPE_FLOAT = "float";
   public final static String TYPE_INT = "int";
   public final static String TYPE_SHORT = "short";
   public final static String TYPE_LONG = "long";
   /** used to tell that the entry is really null (not just empty) */
   public final static String TYPE_NULL = "null";

   // Used to lookup in global.getObjectEntry(OBJECT_ENTRY_ServerScope)
   public final static String  OBJECT_ENTRY_ServerScope = "ServerNodeScope";

   /**
    * The SOCKET protocol can support zlib compression with streaming compression
    * A partial flush means that all data will be output,
    * but the next packet will continue using compression tables from the end of the previous packet.
    * As described in [RFC-1950] and in [RFC-1951]
    * @see http://www.jcraft.com/jzlib/
    */
   public final static String COMPRESS_ZLIB_STREAM = "zlib:stream";

   /**
    * The SOCKET protocol supports zlib compression for each message individually
    * As described in [RFC-1950] and in [RFC-1951]
    * @see http://www.jcraft.com/jzlib/
    */
   public final static String COMPRESS_ZLIB = "zlib";

   /**
    * The native authentication instance of the xmlBlaster server is available
    * under this key in Global.instance().getProperties().
    * <pre>
    * </pre>
    */
   public final static String I_AUTHENTICATE_PROPERTY_KEY = "/xmlBlaster/I_Authenticate";

   /**
    * Used in the MsgUnitRaw to pass as key for the properties to tell the toXml Methods
    * to inhibit wrapping by a CDATA. This is used to avoid unallowed nested CDATA.
    */
   public final static String INHIBIT_CONTENT_CDATA_WRAPPING = "inhibitContentCDATAWrapping";
   
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
   // UpdateReturnQos uq = new UpdateReturnQos(glob);
   // return uq.toXml(); should be somehow similar to RET_OK?
   public final static String RET_OK = "<qos><state id='" + Constants.STATE_OK + "'/></qos>";
   public final static String INFO_INITIAL = "INITIAL";
   /** ConnectQos transports in its QoS clientProperty an optional flag to suppress cb ping */
   public final static String CLIENTPROPERTY_INITIAL_CALLBACK_PING = "__initialCallbackPing";

   /**
    * If this clientProperty is send with ConnectQos the clientProperties are
    * copied to the SessionInfo.remoteProperty map (which are manipulatable by jconsole and EventPlugin)
    */
   public final static String CLIENTPROPERTY_REMOTEPROPERTIES = "__remoteProperties";

   /* C-client if queued on client side: "<qos><state id='OK' info='QUEUED'/></qos>" */

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
   /** Type of a message queue on client side for updates (not really a type but used for id) */
   public final static String RELATING_CLIENT_UPDATE = "clientUpdate";
   /** Type of a history message queue containing references on messages */
   public final static String RELATING_HISTORY = "history";
   /** Type of a subscription message queue containing subscriptions */
   public final static String RELATING_SUBSCRIBE = "subscribe";
   /** Type of a subscription message queue containing sessions */
   public final static String RELATING_SESSION = "session";
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

   /** ClientProperty of QoS for messages from persistent store */
   public final static String PERSISTENCE_ID = "__persistenceId";

   /** Prefix to create a sessionId */
   public final static String SESSIONID_PREFIX = "sessionId:";
   public final static String SUBSCRIPTIONID_PREFIX = "__subId:";
   public final static String SUBSCRIPTIONID_PtP = SUBSCRIPTIONID_PREFIX+"PtP";
   /** If subscription ID is given by client, e.g. "__subId:client/joe/session/3-34"
     * see Requirement engine.qos.subscribe.id
     */
   //public final static String SUBSCRIPTIONID_CLIENT_PREFIX__XXXXXXX = "__subId:/node/";

   public final static String INTERNAL_LOGINNAME_PREFIX_FOR_PLUGINS = "_";
   public final static String INTERNAL_OID_PREFIX_FOR_PLUGINS = "_";
   public final static String INTERNAL_OID_ADMIN_CMD = "__cmd:";
   public final static String INTERNAL_LOGINNAME_PREFIX_FOR_CORE = "__";
   public final static String INTERNAL_OID_PREFIX_FOR_CORE = "__";
   public final static String INTERNAL_OID_PREFIX = "__sys__";  // Should be replaced by INTERNAL_OID_PREFIX_FOR_CORE in future
   public final static String INTERNAL_OID_CLUSTER_PREFIX = INTERNAL_OID_PREFIX + "cluster";  // "__sys__cluster"
   public final static String INTERNAL_OID_REMOTE_PROPERTIES = INTERNAL_OID_PREFIX + "remoteProperties"; // __sys__remoteProperties
   public final static String INTERNAL_OID_RUNLEVEL_MANAGER = INTERNAL_OID_PREFIX + "RunlevelManager"; // __sys__RunlevelManager

   public final static String EVENT_OID_LOGIN = "__sys__Login";
   public final static String EVENT_OID_LOGOUT = "__sys__Logout";
   public final static String EVENT_OID_USERLIST = "__sys__UserList";
   public final static String EVENT_OID_ERASEDTOPIC = "__sys__ErasedTopic";

   /** JDBC access messages */
   public final static String JDBC_OID = INTERNAL_OID_PREFIX + "jdbc";

   /** message queue onOverflow handling "__sys__deadMessage */
   public final static String OID_DEAD_LETTER = INTERNAL_OID_PREFIX + "deadMessage";

   /** Client sends with ConnectQos its current UTC timestamp string so server knows approximate offset in time as client may not have accurate time set */
   public final static String CLIENTPROPERTY_UTC = INTERNAL_OID_PREFIX_FOR_CORE + "UTC";
   /** Dead messages transport in their QoS clientProperty the original message key in '__key' */
   public final static String CLIENTPROPERTY_DEADMSGKEY = INTERNAL_OID_PREFIX_FOR_CORE + "key";
   /** Dead messages transport in their QoS clientProperty the original message QoS in '__qos' */
   public final static String CLIENTPROPERTY_DEADMSGQOS = INTERNAL_OID_PREFIX_FOR_CORE + "qos";
   /** Dead messages contain the information of the sending session */
   public final static String CLIENTPROPERTY_DEADMSGSENDER = INTERNAL_OID_PREFIX_FOR_CORE + "sender";
   /** Dead messages contain the information of the session for which the delivery failed */
   public final static String CLIENTPROPERTY_DEADMSGRECEIVER = INTERNAL_OID_PREFIX_FOR_CORE + "receiver";
   /** Dead messages transport in their QoS clientProperty the rcvTimestamp in '__rcvTimestamp' */
   public final static String CLIENTPROPERTY_RCVTIMESTAMP = INTERNAL_OID_PREFIX_FOR_CORE + "rcvTimestamp";
   /** Dead messages transport in their QoS clientProperty the original message oid in '__oid' */
   public final static String CLIENTPROPERTY_OID = INTERNAL_OID_PREFIX_FOR_CORE + "oid";
   /** Dead messages transport in their QoS clientProperty the error reason in '__deadMessageReason' */
   public final static String CLIENTPROPERTY_DEADMSGREASON = INTERNAL_OID_PREFIX_FOR_CORE + "deadMessageReason";
   /** The plugin xml markup send to RunlevelManager '__plugin.xml' */
   public final static String CLIENTPROPERTY_PLUGIN_XML = INTERNAL_OID_PREFIX_FOR_CORE + "plugin.xml";
   /** The plugin xml markup send to RunlevelManager '__plugin.jarName' */
   public final static String CLIENTPROPERTY_PLUGIN_JARNAME = INTERNAL_OID_PREFIX_FOR_CORE + "plugin.jarName";

   /** ConnectReturnQos their QoS clientProperty the rcvTimestampStr in '__rcvTimestampStr' */
   public final static String CLIENTPROPERTY_RCVTIMESTAMPSTR = INTERNAL_OID_PREFIX_FOR_CORE + "rcvTimestampStr";

   /** Used in Client Properties to define that the content is encoded with the specified value (default to UTF-8) */
   public final static String CLIENTPROPERTY_CONTENT_CHARSET = "__contentCharset";
   
   /** For xml key attribute, contentMimeExtended="1.0" */
   public static final String DEFAULT_CONTENT_MIME_EXTENDED = "1.0";

   public static final String INDENT = " ";
   public static final String OFFSET = "\n" + INDENT;


   /** XmlKey queryType enum */
   public static final String OID_URL_PREFIX = "oid:";
   public static final String XPATH = "XPATH";
   public static final String XPATH_URL_PREFIX = "xpath:";
   public static final String EXACT = "EXACT";
   public static final String EXACT_URL_PREFIX = "exact:";
   public static final String DOMAIN = "DOMAIN";
   public static final String DOMAIN_URL_PREFIX = "domain:";
   public static final String SUBSCRIPTIONID_URL_PREFIX = "subscriptionId:";
   public static final String REGEX = "REGEX";

   public static final String TOXML_NOSECURITY = "noSecurity";
   public static final String TOXML_EXTRAOFFSET = "extraOffset";
   public static final String TOXML_FORCEREADABLE = "forceReadable";
   public static final String TOXML_FORCEREADABLE_TIMESTAMP = "forceReadableTimestamp";
   public static final String TOXML_FORCEREADABLE_BASE64 = "forceReadableBase64";
   public static final String TOXML_ENCLOSINGTAG = "enclosingTag";
   public static final String TOXML_MAXCONTENTLEN = "maxContentLen";

   public static final String EVENTPLUGIN_PROP_SUMMARY = "_summary";
   public static final String EVENTPLUGIN_PROP_DESCRIPTION = "_description";
   public static final String EVENTPLUGIN_PROP_EVENTTYPE = "_eventType";
   public static final String EVENTPLUGIN_PROP_ERRORCODE = "_errorCode";
   public static final String EVENTPLUGIN_PROP_PUBSESSIONID = "_publicSessionId";
   public static final String EVENTPLUGIN_PROP_SUBJECTID = "_subjectId";
   public static final String EVENTPLUGIN_PROP_ABSOLUTENAME = "_absoluteName";
   public static final String EVENTPLUGIN_PROP_NODEID = "_nodeId";

   /** Stuff used for Streaming */

   /** This is the key of a client property telling the number of
    * this chunk in the sequence. The value itself is a long.
    */
   // public final static String CHUNK_SEQ_NUM = "__CHUNK_SEQ_NUM";
   // public final static String CHUNK_SEQ_NUM = XBConnectionMetaData.JMSX_GROUP_SEQ;

   /** If this exists it is always set to boolean 'true' */
   // public final static String CHUNK_EOF = "__CHUNK_EOF";
   // public final static String CHUNK_EOF = XBConnectionMetaData.JMSX_GROUP_EOF;

   /** If set, an exception occured in this chunk. It contains the
    * exception. It is used to perform clean up in case of exceptions.
    */
   // public final static String CHUNK_EXCEPTION = "__CHUNK_EXCEPTION";
   // public final static String CHUNK_EXCEPTION = XBConnectionMetaData.JMSX_GROUP_EX;

   /** This is the same for all chunks in a message. It shall be a
    * globally unique Identifier. */
   // public final static String STREAM_ID = "__STREAM_ID";
   // public final static String STREAM_ID = XBConnectionMetaData.JMSX_GROUP_ID;

   public final static String JMS_PREFIX = "__jms:";

   public final static String JMS_REPLY_TO = "__jms:JMSReplyTo";

   public final static String UPDATE_BULK_ACK = "__updateBulkAck";

   public final static String CLIENTPROPERTY_ISINITIALUPDATE = "__isInitialUpdate"; // msgs from history queue directly after subscribe

   public final static String CLIENTPROPERTY_MULTISUB_CHECKQOS = "__multiSubCheckQos"; // if set it will also check the qos to determine subscription equality

   /** Mimetypes */
   // see @apache/mime.conf or so

   /**
    * Hypertext Markup Language
    */
   public final static String MIME_HTML = "text/html";

   /**
    * Cascading Style Sheet
    */
   public final static String MIME_CSS = "text/css";

   /**
    * Javascript
    */
   public final static String MIME_JS = "text/javascript";

   /**
    * The mime type for the xml.
    * See http://www.rfc-editor.org/rfc/rfc3023.txt
    */
   public final static String MIME_XML = "text/xml";

   /**
    * Joint Photographic Experts Group
    */
   public final static String MIME_JPG = "image/jpeg";

   /**
    * Portable Network Graphics Format Image
    */
   public final static String MIME_PNG = "image/png";

   /**
    * GIF Image
    */
   public final static String MIME_GIF = "image/gif";

   /**
    * Adds to the key a prefix JMS_PREFIX if and only if the key is one of the JMSX properties
    * defined by the XmlBlaster. It does not add anything if it already starts with JMS_PREFIX.
    *
    * @param key
    * @param log
    * @return
    */
   public static String addJmsPrefix(String key, Logger log) {
      if (key.startsWith(JMS_PREFIX)) {
         log.fine("JMS Property '" + key + "' is already starting with '" + JMS_PREFIX + "'");
         return key;
      }
      try {
         if (org.xmlBlaster.jms.XBConnectionMetaData.getReservedProps().contains(key) || org.xmlBlaster.jms.XBConnectionMetaData.getStandardProps().contains(key))
            key = JMS_PREFIX + key;
      }
      catch (Throwable e) {
    	  System.err.println("JSM failed: " + e.toString());
      }
      return key;
   }

   public static byte[] toUtf8Bytes(String s) {
      if (s == null || s.length() == 0)
              return new byte[0];
      try {
              return s.getBytes(Constants.UTF8_ENCODING);
      } catch (UnsupportedEncodingException e) {
              System.out.println("PANIC in XmlBlaster Constants.toUtf8Bytes(" + s
                              + ", " + Constants.UTF8_ENCODING + "): " + e.toString());
              e.printStackTrace();
              return s.getBytes();
      }
   }
 
   public static String toUtf8String(byte[] b) {
      if (b == null || b.length == 0)
              return "";
      try {
              return new String(b, Constants.UTF8_ENCODING);
      } catch (UnsupportedEncodingException e) {
              System.out.println("PANIC in toUtf8String(" + b + ", "
                              + Constants.UTF8_ENCODING + "): " + e.toString());
              e.printStackTrace();
              return new String(b);
      }
   }
   
   public static String toEncodedString(byte[] b, String encoding) {
      if (b == null)
         return null;
      if (encoding == null || encoding.trim().length() < 1)
         encoding = Constants.UTF8_ENCODING;

      try {
         return new String(b, encoding);
      } catch (UnsupportedEncodingException e) {
         e.printStackTrace();
         System.err.println("PANIC Could not encode according to '" + encoding + "': " + e.getMessage());
         return Constants.toUtf8String(b);
      }
   }

	/**
	 * @param bytes
	 *            28991029248
	 * @param si
	 *            SI units and binary units
	 * @return "27.0 GiB" MiB MB ...
	 */
	public static String humanReadableByteCount(long bytes, boolean si) {
		try {
			int unit = si ? 1000 : 1024;
			if (bytes < unit)
				return bytes + " B";
			int exp = (int) (Math.log(bytes) / Math.log(unit));
			String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1)
					+ (si ? "" : "i");
			return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
		} catch (Throwable e) {
			e.printStackTrace();
			return "" + bytes;
		}
	}


   public final static char[] hextable = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

   public static String byteArrayToHex(byte[] array) {
      String s = "";
      for (int i = 0; i < array.length; ++i) {
	     int di = (array[i] + 256) & 0xFF; // Make it unsigned
	     s = s + hextable[(di >> 4) & 0xF] + hextable[di & 0xF];
     }
     return s;
   }

	/**
	 * @param algorithm
	 *            "MD5"
	 * @throws WatcheeRtException
	 */
   public static String digest(byte[] s, String algorithm) throws XmlBlasterException {
		MessageDigest m = null;
		try {
			m = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_ILLEGALARGUMENT, "digest", "digest failed", e);
		}

		m.update(s);
		// "123456" ->
		// [-31, 10, -36, 57, 73, -70, 89, -85, -66, 86, -32, 87, -14, 15, -120,
		// 62]
		return byteArrayToHex(m.digest());
   }

	/**
	 * md5sum: [ 5 millis ] for 1000 iterations -> 200 iterations/millisecond
	 * 
	 * @param s
	 *            "123456"
	 * @return "e10adc3949ba59abbe56e057f20f883e"
	 */
    public static String md5sum(String s) throws XmlBlasterException {
		try {
			if (s == null)
				s = "";
			return digest(s.getBytes("UTF-8"), "MD5");
		} catch (UnsupportedEncodingException e) {
			throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_ILLEGALARGUMENT, "md5sum", "digest failed", e);
		}
   }

	/**
	 * @param s in case null: uses byte[0] with "d41d8cd98f00b204e9800998ecf8427e"
	 * @return never null
	 */
   public static String md5sum(byte[] s) throws XmlBlasterException {
      if (s == null)
          s = new byte[0];
      return digest(s, "MD5");
   }
}

