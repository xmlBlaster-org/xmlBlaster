/*-----------------------------------------------------------------------------
Name:      SaxHandlerBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_SAXHANDLERBASE_C
#define _UTIL_SAXHANDLERBASE_C

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif

#include <SaxHandlerBase.h>

using namespace std;

using namespace org::xmlBlaster::util;
    
   
SaxHandlerBase::SaxHandlerBase(int args, char *argc[]) 
: log_(args, argc),
  charTrimmer_(),
  xmlChTrimmer_() 
{
  if (log_.CALL) log_.trace(me(), "Creating new SaxHandlerBase");
}

void
SaxHandlerBase::init(const string &xmlLiteral) 
{
  xmlLiteral_ = xmlLiteral;
  if (xmlLiteral_.size() > 0) {
    parse(xmlLiteral_);
  }
}
      
/**
 * Does the actual parsing
 * @param xmlData Quality of service in XML notation
 */
      
void 
SaxHandlerBase::parse(const string &xmlData) 
{
  try {
    SAXParser parser;
    // = ParserFactory.makeParser(); // DEFAULT_PARSER_NAME
    parser.setDocumentHandler(this);
    parser.setErrorHandler(this);
    parser.setDTDHandler(this);
    MemBufInputSource inSource((const XMLByte*)xmlData.c_str(), 
                               xmlData.size(), "xmlBlaster", false);
    parser.parse(inSource);
  }
  catch (StopParseException &) { 
    // If it does not work, it could be wrapped into SAXParseException
    if (log_.TRACE) log_.trace(me(), string("StopParseException: ") +
                          "Parsing execution stopped half the way");
    return;
  }
  catch (...) {
   cerr << "SOME OTHER EXEPTION" << std::endl;
  }
}

      

/**
 * This characters emulates the java version but keep in mind that it is
 * not the virtual method inherited from DocumentHandler !!
 */
void 
SaxHandlerBase::characters(const XMLCh* const ch, const unsigned int start,
                const unsigned int length) 
{
  char *chHelper = XMLString::transcode(ch);
  character_.assign(string(chHelper), start, length);
  delete chHelper;
}




//
// ErrorHandler methods
//

/** Warning. */
void
SaxHandlerBase::warning(const SAXParseException &ex) 
{
  string txt = getLocationString(ex);
  txt += string("\n") + xmlLiteral_;
  log_.warn(me(), txt);
}
      
      
/** Error. */
void 
SaxHandlerBase::error(const SAXParseException &ex) 
{
  string txt = getLocationString(ex);
  txt += string("\n") + xmlLiteral_;
  log_.warn(me(), txt);
}


/** Fatal error. */
void 
SaxHandlerBase::fatalError(const SAXParseException &ex) 
{
   string txt = getLocationString(ex);
   txt += string("\n") + xmlLiteral_;
   log_.warn(me(), txt);
   throw ex;
}

void 
SaxHandlerBase::notationDecl(const XMLCh* const name, const XMLCh* const publicId, 
                  const XMLCh* const systemId) 
{
   string txt             = "notationDecl(name=";
   char   *nameHelper     = XMLString::transcode(name);
   char   *publicIdHelper = XMLString::transcode(publicId);
   char   *systemIdHelper = XMLString::transcode(systemId);

   txt += string(nameHelper) + ", publicId=" + publicIdHelper 
      + ", systemId=" + systemIdHelper + ")";
   if (log_.TRACE) log_.trace(me(), txt);
   delete nameHelper;
   delete publicIdHelper;
   delete systemIdHelper;
}
      

/** Fatal error. */
void 
SaxHandlerBase::unparsedEntityDecl(const XMLCh* const name, 
                        const XMLCh* const publicId, 
                        const XMLCh* const systemId, 
                        const XMLCh* const notationName) 
{
  char *nameHelper         = XMLString::transcode(name);
  char *publicIdHelper     = XMLString::transcode(publicId);
  char *systemIdHelper     = XMLString::transcode(systemId);
  char *notationNameHelper = XMLString::transcode(notationName); 

  if (log_.TRACE) log_.trace(me(), string("unparsedEntityDecl(name=") +
                            nameHelper + ", publicId="+publicIdHelper+
                            ", systemId=" + systemIdHelper + 
                            ", notationName=" + notationNameHelper +
                            ")");
  delete nameHelper;
  delete publicIdHelper;
  delete systemIdHelper;
  delete notationNameHelper;
}

/** Returns a string of the location. */
string 
SaxHandlerBase::getLocationString(const SAXParseException &ex) 
{
  string str;
  char*  systemIdHelper = XMLString::transcode(ex.getSystemId()); 
  string systemId       = systemIdHelper;
  delete systemIdHelper;
  if (systemId != "") {
    int index = systemId.find_last_of('/');
    if (index != -1) systemId = systemId.substr(index + 1);
    str += systemId;
  }
  return str + ":" + boost::lexical_cast<string>(ex.getLineNumber()) 
             + ":" + boost::lexical_cast<string>(ex.getColumnNumber());
}

   
/**
 * Compares two strings (where name1 is a Unicode3.0 string!!) for 
 * unsensitive case compare. It returns true if the content of the
 * strings is equal (no matter what the case is). Using this method to
 * compare the strings should be portable to all platforms supported by
 * xerces.
 */
bool 
SaxHandlerBase::caseCompare(const XMLCh *name1, const char *name2) 
{
  XMLCh* name1Helper = XMLString::replicate(name1);
  XMLString::upperCase(name1Helper);
  XMLCh* name2Helper = XMLString::transcode(name2);
  XMLString::upperCase(name2Helper);
  bool ret = (XMLString::compareIString(name1Helper, name2Helper) == 0);
  delete name1Helper;
  delete name2Helper;
  return ret;
}

#endif
