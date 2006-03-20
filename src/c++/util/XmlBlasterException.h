/*------------------------------------------------------------------------------
Name:      XmlBlasterException.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
------------------------------------------------------------------------------*/

/**
 * The basic exception handling class for xmlBlaster.
 * <p>
 * The getMessage() method returns a configurable formatted std::string
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
#include <stdexcept>

namespace org { namespace xmlBlaster { namespace util {

class Dll_Export XmlBlasterException : public std::exception
{
   private:
      const std::string errorCodeStr_;
      const std::string node_;
      std::string location_;
      const std::string lang_;
      const std::string message_;
      const std::string versionInfo_;
      mutable std::string timestamp_;
      std::string stackTrace_;
      std::string embeddedMessage_;
      const std::string transactionInfo_;
      mutable std::string str_; // for what(), holds memory for const char * return

   public:

   /**
    * For internal use: Deserializing and exception creation from CORBA XmlBlasterException
    */
   XmlBlasterException(const std::string &errorCodeStr,
                       const std::string &node,
                       const std::string &location,
                       const std::string &lang="en",
                       const std::string &message="",
                       const std::string &versionInfo="@version@",  // is replaced by ant / build.xml to e.g. "0.85c", see org::xmlBlaster::util::Global.getVersion(),
//                       org::xmlBlaster::util::Timestamp timestamp=0,
                       const std::string &timestampStr="",
                       const std::string &stackTrace="",
                       const std::string &embeddedMessage="",
                       const std::string &transactionInfo="<transactioninfo/>");

   XmlBlasterException(const ErrorCode &errorCode,
                       const std::string &node,
                       const std::string &location,
                       const std::string &lang,
                       const std::string &versionInfo="@version@",  // is replaced by ant / build.xml to e.g. "0.85c", see org::xmlBlaster::util::Global.getVersion(),
//                       org::xmlBlaster::util::Timestamp timestamp=0,
                       const std::string &timestampStr="",
                       const std::string &stackTrace="",
                       const std::string &embeddedMessage="",
                       const std::string &transactionInfo="<transactioninfo/>");

   XmlBlasterException(const ErrorCode &errorCode,
                       const std::string &node,
                       const std::string &embeddedMessage);

   virtual ~XmlBlasterException() throw();

   std::string getErrorCodeStr() const;
   std::string getNode() const;
   void setLocation(const std::string& location) { this->location_ = location; }
   std::string getLocation() const;
   std::string getLang() const;
   std::string getMessage() const;

   /**
    * @return The original message text
    */
   std::string getRawMessage() const;

   /**
    * A comma separated list with key/values containing detailed
    * information about the server environment
    */
   std::string getVersionInfo() const;

   /**
    * org::xmlBlaster::util::Timestamp when exception was thrown
    */
   std::string getTimestamp() const;

   /**
    * @return The stack trace or null, e.g.
    * <pre>
    *  stackTrace= errorCode=internal.unknown message=Bla bla
    *    at org.xmlBlaster.util.XmlBlasterException.main(XmlBlasterException.java:488)
    * </pre>
    * The first line is the result from toString() and the following lines
    * are the stackTrace
    */
   std::string getStackTraceStr() const;

   /**
    * @return The toString() of the embedded exception which is <classname>:getMessage()<br />
    *         or null if not applicable
    */
   std::string getEmbeddedMessage() const;

   /**
    * @return Not defined yet
    */
   std::string getTransactionInfo() const;

   bool isInternal() const;

   bool isResource() const;

   bool isCommunication() const;

   bool isUser() const;

   bool isTransaction() const;

   /**
    * Enforced by std::exception
    * @return The complete exception text with location and message
    */ 
   const char *what() const throw();

   /**
    * Returns a std::stringified version of the exception
    */
   std::string toString() const;

   /**
    * Parsing what toString() produced
    */
   static XmlBlasterException parseFromString(std::string fromString);

   /**
    * Create a XML representation of the Exception.
    * <pre>
    *   &lt;exception errorCode='resource.outOfMemory'>
    *      &lt;class>JavaClass&lt;/class>
    *      &lt;message>&lt;![cdata[  bla bla ]]>&lt;/message>
    *   &lt;/exception>
    * </pre>
    */
   std::string toXml() const;

   /**
    * Returns a std::string containing the stack trace if the system and the
    * compilation permit it (_ENABLE_STACK_TRACE must be set: which is set
    * in xmlBlasterDef.h in case of the gnu compiler.
    */
   static std::string getStackTrace(int maxNumOfLines=20);

};

}}} // namespaces

#endif
