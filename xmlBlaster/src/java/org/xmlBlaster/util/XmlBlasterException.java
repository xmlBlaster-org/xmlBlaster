/*------------------------------------------------------------------------------
Name:      XmlBlasterException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.jutils.JUtilsException;


/**
 * The basic exception handling class for xmlBlaster.
 * <p>
 * This exception will be thrown in remote RMI calls as well.
 * </p>
 * <p>
 * The getMessage() method returns a configurable formatted string
 * here is an example how to configure the format in your xmlBlaster.properties:
 * <pre>
 *  XmlBlasterException.logFormat=XmlBlasterException errorCode=[{0}] node=[{1}] location=[{2}] message=[{4} : {8}]
 *  XmlBlasterException.logFormat.internal= XmlBlasterException errorCode=[{0}] node=[{1}] location=[{2}]\nmessage={4} : {8}\nversionInfo={5}\nstackTrace={7}
 *  XmlBlasterException.logFormat.resource= defaults to XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.communication= defaults to XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.user= defaults to XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.transaction= defaults to XmlBlasterException.logFormat
 *  XmlBlasterException.logFormat.legacy= defaults to XmlBlasterException.logFormat
 * </pre>
 * where the replacements are:
 * <pre>
 *  {0} = errorCodeStr
 *  {1} = node
 *  {2} = location
 *  {3} = isServerSide     // exception thrown from server or from client?
 *  {4} = message
 *  {5} = versionInfo
 *  {6} = timestamp
 *  {7} = stackTrace
 *  {8} = embeddedMessage
 *  {9} = errorCode.getDescription()
 *  // {10} = transactionInfo       IBM's JDK MakeFormat only supports 9 digits
 *  // {11} = lang                  IBM's JDK MakeFormat only supports 9 digits
 * </pre>
 * @author "Marcel Ruff" <xmlBlaster@marcelruff.info>
 * @since 0.8+ with extended attributes
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.errorcodes.html">The admin.errorcodes requirement</a>
 * @see org.xmlBlaster.test.classtest.XmlBlasterExceptionTest
 */
public class XmlBlasterException extends Exception implements java.io.Serializable
{
   transient private final Global glob;
   transient private ErrorCode errorCodeEnum;
   private String errorCodeStr;
   private final String node;
   private String location;
   private final String lang;
   private final String versionInfo;
   transient private Timestamp timestamp;
   private final long timestampNanos;
   private final String stackTrace;
   private boolean isServerSide;

   transient private final Throwable cause; // Since JDK 1.4 this is available in Throwable, we keep it here to support older JDK versions
   private String embeddedMessage;
   private final String transactionInfo;

   private final static String DEFAULT_LOGFORMAT = "XmlBlasterException errorCode=[{0}] serverSideException={3} node=[{1}] location=[{2}] message=[{4} : {8}]";
   private final static String DEFAULT_LOGFORMAT_INTERNAL = "XmlBlasterException serverSideException={3} node=[{1}] location=[{2}]\n" +
                                                            "{8}\n" +
                                                            "stackTrace={7}\n" +
                                                            "versionInfo={5}\n" +
                                                            "errorCode description={9}\n";
   private String logFormatInternal;
   private final String logFormatResource;
   private final String logFormatCommunication;
   private final String logFormatUser;
   private final String logFormatTransaction;
   private final String logFormatLegacy;
   private final String logFormat;

   /**
    * The errorCodeEnum.getDescription() is used as error message. 
    */
   public XmlBlasterException(Global glob, ErrorCode errorCodeEnum, String location) {
      this(glob, errorCodeEnum, location, (String)null, (Throwable)null);
   }

   public XmlBlasterException(Global glob, ErrorCode errorCodeEnum, String location, String message) {
      this(glob, errorCodeEnum, location, message, (Throwable)null);
   }

   public XmlBlasterException(Global glob, ErrorCode errorCodeEnum, String location, String message, Throwable cause) {
      this(glob, errorCodeEnum, (String)null, location, (String)null, message, (String)null, (Timestamp)null,
           (String)null, (String)null, (String)null, (glob==null)?true:glob.isServerSide(), cause);
   }

   /**
    * For internal use: Deserializing and exception creation from CORBA XmlBlasterException
    */
   public XmlBlasterException(Global glob, ErrorCode errorCodeEnum, String node, String location, 
                               String lang, String message, String versionInfo, Timestamp timestamp,
                               String stackTrace, String embeddedMessage, String transcationInfo,
                               boolean isServerSide) {
      this(glob, errorCodeEnum, node, location, lang, message, versionInfo, timestamp,
           stackTrace, embeddedMessage, transcationInfo, isServerSide, (Throwable)null);
   }

   private XmlBlasterException(Global glob, ErrorCode errorCodeEnum, String node, String location, 
                               String lang, String message, String versionInfo, Timestamp timestamp,
                               String stackTrace, String embeddedMessage, String transcationInfo,
                               boolean isServerSide, Throwable cause) {
      //super(message, cause); // JDK 1.4 only
      super((message == null || message.length() < 1) ? errorCodeEnum.getLongDescription() : message);
      this.glob = (glob == null) ? Global.instance() : glob;
      this.logFormat = this.glob.getProperty().get("XmlBlasterException.logFormat", DEFAULT_LOGFORMAT);
      this.logFormatInternal = this.glob.getProperty().get("XmlBlasterException.logFormat.internal", DEFAULT_LOGFORMAT_INTERNAL);
      this.logFormatResource = this.glob.getProperty().get("XmlBlasterException.logFormat.resource", this.logFormat);
      this.logFormatCommunication = this.glob.getProperty().get("XmlBlasterException.logFormat.communication", this.logFormat);
      this.logFormatUser = this.glob.getProperty().get("XmlBlasterException.logFormat.user", this.logFormat);
      this.logFormatTransaction = this.glob.getProperty().get("XmlBlasterException.logFormat.transaction", this.logFormat);
      this.logFormatLegacy = this.glob.getProperty().get("XmlBlasterException.logFormat.legacy", this.logFormat);

      this.errorCodeEnum = (errorCodeEnum == null) ? ErrorCode.INTERNAL_UNKNOWN : errorCodeEnum;
      this.errorCodeStr = this.errorCodeEnum.getErrorCode();
      this.node = (node == null) ? this.glob.getId() : node;
      this.location = location;
      this.lang = (lang == null) ? "en" : lang; // System.getProperty("user.language");
      this.versionInfo = (versionInfo == null) ? createVersionInfo() : versionInfo;
      this.timestamp = (timestamp == null) ? new Timestamp() : timestamp;
      this.timestampNanos = this.timestamp.getTimestamp();

      this.cause = cause;
      this.stackTrace = (stackTrace == null) ? createStackTrace() : stackTrace;
      this.embeddedMessage = (embeddedMessage == null) ?
                                ((this.cause == null) ? "" : this.cause.toString()) : embeddedMessage; // cause.toString() is <classname>:getMessage()
      this.transactionInfo = (transcationInfo == null) ? "<transaction/>" : transcationInfo;
      this.isServerSide = isServerSide;
   }

   public final void changeErrorCode(ErrorCode errorCodeEnum) {
      if (this.embeddedMessage == null || this.embeddedMessage.length() < 1) {
         this.embeddedMessage = "Original erroCode=" + this.errorCodeStr;
      }
      this.errorCodeEnum = (errorCodeEnum == null) ? ErrorCode.INTERNAL_UNKNOWN : errorCodeEnum;
      this.errorCodeStr = this.errorCodeEnum.getErrorCode();
   }

   public final Global getGlobal() {
      return this.glob;
   }

   /**
    * @return The error code enumeration object, is never null
    */
   public final ErrorCode getErrorCode() {
      if (this.errorCodeEnum == null) {
         try {
            this.errorCodeEnum = ErrorCode.toErrorCode(this.errorCodeStr);
         }
         catch (IllegalArgumentException e) {
            this.errorCodeEnum = ErrorCode.INTERNAL_UNKNOWN;
         }
      }
      return this.errorCodeEnum;
   }

   public final boolean isErrorCode(ErrorCode code) {
      return this.errorCodeEnum == code;
   }

   public final String getErrorCodeStr() {
      return this.errorCodeStr;
   }

   public final String getNode() {
      return this.node;
   }

   public final String getLocation() {
      return this.location;
   }

   /** Overwrite the location */
   public final void setLocation(String location) {
      this.location = location;
   }

   public final String getLang() {
      return this.lang;
   }

   /**
    * Configurable with property <i>XmlBlasterException.logFormat</i>,
    * <i>XmlBlasterException.logFormat.internal</i> <i>XmlBlasterException.logFormat.resource</i> etc.
    * @return e.g. errorCode + ": " + getMessage() + ": " + getEmbeddedMessage()
    */
   public String getMessage() {
      Object[] arguments = {  (errorCodeStr==null) ? "" : errorCodeStr,  // {0}
                              (node==null) ? "" : node,                  // {1}
                              (location==null) ? "" : location,          // {2}
                              new Boolean(isServerSide()),               // {3}
                              getRawMessage(),                           // {4}
                              (versionInfo==null) ? "" : versionInfo,         // {5}
                              (timestamp==null) ? "" : timestamp.toString(),  // {6}
                              (stackTrace==null) ? "" : stackTrace,           // {7}
                              (embeddedMessage==null) ? "" : embeddedMessage, // {8}
                              (errorCodeEnum==null) ? "" : errorCodeEnum.getUrl() // {9}
                              // NOTE: IBM JDK 1.3 can't handle {} greater 9!
                              //(errorCodeEnum==null) ? "" : errorCodeEnum.getLongDescription(), // {9}
                              //(transactionInfo==null) ? "" : transactionInfo, // {10}
                              //(lang==null) ? "" : lang,                  // {11}
                              };

      boolean handleAsInternal = this.cause != null &&
              (
                 this.cause instanceof XmlBlasterException && ((XmlBlasterException)this.cause).isInternal() ||
                 this.cause instanceof NullPointerException ||
                 this.cause instanceof IllegalArgumentException ||
                 this.cause instanceof ArrayIndexOutOfBoundsException ||
                 this.cause instanceof StringIndexOutOfBoundsException ||
                 this.cause instanceof ClassCastException ||
                 this.cause instanceof OutOfMemoryError
              );

      try {
         if (isInternal() || handleAsInternal) {
            return MessageFormat.format(this.logFormatInternal, arguments);
         }
         else if (isResource()) {
            return MessageFormat.format(this.logFormatResource, arguments);
         }
         else if (isCommunication()) {
            return MessageFormat.format(this.logFormatCommunication, arguments);
         }
         else if (isUser()) {
            return MessageFormat.format(this.logFormatUser, arguments);
         }
         else if (errorCodeEnum == ErrorCode.LEGACY) {
            return MessageFormat.format(this.logFormatLegacy, arguments);
         }
         else {
            return MessageFormat.format(this.logFormat, arguments);
         }
      }
      catch (IllegalArgumentException e) {
         glob.getLog("core").error("XmlBlasterException", "Please check your formatting string for exceptions, usually set by 'XmlBlasterException.logFormat=...'" +
                   " as described in http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.html: " + e.toString() +
                   "\nOriginal exception is: errorCode=" + errorCodeStr + " message=" + getRawMessage());
         if (isInternal() || handleAsInternal) {
            return MessageFormat.format(this.DEFAULT_LOGFORMAT_INTERNAL, arguments);
         }
         else if (isResource()) {
            return MessageFormat.format(this.DEFAULT_LOGFORMAT, arguments);
         }
         else if (isCommunication()) {
            return MessageFormat.format(this.DEFAULT_LOGFORMAT, arguments);
         }
         else if (isUser()) {
            return MessageFormat.format(this.DEFAULT_LOGFORMAT, arguments);
         }
         else if (errorCodeEnum == ErrorCode.LEGACY) {
            return MessageFormat.format(this.DEFAULT_LOGFORMAT, arguments);
         }
         else {
            return MessageFormat.format(this.DEFAULT_LOGFORMAT, arguments);
         }
      }
   }

   /**
    * Get the original message text, it is prefixed by the current subversion revision number. 
    * For example: "#12702M Can't find class MyPlugin"
    * @return The original message text, never null
    */
   public final String getRawMessage() {
      if (super.getMessage()!=null && super.getMessage().startsWith("#")) {
         return super.getMessage();
      }
      String revision = "#" + glob.getRevisionNumber();
      return (super.getMessage()==null) ? revision : revision + " " + super.getMessage();
   }

   /**
    * A comma separated list with key/values containing detailed
    * information about the server environment
    */
   public final String getVersionInfo() {
      return this.versionInfo;
   }

   /**
    * Timestamp when exception was thrown
    * @return Never null
    */
   public final Timestamp getTimestamp() {
      if (this.timestamp == null) {
         this.timestamp = new Timestamp(this.timestampNanos);
      }
      return this.timestamp;
   }

   /**
    * The original exception, note that this is not serialized. 
    * @return The original exception or null
    */
   public final Throwable getEmbeddedException() {
      //return getCause(); // JDK 1.4 or better only
      return this.cause;
   }

   /**
    * @return The stack trace or null, e.g.
    * <pre>
    *  stackTrace= errorCode=internal.unknown message=Bla bla
    *    at org.xmlBlaster.util.XmlBlasterException.main(XmlBlasterException.java:488)
    * </pre>
    * The first line is the result from toString() and the following lines
    * are the stackTrace
    */
   public final String getStackTraceStr() {
      return this.stackTrace;
   }

   /**
    * @return The toString() of the embedded exception which is <classname>:getMessage()<br />
    *         or null if not applicable
    */
   public final String getEmbeddedMessage() {
      return this.embeddedMessage;
   }

   /**
    * @return Not defined yet
    */
   public final String getTransactionInfo() {
      return this.transactionInfo;
   }

   /**
    * @return true if the exception occured on server side, false if happened on client side
    */
   public final boolean isServerSide() {
      return this.isServerSide;
   }

   /**
    * @param serverSide true to mark the exception has occurred on server side, false if happened on client side
    */
   public final void isServerSide(boolean serverSide) {
      this.isServerSide = serverSide;
   }

   public boolean isInternal() {
      return this.errorCodeStr.startsWith("internal");
   }

   public boolean isResource() {
      return this.errorCodeStr.startsWith("resource");
   }

   public boolean isCommunication() {
      return this.errorCodeStr.startsWith("communication");
   }

   public boolean isUser() {
      return this.errorCodeStr.startsWith("user");
   }

   public boolean isTransaction() {
      return this.errorCodeStr.startsWith("transaction");
   }

   private String createStackTrace() {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      if (this.cause != null) {
         cause.printStackTrace(pw);
      }
      printStackTrace(pw);  // prints: toString() and in next lines the stack trace
      return sw.toString().trim();
   }

   private String createVersionInfo() {
      StringBuffer buf = new StringBuffer(512);
      buf.append("version=").append(this.glob.getVersion()).append(",");
      buf.append("revision=").append(this.glob.getRevisionNumber()).append(",");
      buf.append("os.name=").append(System.getProperty("os.name", "unknown").trim()).append(",");
      buf.append("os.version=").append(System.getProperty("os.version", "unknown").trim()).append(",");
      buf.append("java.vm.vendor=").append(System.getProperty("java.vm.vendor", "unknown").trim()).append(",");
      buf.append("java.vm.version=").append(System.getProperty("java.vm.version", "unknown").trim()).append(",");
      buf.append("os.arch=").append(System.getProperty("os.arch", "unknown").trim()).append(",");
      buf.append("build.timestamp=").append(this.glob.getBuildTimestamp()).append(",");
      buf.append("build.java.vendor=").append(this.glob.getBuildJavaVendor()).append(",");
      buf.append("build.java.version=").append(this.glob.getBuildJavaVersion()); // .append(",");
      return buf.toString();
   }

   /**
    * @deprecated Please use constructor which uses ErrorCode
    */
   public XmlBlasterException(String location, String message) {
      this((Global)null, ErrorCode.LEGACY, location, message, (Throwable)null);
   }

   /**
    * @deprecated Please use constructor which uses ErrorCode
    */
   public XmlBlasterException(JUtilsException e) {
      this((Global)null, ErrorCode.LEGACY, e.id, e.reason);
   }

   /**
    * Caution: The syntax is used by parseToString() to parse the stringified exception again.<br />
    * This is used by XmlRpc, see XmlRpcConnection.extractXmlBlasterException()
    */
   public String toString() {
      //return getMessage();
      String text = "errorCode=" + getErrorCodeStr() + " message=" + getRawMessage();
      if (this.embeddedMessage != null && this.embeddedMessage.length() > 0) {
         text += " : " + embeddedMessage;
      }
      return text;
   }

   /**
    * Parsing what toString() produced
    */
   public static XmlBlasterException parseToString(Global glob, String toString) {
      String errorCode = toString;
      String reason = toString;
      int start = toString.indexOf("errorCode=");
      int end = toString.indexOf(" message=");
      if (start >= 0) {
         if (end >= 0) {
            try { errorCode = toString.substring(start+"errorCode=".length(), end); } catch(IndexOutOfBoundsException e1) {}
         }
         else {
            try { errorCode = toString.substring(start+"errorCode=".length()); } catch(IndexOutOfBoundsException e2) {}
         }
      }
      if (end >= 0) {
         try { reason = toString.substring(end+" message=".length()); } catch(IndexOutOfBoundsException e3) {}
      }
      try {
         return new XmlBlasterException(glob, ErrorCode.toErrorCode(errorCode), "XmlBlasterException", reason);
      }
      catch (IllegalArgumentException e) {
         glob.getLog("core").warn("XmlBlasterException", "Parsing exception string <" + toString + "> failed: " + e.toString());
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, "XmlBlasterException", toString);
      }
   }

   /**
    * @see #toXml(String)
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Create a XML representation of the Exception.
    * <pre>
    *   &lt;exception errorCode='resource.outOfMemory'>
    *      &lt;class>JavaClass&lt;/class>
    *      &lt;message>&lt;![cdata[  bla bla ]]>&lt;/message>
    *   &lt;/exception>
    * </pre>
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(getMessage().length() + 256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<exception errorCode='").append(getErrorCodeStr()).append("'>");
      sb.append(offset).append(" <class>").append(getClass().getName()).append("</class>");
      sb.append(offset).append(" <isServerSide>").append(isServerSide()).append("</isServerSide>");
      sb.append(offset).append(" <node>").append(getNode()).append("</node>");
      sb.append(offset).append(" <location>").append(getLocation()).append("</location>");
      sb.append(offset).append(" <lang>").append(getLang()).append("</lang>");
      sb.append(offset).append(" <message><![CDATA[").append(getRawMessage()).append("]]></message>");
      sb.append(offset).append(" <versionInfo>").append(getVersionInfo()).append("</versionInfo>");
      sb.append(offset).append(" <timestamp>").append(getTimestamp().toString()).append("</timestamp>");
      sb.append(offset).append(" <stackTrace><![CDATA[").append(getStackTraceStr()).append("]]></stackTrace>");
      sb.append(offset).append(" <embeddedMessage><![CDATA[").append(getEmbeddedMessage()).append("]]></embeddedMessage>");
      //sb.append(offset).append(" <transactionInfo><![CDATA[").append(getTransactionInfo()).append("]]></transactionInfo>");
      sb.append(offset).append("</exception>");
      return sb.toString();
   }

   /**
    * Serialize the complete exception
    */
   public byte[] toByteArr() {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream(1024);
      PrintWriter out = new PrintWriter(byteOut);
      out.write(getErrorCodeStr());
      out.write(0);
      out.write(getNode());
      out.write(0);
      out.write(getLocation());
      out.write(0);
      out.write(getLang());
      out.write(0);
      out.write(getRawMessage());
      out.write(0);
      out.write(getVersionInfo());
      out.write(0);
      out.write(getTimestamp().toString());
      out.write(0);
      out.write(getStackTraceStr());
      out.write(0);
      out.write(getEmbeddedMessage());
      out.write(0);
      out.write(getTransactionInfo());
      out.write(0);
      out.write(""+isServerSide());
      out.write(0);
      out.flush();
      byte[] result = byteOut.toByteArray();
      return result;
   }

   /**
    * Serialize the complete exception. 
    * Take care when changing!!!
    * Is used e.g. in CallbackServerUnparsed.c and XmlScriptInterpreter.java
    */
   public static XmlBlasterException parseByteArr(Global glob, byte[] data) {
      if (data == null)
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "XmlBlasterException", "Can't parse given serial XmlBlasterException data");
      int start = 0;
      int end = start;
      String errorCodeStr = null;
      String node = null;
      String location = null;
      String lang = null;
      String message = null;
      String versionInfo = null;
      String timestampStr = null;
      String stackTrace = null;
      String embeddedMessage = null;
      String transactionInfo = null;
      Boolean exceptionFromServer = new Boolean(true);

      try {
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         errorCodeStr = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         node = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         location = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         lang = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         message = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         versionInfo = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         timestampStr = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         stackTrace = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         embeddedMessage = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         transactionInfo = new String(data, start, end-start);

         start = end+1;
         for (end=start; end<data.length; end++)
            if (data[end] == 0)
               break;
         exceptionFromServer = new Boolean(new String(data, start, end-start));
      }
      catch (java.lang.StringIndexOutOfBoundsException e) {
         glob.getLog("core").error("XmlBlasterException", "Receiving invalid format for XmlBlasterException in '" + new String(data) + "'");
      }
      ErrorCode errorCode = ErrorCode.INTERNAL_UNKNOWN;
      try {
         errorCode = ErrorCode.toErrorCode(errorCodeStr);
      }
      catch (Throwable e) {
         glob.getLog("core").error("XmlBlasterException", "Receiving invalid errorCode in XmlBlasterException in '" + new String(data) + "'");
         message = "Can't parse XmlBlasterException in method parseByteArr(). original message is '" + new String(data) + "'";
      }
      Timestamp ti = new Timestamp();
      try {
         ti = Timestamp.valueOf(timestampStr);
      }
      catch (Throwable e) {
         glob.getLog("core").trace("XmlBlasterException", "Receiving invalid timestamp in XmlBlasterException in '" + new String(data) + "'");
      }
      return new XmlBlasterException(glob, errorCode,
                               node, location, lang, message, versionInfo, ti,
                               stackTrace, embeddedMessage, transactionInfo, exceptionFromServer.booleanValue());
   }

   /**
    * If throwable is of type XmlBlasterException it is just casted (and location/message are ignored)
    * else if throwable is one if IllegalArgumentException, NullpointerException or OutOfMemoryError
    * it is converted to an XmlBlasterException with corresponding ErrorCode
    * otherwise the ErrorCode is INTERNAL_UNKNOWN
    * @param location null if not of interest
    * @param message null if not of interest
    * @param throwable Any exception type you can think of
    * @return An exception of type XmlBlasterException
    */
   public static XmlBlasterException convert(Global glob, String location, String message, Throwable throwable) {
      return convert(glob, ErrorCode.INTERNAL_UNKNOWN, location, message, throwable);
   }

   /**
    * @param errorCodeEnum is the fallback error code
    */
   public static XmlBlasterException convert(Global glob, ErrorCode errorCodeEnum, String location, String message, Throwable throwable) {
      if (throwable instanceof XmlBlasterException) {
         return (XmlBlasterException)throwable;
      }
      else if (throwable instanceof NullPointerException) {
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_NULLPOINTER, location, message, throwable);
      }
      else if (throwable instanceof IllegalArgumentException) {
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, location, message, throwable);
      }
      else if (throwable instanceof ArrayIndexOutOfBoundsException) {
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, location, message, throwable);
      }
      else if (throwable instanceof StringIndexOutOfBoundsException) {
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, location, message, throwable);
      }
      else if (throwable instanceof ClassCastException) {
         return new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, location, message, throwable);
      }
      else if (throwable instanceof OutOfMemoryError) {
         return new XmlBlasterException(glob, ErrorCode.RESOURCE_OUTOFMEMORY, location, message, throwable);
      }
      else {
         return new XmlBlasterException(glob, errorCodeEnum, location, message, throwable);
      }
   }

   /**
    * Overwrite the formatting of internal logs
    * (the env property -XmlBlasterException.logFormat.internal)
    */
   public void setLogFormatInternal(String logFormatInternal) {
      this.logFormatInternal = logFormatInternal;
   }
   

   public static XmlBlasterException tranformCallbackException(XmlBlasterException e) {
      // TODO: Marcel: For the time being the client has the chance
      // to force requeueing by sending a USER_HOLDBACK which will lead
      // to a COMMUNICATION exception behaviour
      if (ErrorCode.USER_HOLDBACK.toString().equals(e.getErrorCode().toString())) {
         // Will set dispatcherActive==false
         return new XmlBlasterException(e.getGlobal(),
              ErrorCode.COMMUNICATION_USER_HOLDBACK,
              e.getEmbeddedMessage());
      }
   
      // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
      if (e.isUser())
         return e;
   
      
      // and server side communication problems (how to assure if from server?)
      if (e.isCommunication() && e.isServerSide())
         return e;
   
      // The SOCKET protocol plugin throws this when a client has shutdown its callback server
      //if (xmlBlasterException.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE)
      //   throw xmlBlasterException;
   
      return new XmlBlasterException(e.getGlobal(), ErrorCode.USER_UPDATE_ERROR, e.getLocation(), e.getRawMessage(), e);
   }

   /**
    * java org.xmlBlaster.util.XmlBlasterException
    */
   public static void main(String[] args) {
      Global glob = new Global(args);
      XmlBlasterException e = new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, "LOC", "Bla bla");
      System.out.println(e.toXml());
      byte[] serial = e.toByteArr();
      System.out.println("\n" + new String(serial));
      XmlBlasterException back = parseByteArr(glob, serial);
      System.out.println("BACK\n" + back.toXml());
      System.out.println("\ngetMessage:\n" + back.getMessage());

      e = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "LOC", "Bla bla");
      System.out.println("\ngetMessage:\n" + e.getMessage());

      e = XmlBlasterException.convert(glob, null, null, new IllegalArgumentException("wrong args"));
      System.out.println("\ngetMessage:\n" + e.getMessage());
   }
}
