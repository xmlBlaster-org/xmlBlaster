/*------------------------------------------------------------------------------
Name:      ErrorCode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.enum;

import java.util.Hashtable;
import java.util.Iterator;

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
   private final static Hashtable hash = new Hashtable(); // The key is the 'errorCode' String and the value is an 'ErrorCode' instance
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

   public static final ErrorCode RESOURCE_OUTOFMEMORY = new ErrorCode("resource.outOfMemory",
         "The JVM has no more RAM memory, try increasing it like 'java -Xms18M -Xmx256M org.xmlBlaster.Main'",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.URL, "Increasing JVM heap", "http://java.sun.com/docs/hotspot/ism.html")
         }
      );

   public static final ErrorCode RESOURCE_TOO_MANY_THREADS = new ErrorCode("resource.tooManyThreads",
         "The number of threads used is exceeded, try increasing the number of threads in the properties",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "Increasing the number of threads", "queue.jdbc")
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
            new ResourceInfo(ResourceInfo.REQ, "engine.queue", "engine.queue"),
            new ResourceInfo(ResourceInfo.REQ, "engine.qos.update.queue", "engine.qos.update.queue"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.engine.helper.QueueProperty"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.engine.helper.CbQueueProperty")
         }
      );

   public static final ErrorCode RESOURCE_OVERFLOW_QUEUE_ENTRIES = new ErrorCode("resource.overflow.queue.entries",
         "The maximum number of entries of a queue is exhausted",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "engine.queue", "engine.queue"),
            new ResourceInfo(ResourceInfo.REQ, "engine.qos.update.queue", "engine.qos.update.queue"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.engine.helper.QueueProperty"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.engine.helper.CbQueueProperty")
         }
      );

   public static final ErrorCode RESOURCE_DB_UNAVAILABLE = new ErrorCode("resource.db.unavailable",
         "There is no connection to a backend database using JDBC",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.API, "JDBC connection management", "org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool")
         }
      );

   public static final ErrorCode RESOURCE_DB_UNKNOWN = new ErrorCode("resource.db.unknown",
         "An unknown error with the backend database using JDBC occurred",
         new ResourceInfo[] {
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
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_PLUGINFAILED = new ErrorCode("resource.configuration.pluginFailed",
         "A plugin required couldn't be loaded, please check your configuration.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode RESOURCE_CONFIGURATION_ADDRESS = new ErrorCode("resource.configuration.address",
         "A remote address you passed is invalid, please check your configuration.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.engine.helper.AddressBase")
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

   public static final ErrorCode COMMUNICATION_NOCONNECTION = new ErrorCode("communication.noConnection",
         "A specific remote connection throws an exception on invocation.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION_POLLING = new ErrorCode("communication.noConnection.polling",
         "The remote connection is not established and we are currently polling for it.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.engine.helper.Address"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.engine.helper.CallbackAddress")
         }
      );

   public static final ErrorCode COMMUNICATION_NOCONNECTION_DEAD = new ErrorCode("communication.noConnection.dead",
         "The remote connection is not established and we have given up to poll for it.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect"),
            new ResourceInfo(ResourceInfo.API, "client queue configuration", "org.xmlBlaster.engine.helper.Address"),
            new ResourceInfo(ResourceInfo.API, "callback queue configuration", "org.xmlBlaster.engine.helper.CallbackAddress")
         }
      );

   public static final ErrorCode USER_CONFIGURATION = new ErrorCode("user.configuration",
         "Login to xmlBlaster failed due to configuration problems.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "client.failsave", "client.failsave"),
            new ResourceInfo(ResourceInfo.REQ, "interface.connect", "interface.connect")
         }
      );

   public static final ErrorCode USER_SECURITY_AUTHENTICATION_ACCESSDENIED = new ErrorCode("user.security.authentication.accessDenied",
         "Login to xmlBlaster failed due to missing privileges.",
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

   public static final ErrorCode USER_OID_UNKNOWN = new ErrorCode("user.oid.unknown",
         "You passed a message oid which is not known.",
         new ResourceInfo[] {
         }
      );

   public static final ErrorCode USER_JDBC_INVALID = new ErrorCode("user.jdbc.invalid",
         "You have invoked get() with an illegal JDBC query.",
         new ResourceInfo[] {
            new ResourceInfo(ResourceInfo.REQ, "engine.service.rdbms", "engine.service.rdbms"),
            new ResourceInfo(ResourceInfo.REQ, "interface.get", "interface.get")
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
   ////////// END ////////////////////////////////////////////////////////////////


   /**
    * @exception IllegalArgumentException if the given errorCode is null
    */
   private ErrorCode(String errorCode, String description, ResourceInfo[] resourceInfos) {
      if (errorCode == null)
         throw new IllegalArgumentException("Your given errorCode is null");
      this.errorCode = errorCode;
      this.description = (description == null) ? "" : description;
      this.resourceInfos = (resourceInfos == null) ? new ResourceInfo[0] : resourceInfos;
      hash.put(errorCode, this);
   }

   /**
    * Return a human readable string of the errorCode and description
    */
   public String toString() {
      return "errorCode=" + this.errorCode + ": " + this.description;
   }

   /**
    * Returns the errorCode string. 
    */
   public String getErrorCode() {
      return this.errorCode;
   }

   /**
    * Returns the description of the errorCode. 
    */
   public String getDescription() {
      return this.description;
   }

   /**
    * Return resource info object telling us where to find more information
    * on this errorCode
    */
   public ResourceInfo[] getResourceInfos() {
      return this.resourceInfos;
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
      Object entry = hash.get(errorCode);
      if (entry == null)
         throw new IllegalArgumentException("ErrorCode: The given errorCode=" + errorCode + " is unknown");
      return (ErrorCode)entry;
   }

   public static String toXmlAll(String extraOffset) {
      StringBuffer sb = new StringBuffer(2560);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<ErrorCodes>");
      Iterator it = hash.keySet().iterator();
      while (it.hasNext()) {
         String code = (String)it.next();
         ErrorCode errorCode = (ErrorCode)hash.get(code);
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
      sb.append(offset).append(" <description>").append(getDescription()).append("</description>");
      for (int i=0; i<resourceInfos.length; i++)
         sb.append(resourceInfos[i].toXml(extraOffset+" "));
      sb.append(offset).append("</errorCode>");

      return sb.toString();
   }

   ///////////////
   // This code is a helper for serialization so that after
   // deserial the check
   //   PriortiyEnum.MAX == priorityInstance
   // is still usable (the singleton is assured when deserializing)
   public Object writeReplace() throws java.io.ObjectStreamException {
      return new SerializedForm(this.getErrorCode());
   }
   private static class SerializedForm implements java.io.Serializable {
      String errorCode;
      SerializedForm(String errorCode) { this.errorCode = errorCode; }
      Object readResolve() throws java.io.ObjectStreamException {
         return ErrorCode.toErrorCode(errorCode);
      }
   }
   ///////////////END

   /**
    * Dump all codes to xml notation
    * <pre>
    *  java org.xmlBlaster.util.enum.ErrorCode
    * </pre>
    */
   public static void main (String [] args) {
      System.out.println(toXmlAll(""));
      //verifiySerialization();
   }

   private static void verifiySerialization() {
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
         this.type = type.toUpperCase();
         this.label = label;
         this.resource = resource;
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



