/*------------------------------------------------------------------------------
Name:      XmlBlasterException.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
------------------------------------------------------------------------------*/

/**
 * The basic exception handling class for xmlBlaster.
 * <p>
 * The getMessage() method returns a configurable formatted string
 * (TODO)
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

#ifndef _UTIL_XMLBLASTER_EXCEPTION_H
#define _UTIL_XMLBLASTER_EXCEPTION_H

#include <string>
// #include <util/Timestamp.h>
#include <util/ErrorCode.h>

namespace org { namespace xmlBlaster { namespace util {

class Dll_Export XmlBlasterException
{
   private:
      const string errorCodeStr_;
      const string node_;
      const string location_;
      const string lang_;
      const string message_;
      const string versionInfo_;
      string timestamp_;
      string stackTrace_;
      string embeddedMessage_;
      const string transactionInfo_;

   public:

   /**
    * For internal use: Deserializing and exception creation from CORBA XmlBlasterException
    */
   XmlBlasterException(const string &errorCodeStr,
                       const string &node,
                       const string &location,
                       const string &lang="en",
                       const string &message="",
                       const string &versionInfo="client-c++",
//                       Timestamp timestamp=0,
                       const string &timestampStr="",
                       const string &stackTrace="",
                       const string &embeddedMessage="",
                       const string &transactionInfo="<transactioninfo/>");

   XmlBlasterException(const ErrorCode &errorCode,
                       const string &node,
                       const string &location,
                       const string &lang,
                       const string &versionInfo="client-c++",
//                       Timestamp timestamp=0,
                       const string &timestampStr="",
                       const string &stackTrace="",
                       const string &embeddedMessage="",
                       const string &transactionInfo="<transactioninfo/>");

   XmlBlasterException(const ErrorCode &errorCode,
                       const string &node,
                       const string &embeddedMessage);

   string getErrorCodeStr() const;
   string getNode() const;
   string getLocation() const;
   string getLang() const;
   string getMessage() const;

   /**
    * @return The original message text
    */
   string getRawMessage() const;

   /**
    * A comma separated list with key/values containing detailed
    * information about the server environment
    */
   string getVersionInfo() const;

   /**
    * Timestamp when exception was thrown
    */
   string getTimestamp();

   /**
    * @return The stack trace or null, e.g.
    * <pre>
    *  stackTrace= errorCode=internal.unknown message=Bla bla
    *    at org.xmlBlaster.util.XmlBlasterException.main(XmlBlasterException.java:488)
    * </pre>
    * The first line is the result from toString() and the following lines
    * are the stackTrace
    */
   string getStackTraceStr() const;

   /**
    * @return The toString() of the embedded exception which is <classname>:getMessage()<br />
    *         or null if not applicable
    */
   string getEmbeddedMessage() const;

   /**
    * @return Not defined yet
    */
   string getTransactionInfo() const;

   bool isInternal() const;

   bool isResource() const;

   bool isCommunication() const;

   bool isUser() const;

   bool isTransaction() const;

   /**
    * Returns a stringified version of the exception
    */
   string toString() const;

   /**
    * Parsing what toString() produced
    */
   static XmlBlasterException parseFromString(string fromString);

   /**
    * Create a XML representation of the Exception.
    * <pre>
    *   &lt;exception errorCode='resource.outOfMemory'>
    *      &lt;class>JavaClass&lt;/class>
    *      &lt;message>&lt;![cdata[  bla bla ]]>&lt;/message>
    *   &lt;/exception>
    * </pre>
    */
   string toXml();

   /**
    * Returns a string containing the stack trace if the system and the
    * compilation permit it (_ENABLE_STACK_TRACE must be set: which is set
    * in xmlBlasterDef.h in case of the gnu compiler.
    */
   static string getStackTrace(int maxNumOfLines=20);

};

}}}; // namespaces

#endif
