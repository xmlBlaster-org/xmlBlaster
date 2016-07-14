/*------------------------------------------------------------------------------
Name:      ErrorCode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.def;

import java.util.Iterator;
import java.util.TreeMap;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.ReplaceVariable;

/**
 * This class holds an enumeration error codes. 
 * <p>
 * If you need new error code add it here following the same schema.
 * </p>
 * <p>
 * The documentation is created by examining this class with it links.
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.errorcodes.html">The admin.errorcodes requirement</a>
 */
public final class ErrorCode implements java.io.Serializable
{
   private static final long serialVersionUID = 6926365721931493917L;
   private final static TreeMap errorCodeMap = new TreeMap(); // The key is the 'errorCode' String and the value is an 'ErrorCode' instance
   private final String errorCode;
   private final String description;
   private final ResourceInfo[] resourceInfos;

   ////////// BEGIN /////////// Add the error code instances here ////////////////////
   public static final ErrorCode LEGACY = new ErrorCode("legacy",
         "This error code marks all old style XmlBlasterExceptions until they are ported to the new behaviour.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "admin.errorcodes", "admin.errorcodes")
         }
      );

   public static final ErrorCode INTERNAL = new ErrorCode("internal",
         "These category is an internal exception, usually a Java runtime exception, please post it to the mailing list.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "admin.errorcodes", "admin.errorcodes")
         }
      );

   public static final ErrorCode INTERNAL_UNKNOWN = new ErrorCode("internal.unknown",
         "This is an unknown and unexpected error, usually a Java runtime exception, please post it to the mailing list.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "admin.errorcodes", "admin.errorcodes")
         }
      );

   public static final ErrorCode INTERNAL_NULLPOINTER = new ErrorCode("internal.nullpointer",
         "A null pointer is an xmlBlaster internal programming error, please post it to the mailing list.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode INTERNAL_ILLEGALARGUMENT = new ErrorCode("internal.illegalArgument",
         "An illegal argument is an xmlBlaster internal programming error, please post it to the mailing list.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode INTERNAL_INTERRUPTED = new ErrorCode("internal.interrupted",
         "An unexpected InterruptedException for a thread occurred, please post it to the mailing list.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode INTERNAL_NOTIMPLEMENTED = new ErrorCode("internal.notImplemented",
         "The feature is not implemented yet.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode INTERNAL_CONNECTIONFAILURE = new ErrorCode("internal.connectionFailure",
         "An internal error occurred, we were not able to access the server handle.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect"),
         }
      );

   public static final ErrorCode INTERNAL_ILLEGALSTATE = new ErrorCode("internal.illegalState",
         "The state of an object is not allowed.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode INTERNAL_DISCONNECT = new ErrorCode("internal.disconnect",
         "An internal error occurred when processing a disconnect() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.disconnect", "interface.disconnect")
         }
      );

   public static final ErrorCode INTERNAL_SUBSCRIBE = new ErrorCode("internal.subscribe",
         "An internal error occurred when processing a subscribe() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.subscribe", "interface.subscribe")
         }
      );

   public static final ErrorCode INTERNAL_UNSUBSCRIBE = new ErrorCode("internal.unSubscribe",
         "An internal error occurred when processing an unSubscribe() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.unSubscribe", "interface.unSubscribe")
         }
      );

   public static final ErrorCode INTERNAL_PUBLISH = new ErrorCode("internal.publish",
         "An internal error occurred when processing a publish() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.publish", "interface.publish"),
         }
      );

   public static final ErrorCode INTERNAL_PUBLISH_ARR = new ErrorCode("internal.publishArr",
         "An internal error occurred when processing a publishArr() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.publish", "interface.publish"),
         }
      );

   public static final ErrorCode INTERNAL_ERASE = new ErrorCode("internal.erase",
         "An internal error occurred when processing a erase() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.erase", "interface.erase"),
         }
      );

   public static final ErrorCode INTERNAL_GET = new ErrorCode("internal.get",
         "An internal error occurred when processing a get() request.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.get", "interface.get"),
         }
      );

   public static final ErrorCode INTERNAL_STOP = new ErrorCode("internal.stop",
         "An internal control exception, for example to stop parsing of XML.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE = new ErrorCode("resource",
         "This category is for resource problems like too low memory. It can usually be fixed by the administrator",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_MAINTENANCE = new ErrorCode("resource.maintenance",
	         "The resource is under maintenance, please try again later",
	         new ResourceInfo[] {
	         }
	      );

   public static final ErrorCode RESOURCE_OUTOFMEMORY = new ErrorCode("resource.outOfMemory",
         "The JVM has no more RAM memory, try increasing it like 'java -Xms18M -Xmx256M org.xmlBlaster.Main'",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.URL, "Increasing JVM heap", "http://java.sun.com/docs/hotspot/ism.html")
         }
      );

   public static final ErrorCode RESOURCE_TOO_MANY_THREADS = new ErrorCode("resource.tooManyThreads",
         "The number of threads used is exceeded, try increasing the number of threads in the properties",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "Increasing the number of threads", "queue.jdbc.commontable")
         }
      );

   public static final ErrorCode RESOURCE_EXHAUST = new ErrorCode("resource.exhaust",
         "A resource of your system exhausted",
         new ResourceInfo[] {
         }
      );

   /*
   public static final ErrorCode RESOURCE_NOMORESPACE = new ErrorCode("resource.noMoreSpace",
         "The harddisk is full",
         new ResourceInfo[] {
         }
      );
   */

   public static final ErrorCode RESOURCE_CALLBACKSERVER_CREATION = new ErrorCode("resource.callbackServer.creation",
         "The callback server can't be created",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_OVERFLOW_QUEUE_BYTES = new ErrorCode("resource.overflow.queue.bytes",
         "The maximum size in bytes of a queue is exhausted",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "queue", "queue"),
            new ResourceInfo(ResourceInfo.REQ, "engine.queue", "engine.queue"),
            new ResourceInfo(ResourceInfo.API, "queue configuration", "org.xmlBlaster.util.qos.storage.QueuePropertyBase"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.util.qos.storage.ClientQueueProperty"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.util.qos.storage.CbQueueProperty")
         }
      );

   public static final ErrorCode RESOURCE_OVERFLOW_QUEUE_ENTRIES = new ErrorCode("resource.overflow.queue.entries",
         "The maximum number of entries of a queue is exhausted",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "queue", "queue"),
            new ResourceInfo(ResourceInfo.REQ, "engine.queue", "engine.queue"),
            new ResourceInfo(ResourceInfo.API, "queue configuration", "org.xmlBlaster.util.qos.storage.QueuePropertyBase"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.util.qos.storage.ClientQueueProperty"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.util.qos.storage.CbQueueProperty")
         }
      );

   public static final ErrorCode RESOURCE_UNAVAILABLE = new ErrorCode("resource.unavailable",
         "The resource is not available (e.g. it is shutdown)",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_TEMPORARY_UNAVAILABLE = new ErrorCode("resource.temporary.unavailable",
         "The server has a temporary resource timeout, please try again.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_DB_UNAVAILABLE = new ErrorCode("resource.db.unavailable",
         "There is no connection to a backend database using JDBC",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "queue.jdbc.hsqldb", "queue.jdbc.hsqldb"),
            new ResourceInfo(ResourceInfo.API, "JDBC connection management", "org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool")
         }
      );

   public static final ErrorCode RESOURCE_DB_BLOCK = new ErrorCode("resource.db.block",
         "A timeout occurred while accessing the database, typically caused by concurrent multithreaded access", // Used in C queue (SQLite)
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_DB_UNKNOWN = new ErrorCode("resource.db.unknown",
         "An unknown error with the backend database using JDBC occurred",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "queue.jdbc.hsqldb", "queue.jdbc.hsqldb"),
            new ResourceInfo(ResourceInfo.API, "JDBC connection management", "org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool")
         }
      );

   public static final ErrorCode RESOURCE_ADMIN_UNAVAILABLE = new ErrorCode("resource.admin.unavailable",
         "The administrative support is switched off for this xmlBlaster instance",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "admin.messages", "admin.messages")
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION = new ErrorCode("resource.configuration",
         "Please check your configuration.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "client.configuration", "client.configuration"),
            new ResourceInfo(ResourceInfo.REQ, "The CORBA protocol plugin", "protocol.corba.JacORB")
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_CONNECT = new ErrorCode("resource.configuration.connect",
         "Please check your connection configuration settings or the availability of a remote server.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "client.configuration", "client.configuration"),
            new ResourceInfo(ResourceInfo.REQ, "The EMAIL protocol plugin", "protocol.email")
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_PLUGINFAILED = new ErrorCode("resource.configuration.pluginFailed",
         "A plugin required couldn't be loaded, please check your configuration.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "The runlevel manager howto", "engine.runlevel.howto"),
            new ResourceInfo(ResourceInfo.REQ, "The runlevel manager", "engine.runlevel")
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_XML = new ErrorCode("resource.configuration.xml",
         "Your XML / XSL configuration needs to be adjusted, please check your configuration.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.URL, "Changing XML/XSL implementation", "http://www.xmlblaster.org/FAQ.html#xml-parser")
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_SSLSOCKET = new ErrorCode("resource.configuration.sslSocket",
         "A SOCKET plugin required couldn't be loaded, please check your (ssl) configuration.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "The runlevel manager howto", "engine.runlevel.howto"),
            new ResourceInfo(ResourceInfo.REQ, "The SOCKET protocol specification", "protocol.socket")
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_ADDRESS = new ErrorCode("resource.configuration.address",
         "A remote address you passed is invalid, please check your configuration.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.util.qos.address.AddressBase")
         }
      );

   public static final ErrorCode RESOURCE_FILEIO = new ErrorCode("resource.fileIO",
         "A file access failed.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_FILEIO_FILELOST = new ErrorCode("resource.fileIO.fileLost",
         "A file disappeared, access failed.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_CLUSTER_NOTAVAILABLE = new ErrorCode("resource.cluster.notAvailable",
         "A remote cluster node is not reachable.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "cluster requirement", "cluster")
         }
      );

   public static final ErrorCode RESOURCE_CLUSTER_CIRCULARLOOP = new ErrorCode("resource.cluster.circularLoop",
         "A message loops between cluster nodes and can't reach its destination, please check the destination cluster node name.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "cluster PtP requirement", "cluster.PtP"),
            new ResourceInfo(ResourceInfo.REQ, "cluster requirement", "cluster")
         }
      );

   public static final ErrorCode COMMUNICATION = new ErrorCode("communication",
         "This category is related to communication problems between client and server.",
         new ResourceInfo[] {
         }
      );

   // Is thrown by server but we want the client to behave like a communication problem (re-connect)
   public static final ErrorCode COMMUNICATION_RESOURCE_TEMPORARY_UNAVAILABLE = new ErrorCode("communication.resource.temporary.unavailable",
         "The server has a temporary resource timeout, please try again.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION = new ErrorCode("communication.noConnection",
         "A specific remote connection throws an exception on invocation.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode COMMUNICATION_TIMEOUT = new ErrorCode("communication.timeout",
         "The socket call blocked until a timeout occurred.",
         new ResourceInfo[] {
         }
      );
   
   public static final ErrorCode COMMUNICATION_RESPONSETIMEOUT = new ErrorCode("communication.responseTimeout",
        "A method call blocked when waiting on the ACK/NAK return message.",
         new ResourceInfo[] {
         }
      );


   public static final ErrorCode COMMUNICATION_FORCEASYNC = new ErrorCode("communication.forceAsync",
         "Thrown if a ping is called but we can't afford to block until it succeeds.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION_SERVERDENY = new ErrorCode("communication.noConnection.serverDeny",
         "Thrown by the server if no connection is accepted, usually on startup when the server is not ready for it (standby mode).",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "run level requirement", "engine.runlevel")
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE = new ErrorCode("communication.noConnection.callbackServer.notavailable",
         "The callback server is not available, this usually happens when the callback server is shutdown on client side",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.update", "interface.update")
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION_POLLING = new ErrorCode("communication.noConnection.polling",
         "The remote connection is not established and we are currently polling for it.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.util.qos.address.Address"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.util.qos.address.CallbackAddress")
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION_DEAD = new ErrorCode("communication.noConnection.dead",
         "The remote connection is not established and we have given up to poll for it.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect"),
            new ResourceInfo(ResourceInfo.REQ, "client.configuration", "client.configuration"),
            new ResourceInfo(ResourceInfo.REQ, "client.failsafe", "client.failsafe"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.util.qos.address.Address"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.util.qos.address.CallbackAddress")
         }
      );

   // The dispatch framework reacts specific for communication exceptions
   public static final ErrorCode COMMUNICATION_USER_HOLDBACK = new ErrorCode("communication.user.holdback",
         "See USER_UPDATE_HOLDBACK.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode USER = new ErrorCode("user",
         "This category stands for wrong usage by the programmer using xmlBlaster.",
         new ResourceInfo[] {
         }
      );

   // TODO: Replace by finer adjusting possibilities (like retry timeouts etc.)
   public static final ErrorCode USER_UPDATE_HOLDBACK = new ErrorCode("user.update.holdback",
         "You can throw this on client side in your update() method: Like this the server queues the message and sets the dispatcActive to false. You need to manually activate the dispatcher again.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode USER_UPDATE_DEADMESSAGE = new ErrorCode("user.update.deadMessage",
	         "You can throw this on client side in your update() method: Like this the server publishes the message as dead letter and removes it from the callback queue. Since xmlBlaster > v2.0.",
	         new ResourceInfo[] {
	         }
	      );

   public static final ErrorCode USER_WRONG_API_USAGE = new ErrorCode("user.wrongApiUsage",
         "Please check your client code.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode USER_CONFIGURATION = new ErrorCode("user.configuration",
         "Login to xmlBlaster failed due to configuration problems.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "client.configuration", "client.configuration"),
            new ResourceInfo(ResourceInfo.REQ, "client.failsafe", "client.failsafe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_VERSION = new ErrorCode("user.version",
	         "Your version is outdated, login to xmlBlaster failed.",
	         new ResourceInfo[] {
	         }
	      );

   public static final ErrorCode USER_CONFIGURATION_MAXSESSION = new ErrorCode("user.configuration.maxSession",
         "Login to xmlBlaster failed due to maximum sessions of a subject reached.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "client.failsafe", "client.failsafe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_CONFIGURATION_IDENTICALCLIENT = new ErrorCode("user.configuration.identicalClient",
         "Login to xmlBlaster failed, reconnect for other client instance on existing public session is switched off, see connect QoS reconnectSameClientOnly=true setting.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "client.failsafe", "client.failsafe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_SECURITY = new ErrorCode("user.security",
	         "General security exception, authentication or authorization.",
	         new ResourceInfo[] {
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION = new ErrorCode("user.security.authentication",
	         "Login to xmlBlaster failed due to some reason.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ACCESSDENIED = new ErrorCode("user.security.authentication.accessDenied",
         "Login failed due to missing privileges.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

	public static final ErrorCode USER_SECURITY_AUTHENTICATION_NFC_DEVICETYPE = new ErrorCode(
			"user.security.authentication.nfc_deviceType",
			"Your NFC login is not allowed for this device type.",
			new ResourceInfo[] {});

	public static final ErrorCode USER_SECURITY_AUTHENTICATION_NFC_NOTREGISTERED = new ErrorCode(
			"user.security.authentication.nfc_notRegistered", "Your NFC tagId is not known.",
			new ResourceInfo[] {});

	public static final ErrorCode USER_SECURITY_AUTHENTICATION_NFC_IMEI = new ErrorCode(
			"user.security.authentication.nfc_imei",
			"Your NFC tagId is used with unregistered device IMEI.",
			new ResourceInfo[] {});

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ILLEGALDEVICEID = new ErrorCode("user.security.authentication.illegalDeviceId",
	         "Login denied due to device identification failed. For example for invalid IMEI given.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ILLEGALDEVICETYPE = new ErrorCode("user.security.authentication.illegalDeviceType",
	         "Login denied due to illegal device type. For example for invalid smartphone type.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ACCESSDENIED_UNKNOWNLOGINNAME = new ErrorCode("user.security.authentication.accessDenied.unknownLoginName",
	         "Login failed due to unkown login name.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ACCESSDENIED_WRONGPASSWORD = new ErrorCode("user.security.authentication.accessDenied.wrongPassword",
	         "Login failed due to wrong password.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_INACTIVE = new ErrorCode("user.security.authentication.inactive",
	         "Login failed, the account is not active anymore.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_EXPIRED = new ErrorCode("user.security.authentication.expired",
	         "Login failed, the account is expired.",
	         new ResourceInfo[] {
	            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
	         }
	      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT = new ErrorCode("user.security.authentication.illegalArgument",
         "Login to xmlBlaster failed due to illegal arguments.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_SECURITY_AUTHORIZATION_NOTAUTHORIZED = new ErrorCode("user.security.authorization.notAuthorized",
         "Login to xmlBlaster failed due to missing privileges.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_UPDATE_ERROR = new ErrorCode("user.update.error",
         "Exception thrown by client on callback update invocation.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.update", "interface.update")
         }
      );

   public static final ErrorCode USER_UPDATE_INTERNALERROR = new ErrorCode("user.update.internalError",
         "Unexpected exception thrown by client code on programming error.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.update", "interface.update")
         }
      );

   public static final ErrorCode USER_UPDATE_ILLEGALARGUMENT = new ErrorCode("user.update.illegalArgument",
         "The update method was invoked without useful data.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.update", "interface.update")
         }
      );

   public static final ErrorCode USER_ILLEGALARGUMENT = new ErrorCode("user.illegalArgument",
         "You have invoked a server method with illegal arguments.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode USER_MAXSIZEEXCEED = new ErrorCode("user.maxSizeExceed",
	         "You have invoked a server method with too big data.",
	         new ResourceInfo[] {
	         }
	      );

   public static final ErrorCode USER_UPDATE_SECURITY_AUTHENTICATION_ACCESSDENIED = new ErrorCode("user.update.security.authentication.accessDenied",
         "The update method was invoked with an invalid callback session ID.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.update", "interface.update")
         }
      );

   public static final ErrorCode USER_PUBLISH_READONLY = new ErrorCode("user.publish.readonly",
         "You published a message which is marked as readonly.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "engine.qos.publish.readonly", "engine.qos.publish.readonly"),
         }
      );

   public static final ErrorCode USER_SUBSCRIBE_ID = new ErrorCode("user.subscribe.id",
         "Your subscription tries to pass an illegal subscriptionId.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.subscribe", "interface.subscribe"),
            new ResourceInfo(ResourceInfo.REQ, "engine.qos.subscribe.id", "engine.qos.subscribe.id")
         }
      );

   public static final ErrorCode USER_SUBSCRIBE_NOCALLBACK = new ErrorCode("user.subscribe.noCallback",
         "You try to subscribe to a topic but have no callback registered on connect.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect"),
         }
      );

   public static final ErrorCode USER_OID_UNKNOWN = new ErrorCode("user.oid.unknown",
         "You passed a message oid which is not known.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode USER_JDBC_INVALID = new ErrorCode("user.jdbc.invalid",
         "Illegal JDBC query or access.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "engine.service.rdbms", "engine.service.rdbms")
         }
      );

   public static final ErrorCode USER_CONNECT = new ErrorCode("user.connect",
         "Your connection request could not be handled, check your QoS",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_CONNECT_MULTIPLE = new ErrorCode("user.connect.multiple",
         "You have invoked connect() multiple times",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_NOT_CONNECTED = new ErrorCode("user.notConnected",
         "Your operation is not possible, please login with connect() first",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_PUBLISH = new ErrorCode("user.publish",
         "Your published message could not be handled, check your QoS",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.publish", "interface.publish")
         }
      );

   public static final ErrorCode USER_PTP_UNKNOWNSESSION = new ErrorCode("user.ptp.unknownSession",
         "You have send a point to point message to a specific user session but the receiver is not known.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.publish", "interface.publish")
         }
      );

   public static final ErrorCode USER_PTP_UNKNOWNDESTINATION = new ErrorCode("user.ptp.unknownDestination",
         "You have send a point to point message but the receiver is not known and <destination forceQueuing='true'> is not set.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.publish", "interface.publish")
         }
      );

   public static final ErrorCode USER_PTP_UNKNOWNDESTINATION_SESSION = new ErrorCode("user.ptp.unknownDestinationSession",
         "You have send a point to point message but the receiver session is not known.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.publish", "interface.publish")
         }
      );

   public static final ErrorCode USER_PTP_DENIED = new ErrorCode("user.ptp.denied",
           "You have send a point to point message but the receiver session does not accept PtP.",
           new ResourceInfo[] {
               new ResourceInfo(ResourceInfo.REQ, "interface.connect",
                   "interface.connect")
         }
      );

   public static final ErrorCode USER_MESSAGE_INVALID = new ErrorCode("user.message.invalid",
         "Usually thrown by a mime plugin if your MIME type does not fit to your message content, e.g. mime='text/xml' and content='Nice weather'.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "mime.plugin.accessfilter", "mime.plugin.accessfilter")
         }
      );

   public static final ErrorCode USER_QUERY_INVALID = new ErrorCode("user.query.invalid",
         "You have invoked get(), subscribe(), unSubscribe() or erase() with an illegal query syntax.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.get", "interface.get"),
            new ResourceInfo(ResourceInfo.REQ, "interface.subscribe", "interface.subscribe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.unSubscribe", "interface.unSubscribe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.erase", "interface.erase"),
            new ResourceInfo(ResourceInfo.API, "query syntax", "org.xmlBlaster.util.key.QueryKeyData")
         }
      );

   public static final ErrorCode USER_ADMIN_INVALID = new ErrorCode("user.admin.invalid",
         "Your administrative request was illegal.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "admin.messages", "admin.messages")
         }
      );

   public static final ErrorCode USER_QUERY_TYPE_INVALID = new ErrorCode("user.query.type.invalid",
         "You have invoked get(), subscribe(), unSubscribe() or erase() with an illegal query type, try EXACT or XPATH.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.get", "interface.get"),
            new ResourceInfo(ResourceInfo.REQ, "interface.subscribe", "interface.subscribe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.unSubscribe", "interface.unSubscribe"),
            new ResourceInfo(ResourceInfo.REQ, "interface.erase", "interface.erase"),
            new ResourceInfo(ResourceInfo.API, "query syntax", "org.xmlBlaster.util.key.QueryKeyData")
         }
      );

   public static final ErrorCode USER_CLIENTCODE = new ErrorCode("user.clientCode",
         "You may use this error code in your client implementation to throw your own exceptions.",
         new ResourceInfo[] {
         }
      );


   /**
    * @exception IllegalArgumentException if the given errorCode is null
    */
   private ErrorCode(String errorCode, String description, ResourceInfo[] resourceInfos) {
      if (errorCode == null)
         throw new IllegalArgumentException("Your given errorCode is null");
      this.errorCode = errorCode;
      this.description = (description == null) ? "" : description;
      this.resourceInfos = (resourceInfos == null) ? new ResourceInfo[0] : resourceInfos;
      errorCodeMap.put(errorCode, this);
   }

   /**
    * Return a human readable string of the errorCode and description
    * @return never null
    */
   public String toString() {
      return "errorCode=" + this.errorCode + ": " + this.description;
   }

   /**
    * Returns 'true' if this error code is a 'child' of the error code
    * specified in baseCode. It follows the name convention of the error
    * code. For example USER_SECURITY_AUTHENTICATION_DENIED
    * would be of type (as it is a subtype) of USER_SECURITY_AUTHENTICATION
    * If one of the error codes code name is null, false is returned.
    * @param baseCode the base ErrorCode to check against. If null, false is
    * returned. 
    * @return
    */
   public final boolean isOfType(ErrorCode baseCode) {
	   if (baseCode == null)
		   return false;
	   String baseCodeTxt = baseCode.getErrorCode();
	   if (this.errorCode == null || baseCodeTxt == null)
		   return false;
	   return this.errorCode.startsWith(baseCodeTxt);
   }
   /**
    * Returns the errorCode string. 
    * @return never null
    */
   public String getErrorCode() {
      return this.errorCode;
   }

   /**
    * Returns the description of the errorCode. 
    * @return never null
    */
   public String getDescription() {
      return this.description;
   }

   /**
    * Returns the description of the errorCode including the online link with further explanations. 
    * @return never null
    */
   public String getLongDescription() {
      return this.description + " -> " + getUrl();
   }

   /**
    * The link to find more information about this problem
    */
   public String getUrl() {
      return "http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#" + getErrorCode();
   }

   /**
    * Return resource info object telling us where to find more information
    * on this errorCode
    */
   public ResourceInfo[] getResourceInfos() {
      return this.resourceInfos;
   }
   
   /**
    * @return The top level category like 'internal'
    */
   public static ErrorCode getCategory(ErrorCode errorCode) {
      if (errorCode == null || errorCode.errorCode == null) return ErrorCode.INTERNAL;
      int index = errorCode.errorCode.indexOf(".");
      if (index == -1) return errorCode; // is already a top level
      if (index == 0) return ErrorCode.INTERNAL; // ".blabla" shouldn't appear (no leading dots)
      String top = errorCode.errorCode.substring(0,index);
      return toErrorCode(top);
   }

   /**
    * @return The top level category like 'internal'
    */
   public static ErrorCode getCategory(String errorCode) {
      if (errorCode == null) return ErrorCode.INTERNAL;
      int index = errorCode.indexOf(".");
      if (index == -1) return ErrorCode.toErrorCode(errorCode); // is already a top level
      if (index == 0) return ErrorCode.INTERNAL; // ".blabla" shouldn't appear (no leading dots)
      String top = errorCode.substring(0,index);
      return toErrorCode(top);
   }

   /**
    * Returns the ErrorCode object for the given String error code. 
    * @param errorCode The String code to lookup
    * @return The enumeration object for this errorCode
    * @exception IllegalArgumentException if the given errorCode is invalid
    */
   public static final ErrorCode toErrorCode(String errorCode) throws IllegalArgumentException {
      if (errorCode == null) {
         throw new IllegalArgumentException("ErrorCode: The given errorCode=" + errorCode + " is null");
      }
      Object entry = errorCodeMap.get(errorCode);
      if (entry == null)
         throw new IllegalArgumentException("ErrorCode: The given errorCode=" + errorCode + " is unknown");
      return (ErrorCode)entry;
   }

   /**
    * Returns the ErrorCode object for the given String error code. 
    * @param errorCode The String code to lookup
    * @param the code to use if errorCode is not known
    * @return The enumeration object for this errorCode
    */
   public static final ErrorCode toErrorCode(String errorCode, ErrorCode fallback) {
      if (errorCode == null) {
         return fallback;
      }
      Object entry = errorCodeMap.get(errorCode);
      if (entry == null)
         return fallback;
      return (ErrorCode)entry;
   }

   public final boolean equals(ErrorCode other) {
      return this.errorCode.equals(other.getErrorCode());
   }

   /**
    * Dump a plain list of all errorCodes. 
    * @return The list with each errorCode in a new line
    */
   public static String toPlainList() {
      StringBuffer sb = new StringBuffer(2560);
      String offset = "\n";
      java.util.Date date = new java.util.Date();
      String d = new java.sql.Timestamp(date.getTime()).toString();
      sb.append("# XmlBlaster ErrorCode listing " + d);
      Iterator it = errorCodeMap.keySet().iterator();
      while (it.hasNext()) {
         String code = (String)it.next();
         ErrorCode errorCode = (ErrorCode)errorCodeMap.get(code);
         sb.append(offset).append(errorCode.getErrorCode());
      }
      return sb.toString();
   }

   /**
    * Generate a HTML table listing of all error codes. 
    * @return The HTML markup
    */
   public static String toHtmlTable() {
      StringBuffer sb = new StringBuffer(2560);
      String offset = "\n ";
      sb.append(offset).append("<table border='1'>");
      Iterator it = errorCodeMap.keySet().iterator();
      sb.append(offset).append("<tr><th>Error Code</th><th>Description</th><th>See</th></tr>");
      while (it.hasNext()) {
         sb.append(offset).append("<tr>");
         String code = (String)it.next();
         ErrorCode errorCode = (ErrorCode)errorCodeMap.get(code);

         sb.append(offset).append(" <td><a name='").append(errorCode.getErrorCode()).append("'></a>");
         sb.append(errorCode.getErrorCode()).append("</td>");

         String desc = ReplaceVariable.replaceAll(errorCode.getDescription(),
                              "&", "&amp;");
         desc = ReplaceVariable.replaceAll(errorCode.getDescription(),
                              "<", "&lt;");
         sb.append(offset).append(" <td>").append(desc).append("</td>");
         
         ResourceInfo[] resourceInfos = errorCode.getResourceInfos();
         sb.append(offset).append(" <td>");
         for (int i=0; i<resourceInfos.length; i++) {
            if (i>0)
               sb.append("<br />");

            String resource = ReplaceVariable.replaceAll(resourceInfos[i].getResource(),
                              "<", "&lt;"); 
            String url=null;

            if (ResourceInfo.REQ.equals(resourceInfos[i].getType()))
               url="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/"+resource+".html";
            else if (ResourceInfo.URL.equals(resourceInfos[i].getType()))
               url= resource;
            else if (ResourceInfo.API.equals(resourceInfos[i].getType())) {
               String replace = ReplaceVariable.replaceAll(resource, ".", "/"); 
               url="http://www.xmlBlaster.org/xmlBlaster/doc/api/"+replace+".html";
            }
            else {
               System.out.println("Ignoring unknown resource type '" + resourceInfos[i].getType() + "'");
               continue;
            }

            sb.append("<a href='").append(url).append("' target='others'>");
            sb.append(resourceInfos[i].getLabel()).append("</a>");
         }
         if (resourceInfos.length == 0)
            sb.append("-");

         sb.append("</td>");
         sb.append(offset).append("</tr>");
      }
      sb.append(offset).append("</table>");

      return sb.toString();
   }

   public static String toRequirement() {
      String req=
         "<?xml version='1.0' encoding='ISO-8859-1' ?>\n"+
         "<!DOCTYPE requirement SYSTEM 'requirement.dtd'>\n" +
         "<requirement id='admin.errorcodes.listing' type='NEW' prio='LOW' status='CLOSED'>\n" +
         "   <topic>XmlBlaster error code reference</topic>\n" +
         "   <description>\n" +
         toHtmlTable() +
         "\nGenerated by org.xmlBlaster.util.def.ErrorCode\n" +
         "   </description>\n" +
         "</requirement>";
      return req;
   }

   public static String toXmlAll(String extraOffset) {
      StringBuffer sb = new StringBuffer(2560);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<ErrorCodes>");
      Iterator it = errorCodeMap.keySet().iterator();
      while (it.hasNext()) {
         String code = (String)it.next();
         ErrorCode errorCode = (ErrorCode)errorCodeMap.get(code);
         sb.append(errorCode.toXml(" "));
      }
      sb.append(offset).append("</ErrorCodes>");

      return sb.toString();
   }

   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<errorCode id='").append(getErrorCode()).append("'>");
      sb.append(offset).append(" <description>").append(getLongDescription()).append("</description>");
      for (int i=0; i<resourceInfos.length; i++)
         sb.append(resourceInfos[i].toXml(extraOffset+" "));
      sb.append(offset).append("</errorCode>");

      return sb.toString();
   }

   /**
    * This code is a helper for serialization so that after
    * deserial the check
    *   <pre>ErrorCode.INTERNAL_UNKNOWN == internalUnknownInstance</pre>
    * is still usable (the singleton is assured when deserializing)
    * <br />
    * See inner class SerializedForm
    */
   public Object writeReplace() throws java.io.ObjectStreamException {
      return new SerializedForm(this.getErrorCode());
   }
   /**
    * A helper class for singleton serialization. 
    */
   private static class SerializedForm implements java.io.Serializable {
      private static final long serialVersionUID = 1L;
      String errorCode;
      SerializedForm(String errorCode) { this.errorCode = errorCode; }
      Object readResolve() throws java.io.ObjectStreamException {
         return ErrorCode.toErrorCode(errorCode);
      }
   }

   /**
    * Generate a requirement file for all error codes. 
    * Used by build.xml, change with care!
    * <pre>
    *  java org.xmlBlaster.util.def.ErrorCode <HtmlFileName>
    *  java org.xmlBlaster.util.def.ErrorCode verifySerialization
    *  java org.xmlBlaster.util.def.ErrorCode toPlainList  
    * </pre>
    */
   public static void main(String [] args) {
      String file = "doc/requirements/admin.errorcodes.listing.xml";
      if (args.length > 0) {
         file = args[0];
      }
      if ("verifySerialization".equals(file)) {
         verifySerialization();
      }
      else if ("toPlainList".equals(file)) {
         System.out.println(toPlainList());
      }
      else {
         String req = toRequirement();
         try {
            FileLocator.writeFile(file, req);
            System.out.println("Created requirement file '" + file + "'");
         }
         catch (Exception e) {
            System.out.println("Writing file '" + file + "' failed: " + e.toString());
         }
      }
   }

   private static void verifySerialization() {
      String fileName = "ErrorCode.ser";
      ErrorCode pOrig = ErrorCode.USER_PTP_UNKNOWNSESSION;
      {

         try {
            java.io.FileOutputStream f = new java.io.FileOutputStream(fileName);
            java.io.ObjectOutputStream objStream = new java.io.ObjectOutputStream(f);
            objStream.writeObject(pOrig);
            objStream.flush();
            System.out.println("SUCCESS written " + pOrig.toString());
         }
         catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
         }
      }

      ErrorCode pNew = null;
      {

         try {
            java.io.FileInputStream f = new java.io.FileInputStream(fileName);
            java.io.ObjectInputStream objStream = new java.io.ObjectInputStream(f);
            pNew = (ErrorCode)objStream.readObject();
            System.out.println("SUCCESS loaded " + pNew.toString());
         }
         catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
         }
      }

      if (pNew.toString().equals(pOrig.toString())) {
         System.out.println("SUCCESS, string form is equals " + pNew.toString());
      }
      else {
         System.out.println("ERROR, string form is different " + pNew.toString());
      }

      int hashOrig = pOrig.hashCode();
      int hashNew = pNew.hashCode();

      if (pNew == pOrig) {
         System.out.println("SUCCESS, hash is same, the objects are identical");
      }
      else {
         System.out.println("ERROR, hashCode is different hashOrig=" + hashOrig + " hashNew=" + hashNew);
      }
   }
}


   /**
    * class holding reference data about other documentation locations
    */
   final class ResourceInfo {
      public final static String REQ = "REQ";
      public final static String API = "API";
      public final static String URL = "URL";

      private final String type; // "API", "REQ", "URL"
      private final String label;
      private final String resource;

      /**
       * @param type One of "API", "REQ", "URL"
       * @param label The visible name for the link
       * @param resource The classname (for API), the requirement name (for REQ) or the href url (for URL)
       */
      public ResourceInfo(String type, String label, String resource) {
         if (!REQ.equalsIgnoreCase(type) && !API.equalsIgnoreCase(type) && !URL.equalsIgnoreCase(type))
            throw new IllegalArgumentException("Construction of ResourceInfo with illegal type=" + type);
         if (label == null || label.length() < 1)
            throw new IllegalArgumentException("Construction of ResourceInfo with empty lable");
         this.type = type.toUpperCase();
         this.label = label;
         this.resource = (resource==null) ? "" : resource;
      }

      public String getType() { return this.type; }
      public String getLabel() { return this.label; }
      public String getResource() { return this.resource; }

      public final String toXml(String extraOffset) {
         StringBuffer sb = new StringBuffer(256);
         String offset = "\n ";
         if (extraOffset == null) extraOffset = "";
         offset += extraOffset;

         sb.append(offset).append("<ResourceInfo type='").append(getType()).append("'");
         sb.append(" label='").append(getLabel()).append("'>");

         sb.append(offset).append(" ").append(getResource());
         sb.append(offset).append("</ResourceInfo>");

         return sb.toString();
      }
   }




