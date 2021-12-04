/*------------------------------------------------------------------------------
Name:      XmlBlasterException.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Basic xmlBlaster exception.
------------------------------------------------------------------------------*/
#include <util/XmlBlasterException.h>
#include <util/ErrorCode.h>
#include <stdexcept>
#include <util/lexical_cast.h>
#include <util/Global.h>


using namespace std;

namespace org { namespace xmlBlaster { namespace util {

XmlBlasterException::XmlBlasterException(const string &errorCodeStr,
                    const string &node,
                    const string &location,
                    const string &lang,
                    const string &message,
                    const string &versionInfo,
                    const string &timestampStr,
                    const string &stackTrace,
                    const string &embeddedMessage,
                    const string &transactionInfo)
   :                errorCodeStr_(errorCodeStr),
                    node_(node),
                    location_(location),
                    lang_(lang),
                    message_(message),
                    versionInfo_(versionInfo),
                    timestamp_(timestampStr),
                    stackTrace_(stackTrace),
                    embeddedMessage_(embeddedMessage),
                    transactionInfo_(transactionInfo)
{
   if (embeddedMessage_ == "") {
      embeddedMessage_ = "Original errorCode=" + errorCodeStr_;
   }
   if (stackTrace_.size() < 1 && isInternal()) stackTrace_ = getStackTrace();
}


XmlBlasterException::XmlBlasterException(const ErrorCode &errorCode,
                    const string &node,
                    const string &location,
                    const string &lang,
                    const string &versionInfo,
                    const string &timestampStr,
                    const string &stackTrace,
                    const string &embeddedMessage,
                    const string &transactionInfo)
   :                errorCodeStr_(errorCode.errorCode),
                    node_(node),
                    location_(location),
                    lang_(lang),
                    message_(errorCode.description),
                    versionInfo_(versionInfo),
                    timestamp_(timestampStr),
                    stackTrace_(stackTrace),
                    embeddedMessage_(embeddedMessage),
                    transactionInfo_(transactionInfo)
{
   if (embeddedMessage_ == "") {
      embeddedMessage_ = "Original errorCode=" + errorCodeStr_;
   }
   if (stackTrace_.size() < 1 && isInternal()) stackTrace_ = getStackTrace();
}


XmlBlasterException::XmlBlasterException(const ErrorCode &errorCode,
                    const string &node,
                    const string &embeddedMessage)
   :                errorCodeStr_(errorCode.errorCode),
                    node_(node),
                    location_(""),
                    lang_("en"),
                    message_(errorCode.description),
                    versionInfo_(Global::getReleaseId()),
                    timestamp_(""),
                    stackTrace_(""),
                    embeddedMessage_(embeddedMessage),
                    transactionInfo_("<transactioninfo/>")
{
   if (embeddedMessage_ == "") {
      embeddedMessage_ = "Original errorCode=" + errorCodeStr_;
   }
   if (stackTrace_.size() < 1 && isInternal()) stackTrace_ = getStackTrace();
}

XmlBlasterException::~XmlBlasterException() throw()
{
}

const char *XmlBlasterException::what() const throw()
{
   str_ = toString();
   return str_.c_str();
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
   string ret = errorCodeStr_ ;
   ret += ", node=" + node_;
   if (getLocation() != "")        ret += ", location=" + getLocation();
   if (getLang() != "en")          ret += ", lang=" + getLang();
   if (getVersionInfo() != "")     ret += ", versionInfo=" + getVersionInfo();
   if (timestamp_ != "")           ret += ", timestamp=" + getTimestamp();
   if (getStackTrace() != "")      ret += ", stackTrace=" + getStackTrace();
   if (getEmbeddedMessage() != "") ret += ", embeddedMessage=" + getEmbeddedMessage();
   if (getTransactionInfo() != "" && getTransactionInfo() !=  "<transactioninfo/>")
                                   ret += ", transactionInfo=" + getTransactionInfo();
   ret += ", original message=" + message_;
  return ret;
}

string XmlBlasterException::getRawMessage() const
{
   return message_;
}

string XmlBlasterException::getVersionInfo() const
{
   return versionInfo_;
}

string XmlBlasterException::getTimestamp() const
{
   if (timestamp_ == "") {
      timestamp_ = lexical_cast<std::string>(TimestampFactory::getInstance().getTimestamp());
   }
   return timestamp_;
}

string XmlBlasterException::getStackTraceStr() const
{
   return stackTrace_;
}

string XmlBlasterException::getEmbeddedMessage() const
{
   return embeddedMessage_;
}

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
   size_t start = fromString.find("errorCode=");
   size_t end = fromString.find(" message=");
   if (start != string::npos) {
      if (end != string::npos) {
         try {
            errorCode = fromString.substr(start+(sizeof("errorCode=")/sizeof("e")), end);
         }
         catch(const out_of_range &/*e1*/) {
         }
      }
      else {
         try {
            errorCode = fromString.substr(start+(sizeof("errorCode=")/sizeof("e")));
         }
         catch(out_of_range &/*e2*/) {
         }
      }
   }
   if (end != string::npos) {
      try {
         reason = fromString.substr(end+(sizeof(" message=")/sizeof("e")));
      }
      catch(out_of_range &/*e3*/) {
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
string XmlBlasterException::toXml() const
{
   string buf = "<exception errorCode='" + getErrorCodeStr() + "'>\n";
   if (getNode() != "")            buf += "   <node>" + getNode() + "</node>\n";
   if (getLocation() != "")        buf += "   <location>" + getLocation() + "</location>\n";
   if (getLang() != "en")          buf += "   <lang>" + getLang() + "</lang>\n";
   if (getRawMessage() != "")      buf += "   <message><![CDATA[" + getRawMessage() + "]]></message>\n";
   if (getVersionInfo() != "")     buf += "   <versionInfo>" + getVersionInfo() + "</versionInfo>\n";
   buf += "   <timestamp>" + getTimestamp() + "</timestamp>\n";
   if (getStackTraceStr() != "")   buf += "   <stackTrace><![CDATA[" + getStackTraceStr() + "]]></stackTrace>\n";
   if (getEmbeddedMessage() != "") buf += "   <embeddedMessage><![CDATA[" + getEmbeddedMessage() + "]]></embeddedMessage>\n";
   //                              buf += "   <transactionInfo><![CDATA[" + getTransactionInfo() + "]]></transactionInfo>\n";
   buf += "</exception>";
   return buf;
}

#if defined(_ENABLE_STACK_TRACE_) && defined(__GNUC__)
string XmlBlasterException::getStackTrace(int maxNumOfLines)
{
   void** arr = new void*[maxNumOfLines];
   /*
   > +Currently, the function name and offset can only be obtained on systems
   > +that use the ELF binary format for programs and libraries.
   Perhaps a reference to the addr2line program can be added here.  It
   can be used to retrieve symbols even if the -rdynamic flag wasn't
   passed to the linker, and it should work on non-ELF targets as well.
   o  Under linux, gcc interprets it by setting the 
      "-export-dynamic" option for ld, which has that effect, according
      to the linux ld manpage.

   o Under IRIX it's ignored, and the program's happy as a clam.

   o Under SunOS-4.1, gcc interprets it by setting the -dc -dp
      options for ld, which again forces the allocation of the symbol
      table in the code produced (see ld(1) on a Sun).
   */
   int bt = backtrace(arr, maxNumOfLines);
   char** list = backtrace_symbols(arr, bt); // malloc the return pointer, the entries don't need to be freed
   string ret;
   for (int i=0; i<bt; i++) {
      if (list[i] != NULL) ret += list[i] + string("\n");
   }
   free(list);
   delete[] arr;
   if (ret.size() < 1) {
      ret = "Creation of stackTrace failed";
   }
   return ret;
}
#else
string XmlBlasterException::getStackTrace(int )
{
   return ""; //no stack trace provided in this system";
}
#endif

}}} // namespaces


