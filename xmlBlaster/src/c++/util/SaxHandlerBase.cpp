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

#include <util/SaxHandlerBase.h>
#include <sax/SAXException.hpp>
#include <util/XmlBlasterException.h>
#include <util/Global.h>

using namespace std;

using namespace org::xmlBlaster::util;
    
   
// SaxHandlerBase::SaxHandlerBase(int args, const char * const argc[])
SaxHandlerBase::SaxHandlerBase(Global& global)
: charTrimmer_(),
  xmlChTrimmer_(),
  global_(global),
  log_(global.getLog("util"))
{
//  log_.initialize();
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
  log_.call(me(), "parse");
  if (log_.TRACE) {
     log_.trace(me(), string("parse content:'") + xmlData + string("'"));
  }
  try {
    log_.trace(me(), "parse entrering try/catch block");
    SAXParser parser;
    log_.trace(me(), "parser successfully created");
    // = ParserFactory.makeParser(); // DEFAULT_PARSER_NAME
    parser.setDocumentHandler(this);
    parser.setErrorHandler(this);
    parser.setDTDHandler(this);
    MemBufInputSource inSource((const XMLByte*)xmlData.c_str(), 
                               xmlData.size(), "xmlBlaster", false);
    parser.parse(inSource);
  }
  catch (StopParseException&) {
    // If it does not work, it could be wrapped into SAXParseException
    log_.error(me(), string("StopParseException: ") +
                            "Parsing execution stopped half the way ");
    if (log_.TRACE) {
       string help = XmlBlasterException::getStackTrace();
       log_.plain(me(), help);
    }
    return;
  }
  catch (SAXException &err) {
    if (log_.TRACE) {
       char *msg = XMLString::transcode(err.getMessage());
       log_.error(me(), string("parse: SAXException. message:") + string(msg));
       log_.error(me(), string("parse: SAXException. stack trace:") + XmlBlasterException::getStackTrace());

       delete msg;
    }
    return;
  }

  catch (const bad_alloc& err) {
    log_.error(me(), string("parse: bad_alloc exception. message:") + err.what());
    return;
  }
  catch (const bad_exception& err) {
       log_.error(me(), string("parse: bad_exception exception. message:") + err.what());
    return;
  }
  catch (const exception& err) {
    log_.error(me(), string("parse: exception. message:") + err.what());
    return;
  }
  catch (...) {
       log_.error(me(), "parse: unknown exception.");
    return;
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
  if (log_.TRACE)
     log_.trace(me(), string("characters, character:'") + character_ + string("'"));
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


string
SaxHandlerBase::getStartElementAsString(const XMLCh* const name, AttributeList& attrs) const
{
   char *nameCh = NULL;
   char *keyCh = NULL;
   char *valueCh = NULL;
   string ret = "";
   try {
      nameCh = XMLString::transcode(name);
      int len = attrs.getLength();
      ret += string("<") + string(nameCh) + string(" ");
      for (int i = 0; i < len; i++) {
         if (keyCh != NULL) delete keyCh;
         if (valueCh != NULL) delete valueCh;
         keyCh   = XMLString::transcode(attrs.getName(i));
         valueCh = XMLString::transcode(attrs.getValue(i));
         ret += string(keyCh) + string("='") + string(valueCh) + string("' ");
      }
   }
   catch (...) { // to avoid memory leaks
   }
   delete nameCh;
   delete keyCh;
   delete valueCh;
   ret += ">";
   return ret;
}


/**
 * returns a value (usually from an attribute) as a string
 */
string
SaxHandlerBase::getStringValue(const XMLCh* const value)
{
   char* help1   = NULL;
   char* help2   = NULL;
   string ret;
   try {
      help1 = XMLString::transcode(value);
      help2 = charTrimmer_.trim(help1);
      ret.assign(help2);
   }
   catch (...) {}
   delete help1;
   delete help2;
   return ret;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool SaxHandlerBase::getStringAttr(const AttributeList& list, const XMLCh* const name, string& value, bool doTrim)
{
   const XMLCh* tmp = list.getValue(name);
   if (!tmp) return false;

   char* help1 = NULL;
   char* help2 = NULL;
   bool  ret = true;
   try {
      help1 = XMLString::transcode(tmp);
      if (!help1) return false;
      if (doTrim) {
         help2 = charTrimmer_.trim(help1);
	 if (!help2) ret = false;
	 if (ret) value.assign(help2);
      }
      else value.assign(help1);
   }
   catch (...) {}
   delete help1;
   delete help2;
   return ret;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool SaxHandlerBase::getIntAttr(const AttributeList& list, const XMLCh* const name, int& value)
{
   string buf;
   bool ret = getStringAttr(list, name, buf);
   if (ret) {
      value = atoi(buf.c_str());
      return true;
   }
   return false;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool SaxHandlerBase::getLongAttr(const AttributeList& list, const XMLCh* const name, long& value)
{
   string buf;
   bool ret = getStringAttr(list, name, buf);
   if (ret) {
      value = atol(buf.c_str());
      return true;
   }
   return false;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool SaxHandlerBase::getTimestampAttr(const AttributeList& list, const XMLCh* const name, Timestamp& value)
{
   string buf;
   bool ret = getStringAttr(list, name, buf);
   if (ret) {
      value = STRING_TO_TIMESTAMP(buf.c_str());
      return true;
   }
   return false;
}


/**
 * returns a value (usually from an attribute) as an integer
 */
int
SaxHandlerBase::getIntValue(const XMLCh* const value)
{
    char* help1   = NULL;
    char* help2   = NULL;
    int ret = 0;
    try {
       help1 = XMLString::transcode(value);
       help2 = charTrimmer_.trim(help1);
       ret = atoi(help2);
    }
    catch (...) {}
    delete help1;
    delete help2;
    return ret;
}

/**
 * returns a value (usually from an attribute) as a string
 */
long
SaxHandlerBase::getLongValue(const XMLCh* const value)
{
   char* help1   = NULL;
   char* help2   = NULL;
   long ret = 0l;
   try {
      help1 = XMLString::transcode(value);
      help2 = charTrimmer_.trim(help1);
      ret = atol(help2);
   }
   catch (...) {}
   delete help1;
   delete help2;
   return ret;
}

/**
 * returns a value (usually from an attribute) as a string
 */
Timestamp
SaxHandlerBase::getTimestampValue(const XMLCh* const value)
{
   char* help1   = NULL;
   char* help2   = NULL;
   Timestamp ret = 0l;
   try {
      help1 = XMLString::transcode(value);
      help2 = charTrimmer_.trim(help1);
      // this might not be fully ansi compatible
      ret = STRING_TO_TIMESTAMP(help2);
   }
   catch (...) {}
   delete help1;
   delete help2;
   return ret;
}

#endif
