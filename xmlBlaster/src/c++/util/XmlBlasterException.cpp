/*------------------------------------------------------------------------------
Name:      XmlBlasterException.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
------------------------------------------------------------------------------*/

#include <util/XmlBlasterException.h>
#include <util/ErrorCode.h>
#include <stdexcept>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util {

   XmlBlasterException::XmlBlasterException(const string &errorCodeStr,
                       const string &node,
                       const string &location,
                       const string &lang,
                       const string &message,
                       const string &versionInfo,
                       Timestamp timestamp,
                       const string &stackTrace,
                       const string &embeddedMessage,
                       const string &transactionInfo)
      :                errorCodeStr_(errorCodeStr),
                       node_(node),
                       location_(location),
                       lang_(lang),
                       message_(message),
                       versionInfo_(versionInfo),
                       timestamp_(timestamp),
                       stackTrace_(stackTrace),
                       embeddedMessage_(embeddedMessage),
                       transactionInfo_(transactionInfo)
   {
      if (embeddedMessage_ == "") {
         embeddedMessage_ = "Original errorCode=" + errorCodeStr_;
      }
   }


   XmlBlasterException::XmlBlasterException(const ErrorCode &errorCode,
                       const string &node,
                       const string &location,
                       const string &lang,
                       const string &versionInfo,
                       Timestamp timestamp,
                       const string &stackTrace,
                       const string &embeddedMessage,
                       const string &transactionInfo)
      :                errorCodeStr_(errorCode.errorCode),
                       node_(node),
                       location_(location),
                       lang_(lang),
                       message_(errorCode.description),
                       versionInfo_(versionInfo),
                       timestamp_(timestamp),
                       stackTrace_(stackTrace),
                       embeddedMessage_(embeddedMessage),
                       transactionInfo_(transactionInfo)
   {
      if (embeddedMessage_ == "") {
         embeddedMessage_ = "Original errorCode=" + errorCodeStr_;
      }
   }

   string XmlBlasterException::getErrorCodeStr() const
   {
      return errorCodeStr_;
   }

   string XmlBlasterException::getNode() const
   {
      return node_;
   }

   string XmlBlasterException::getLocation() const
   {
      return location_;
   }

   string XmlBlasterException::getLang() const
   {
      return lang_;
   }


   string XmlBlasterException::getMessage() const
   {
      string ret = errorCodeStr_ + ", node=" + node_ +
        ", location=" + location_ + ", lang=" + lang_ +
        "versionInfo=" + versionInfo_ + ", timestamp=" +
        lexical_cast<string>(timestamp_) + ", stackTrace=" + stackTrace_ +
        ", embeddedMessage=" + embeddedMessage_ + ", transactionInfo=" +
        transactionInfo_ + ", original message=" + message_;
     return ret;
   }

   /**
    * @return The original message text
    */
   string XmlBlasterException::getRawMessage() const
   {
      return message_;
   }

   /**
    * A comma separated list with key/values containing detailed
    * information about the server environment
    */
   string XmlBlasterException::getVersionInfo() const
   {
      return versionInfo_;
   }

   /**
    * Timestamp when exception was thrown
    */
   Timestamp XmlBlasterException::getTimestamp()
   {
      if (timestamp_ == 0) {
         timestamp_ = TimestampFactory::getInstance().getTimestamp();
      }
      return timestamp_;
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
   string XmlBlasterException::getStackTraceStr() const
   {
      return stackTrace_;
   }

   /**
    * @return The toString() of the embedded exception which is <classname>:getMessage()<br />
    *         or null if not applicable
    */
   string XmlBlasterException::getEmbeddedMessage() const
   {
      return embeddedMessage_;
   }

   /**
    * @return Not defined yet
    */
   string XmlBlasterException::getTransactionInfo() const
   {
      return transactionInfo_;
   }

   bool XmlBlasterException::isInternal() const
   {
      return (errorCodeStr_.find("internal") == 0);
   }

   bool XmlBlasterException::isResource() const
   {
      return (errorCodeStr_.find("resource") == 0);
   }

   bool XmlBlasterException::isCommunication() const
   {
      return (errorCodeStr_.find("communication") == 0);
   }

   bool XmlBlasterException::isUser() const
   {
      return (errorCodeStr_.find("user") == 0);
   }

   bool XmlBlasterException::isTransaction() const
   {
      return (errorCodeStr_.find("transaction") == 0);
   }

   /**
    * Returns a stringified version of the exception
    */
   string XmlBlasterException::toString() const
   {
      return "errorCode=" + getErrorCodeStr() + " message=" + getRawMessage();
   }

   /**
    * Parsing what toString() produced
    */
   XmlBlasterException XmlBlasterException::parseFromString(string fromString)
   {
      string errorCode = fromString;
      string reason = fromString;
      int start = fromString.find("errorCode=");
      int end = fromString.find(" message=");
      if (start != string::npos) {
         if (end != string::npos) {
            try {
               errorCode = fromString.substr(start+(sizeof("errorCode=")/sizeof("e")), end);
            }
            catch(const out_of_range &e1) {
            }
         }
         else {
            try {
               errorCode = fromString.substr(start+(sizeof("errorCode=")/sizeof("e")));
            }
            catch(out_of_range e2) {
            }
         }
      }
      if (end >= 0) {
         try {
            reason = fromString.substr(end+(sizeof(" message=")/sizeof("e")));
         }
         catch(out_of_range e3) {
         }
      }
      try {
         return XmlBlasterException(errorCode, "XmlBlasterException", "en", reason);
      }
      catch (...) {
         return XmlBlasterException(INTERNAL_ILLEGALARGUMENT.errorCode, "XmlBlasterException", "en", fromString);
      }
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
   string XmlBlasterException::toXml()
   {
      string buf = "<exception errorCode='" + getErrorCodeStr() + "'>\n" +
         "   <class>c++ client</class>\n" +
      "   <node>" + getNode() + "</node>\n" +
      "   <location>" + getLocation() + "</location>\n" +
      "   <lang>" + getLang() + "</lang>\n" +
      "   <message><![CDATA[" + getRawMessage() + "]]></message>\n" +
      "   <versionInfo>" + getVersionInfo() + "</versionInfo>\n" +
      "   <timestamp>" + lexical_cast<string>(getTimestamp()) + "</timestamp>\n" +
      "   <stackTrace><![CDATA[" + getStackTraceStr() + "]]></stackTrace>\n" +
      "   <embeddedMessage><![CDATA[" + getEmbeddedMessage() + "]]></embeddedMessage>\n" +
      //"   <transactionInfo><![CDATA[" + getTransactionInfo() + "]]></transactionInfo>\n" +
      "</exception>";
      return buf;
   }

}}}; // namespaces


int main() {
   return 0;
}

