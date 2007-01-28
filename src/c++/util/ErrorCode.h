/*------------------------------------------------------------------------------
Name:      ErrorCode.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception ErrorCode definitions.
------------------------------------------------------------------------------*/

/**
 * The basic exception handling class for xmlBlaster.
 * <p>
 * The getMessage() method returns a configurable formatted std::string
 * (TODO)
 * here is an example how to configure the format in your xmlBlaster.properties:
 * <pre>
 *  XmlBlasterException.logFormat=XmlBlasterException errorCode=[{0}] node=[{1}] location=[{2}] message=[{4} : {8}]
 *  XmlBlasterException.logFormat.internal= org::xmlBlaster::util::XmlBlasterException errorCode=[{0}] node=[{1}] location=[{2}]\nmessage={4} : {8}\nversionInfo={5}\nstackTrace={7}
 *  XmlBlasterException.logFormat.resource= defaults to org::xmlBlaster::util::XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.communication= defaults to org::xmlBlaster::util::XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.user= defaults to org::xmlBlaster::util::XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.transaction= defaults to org::xmlBlaster::util::XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.legacy= defaults to org::xmlBlaster::util::XmlBlasterException.logFormat
 * </pre>
 * where the replacements are:
 * <pre>
 *  {0} = errorCodeStr
 *  {1} = node
 *  {2} = location
 *  {3} = lang
 *  {4} = message
 *  {5} = versionInfo
 *  {6} = timestamp
 *  {7} = stackTrace
 *  {8} = embeddedMessage
 *  {9} = transactionInfo
 * </pre>
 * @author "Marcel Ruff" <xmlBlaster@marcelruff.info>
 * @author "Michele Laghi" <laghi@swissinfo.org>
 * @since 0.8+ with extended attributes
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.errorcodes.html">The admin.errorcodes requirement</a>
 */

#ifndef _UTIL_ERRORCODE_H
#define _UTIL_ERRORCODE_H

#include <string>
#include <util/Timestamp.h>

namespace org { namespace xmlBlaster { namespace util {

   struct Dll_Export ErrorCode {
      std::string errorCode;
      std::string description;
      ErrorCode(const std::string &ec, const std::string &desc)
        : errorCode(ec), description(desc)
      {
      }
   };

   const ErrorCode LEGACY("legacy",
      std::string("This error code marks all old style org::xmlBlaster::util::XmlBlasterExceptions ") +
      std::string("until they are ported to the new behaviour.")
   );

   const ErrorCode INTERNAL_UNKNOWN("internal.unknown",
      std::string("This is an unknown and unexpected error, usually a runtime ") +
      std::string("exception, please post it to the mailing list.")
   );

   const ErrorCode INTERNAL_NULLPOINTER("internal.nullpointer",
      std::string("A null pointer is an xmlBlaster internal programming error, ") +
      std::string("please post it to the mailing list.")
   );

   const ErrorCode INTERNAL_ILLEGALARGUMENT("internal.illegalArgument",
      std::string("An illegal argument is an xmlBlaster internal programming ") +
      std::string("error, please post it to the mailing list.")
   );

   const ErrorCode INTERNAL_NOTIMPLEMENTED("internal.notImplemented",
      std::string("The feature is not implemented yet.")
   );

   const ErrorCode INTERNAL_CONNECTIONFAILURE("internal.connectionFailure",
      std::string("An internal error occurred, we were not able to access the ") +
      std::string("server handle.")
   );

   const ErrorCode INTERNAL_DISCONNECT("internal.disconnect",
      std::string("An internal error occurred when processing a disconnect() request.")
   );

   const ErrorCode INTERNAL_SUBSCRIBE("internal.subscribe",
         "An internal error occurred when processing a subscribe() request."
   );

   const ErrorCode INTERNAL_UNSUBSCRIBE("internal.unSubscribe",
         "An internal error occurred when processing an unSubscribe() request."
   );

   const ErrorCode INTERNAL_PUBLISH("internal.publish",
         "An internal error occurred when processing a publish() request."
   );

   const ErrorCode INTERNAL_ERASE("internal.erase",
         "An internal error occurred when processing a erase() request."
   );

   const ErrorCode INTERNAL_GET("internal.get",
         "An internal error occurred when processing a get() request."
   );

   const ErrorCode RESOURCE_OUTOFMEMORY("resource.outOfMemory",
      std::string("The JVM has no more RAM memory, try increasing it like 'java ") +
      std::string("-Xms18M -Xmx256M org.xmlBlaster.Main'")
   );

   const ErrorCode RESOURCE_TOO_MANY_THREADS("resource.tooManyThreads",
      std::string("The number of threads used is exceeded, try increasing the ") +
      std::string("number of threads in the properties")
   );

   const ErrorCode RESOURCE_CALLBACKSERVER_CREATION("resource.callbackServer.creation",
            "The callback server can't be created"
   );

   const ErrorCode RESOURCE_OVERFLOW_QUEUE_BYTES("resource.overflow.queue.bytes",
         "The maximum size in bytes of a queue is exhausted"
   );

   const ErrorCode RESOURCE_OVERFLOW_QUEUE_ENTRIES("resource.overflow.queue.entries",
         "The maximum number of entries of a queue is exhausted"
   );

   const ErrorCode RESOURCE_DB_UNAVAILABLE("resource.db.unavailable",
         "There is no connection to a backend database using JDBC"
   );

   const ErrorCode RESOURCE_CONFIGURATION_PLUGINFAILED("resource.configuration.pluginFailed",
         "A plugin required couldn't be loaded, please check your configuration."
   );

   const ErrorCode RESOURCE_CONFIGURATION_ADDRESS("resource.configuration.address",
         "A remote address you passed is invalid, please check your configuration."
   );

   const ErrorCode RESOURCE_FILEIO("resource.fileIO", "A file access failed.");

   const ErrorCode RESOURCE_FILEIO_FILELOST("resource.fileIO.fileLost",
         "A file disappeared, access failed."
   );

   const ErrorCode RESOURCE_CLUSTER_NOTAVAILABLE("resource.cluster.notAvailable",
         "A remote cluster node is not reachable."
   );

   const ErrorCode COMMUNICATION_NOCONNECTION("communication.noConnection",
         "A method invocation on a remote connection failed."
   );

   const ErrorCode COMMUNICATION_TIMEOUT("communication.timeout",
         "The socket call blocked until a timeout occurred."
   );

   const ErrorCode COMMUNICATION_RESPONSETIMEOUT("communication.responseTimeout",
         "A method call blocked when waiting on the ACK/NAK return message."
   );

   const ErrorCode COMMUNICATION_NOCONNECTION_POLLING("communication.noConnection.polling",
         "The remote connection is not established and we are currently polling for it."
   );

   const ErrorCode COMMUNICATION_NOCONNECTION_DEAD("communication.noConnection.dead",
         "The remote connection is not established and we have given up to poll for it."
   );

   const ErrorCode USER_WRONG_API_USAGE("user.wrongApiUsage",
         "Please check your code."
   );

   const ErrorCode USER_CONFIGURATION("user.configuration",
         "Login to xmlBlaster failed due to configuration problems."
   );

   const ErrorCode USER_SECURITY_AUTHENTICATION_ACCESSDENIED("user.security.authentication.accessDenied",
         "Login to xmlBlaster failed due to missing privileges."
   );

   const ErrorCode USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT("user.security.authentication.illegalArgument",
         "Login to xmlBlaster failed due to illegal arguments."
   );

   const ErrorCode USER_SECURITY_AUTHORIZATION_NOTAUTHORIZED("user.security.authorization.notAuthorized",
         "Login to xmlBlaster failed due to missing privileges."
   );

   const ErrorCode USER_UPDATE_ERROR("user.update.error",
         "Exception thrown by client on callback update invocation."
   );

   const ErrorCode USER_UPDATE_INTERNALERROR("user.update.internalError",
         "Unexpected exception thrown by client code on programming error."
   );

   const ErrorCode USER_UPDATE_ILLEGALARGUMENT("user.update.illegalArgument",
         "The update method was invoked without useful data."
   );

   const ErrorCode USER_ILLEGALARGUMENT("user.illegalArgument",
         "You have invoked a server method with illegal arguments."
   );

   const ErrorCode USER_UPDATE_HOLDBACK("user.update.holdback",
         "You can throw this on client side in your update() method: Like this the server queues the message and sets the dispatcActive to false. You need to manually activate the dispatcher again."
   );

   const ErrorCode USER_UPDATE_SECURITY_AUTHENTICATION_ACCESSDENIED("user.update.security.authentication.accessDenied",
         "The update method was invoked with an invalid callback session ID."
   );

   const ErrorCode USER_OID_UNKNOWN("user.oid.unknown",
         "You passed a message oid which is not known."
   );

   const ErrorCode USER_JDBC_INVALID("user.jdbc.invalid",
         "You have invoked get() with an illegal JDBC query."
   );

   const ErrorCode USER_PUBLISH("user.publish",
         "Your published message could not be handled, check your QoS"
   );

   const ErrorCode USER_SUBSCRIBE_ID("user.subscribe.id",
         "Your subscription tries to pass an illegal subscriptionId."
   );

   const ErrorCode USER_CONNECT("user.connect",
         "Your connection request could not be handled, check your QoS"
   );

   const ErrorCode USER_PTP_UNKNOWNSESSION("user.ptp.unknownSession",
      std::string("You have send a point to point message to a specific user ") +
      std::string("session but the receiver is not known.")
   );

   const ErrorCode USER_PTP_UNKNOWNDESTINATION("user.ptp.unknownDestination",
      std::string("You have send a point to point message but the receiver is not ") +
      std::string("known and <destination forceQueuing='true'> is not set.")
   );

   const ErrorCode USER_QUERY_TYPE_INVALID("user.query.type.invalid",
      std::string("You have invoked get(), subscribe(), unSubscribe() or erase() ") +
      std::string("with an illegal query type, try EXACT or XPATH.")
   );

   const ErrorCode USER_NOT_CONNECTED("user.notConnected",
      "Your operation is not possible, please login with connect() first"
   );


}}} // namespaces

#endif
