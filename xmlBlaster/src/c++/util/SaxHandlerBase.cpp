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
#include <util/StopWatch.h>

#include <util/lexical_cast.h>


using namespace std;

using namespace org::xmlBlaster::util;


// SaxHandlerBase::SaxHandlerBase(int args, const char * const argc[])
SaxHandlerBase::SaxHandlerBase(Global& global)
: 
  global_(global),
  log_(global.getLog("util"))
{
//  log_.initialize();
  if (log_.call()) log_.trace(me(), "Creating new SaxHandlerBase");
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
  if (log_.trace()) {
     log_.trace(me(), string("parse content:'") + xmlData + string("'"));
  }
  StopWatch stopWatch;
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
    if (log_.trace()) {
       string help = XmlBlasterException::getStackTrace();
       log_.plain(me(), help);
    }
    return;
  }
  catch (SAXException &err) {
    string msg = getStringValue(err.getMessage());
    throw XmlBlasterException(USER_ILLEGALARGUMENT, me() + "::parse", string("sax parser exception: ") + msg);
  }

  catch (XmlBlasterException& ex) {
     throw ex;
  }
  catch (const exception& err) {
    throw XmlBlasterException(INTERNAL_UNKNOWN, me() + "::parse", string("parse: exception. message:") + err.what());
  }

  catch (const string& err) {
    throw XmlBlasterException(INTERNAL_UNKNOWN, me() + "::parse", string("parse: exception. message:") + err);
  }

  catch (const char* err) {
    throw XmlBlasterException(INTERNAL_UNKNOWN, me() + "::parse", string("parse: exception. message:") + err);
  }

  catch (...) {
    throw XmlBlasterException(INTERNAL_UNKNOWN, me() + "::parse", string("parse: unknown exception."));
  }

  if (log_.trace()) log_.trace(me(), "Time used for parsing: " + stopWatch.nice());
}

      

/**
 * This characters emulates the java version but keep in mind that it is
 * not the virtual method inherited from DocumentHandler !!
 */
void 
SaxHandlerBase::characters(const XMLCh* const ch, const unsigned int start,
                const unsigned int length) 
{
  character_.assign(getStringValue(ch), start, length);
  if (log_.trace()) log_.trace(me(), string("characters, character:'") + character_ + string("'"));
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
   string txt = "notationDecl(name=" +
        getStringValue(name) + ", publicId=" + getStringValue(publicId) +
        ", systemId=" + getStringValue(systemId) + ")";
   if (log_.trace()) log_.trace(me(), txt);
}
      

/** Fatal error. */
void 
SaxHandlerBase::unparsedEntityDecl(const XMLCh* const name, 
                        const XMLCh* const publicId, 
                        const XMLCh* const systemId, 
                        const XMLCh* const notationName) 
{
  if (log_.trace()) log_.trace(me(),
      string("unparsedEntityDecl(name=") + getStringValue(name) +
      ", publicId="+getStringValue(publicId)+
      ", systemId=" + getStringValue(systemId) + 
      ", notationName=" + getStringValue(notationName) +
      ")");
}

/** Returns a string of the location. */
string 
SaxHandlerBase::getLocationString(const SAXParseException &ex) 
{
  string systemId = getStringValue(ex.getSystemId());
  string str;
  if (systemId != "") {
    int index = systemId.find_last_of('/');
    if (index != -1) systemId = systemId.substr(index + 1);
    str = systemId + ":";
  }
  return str +lexical_cast<string>(ex.getLineNumber()) 
      + ":" + lexical_cast<string>(ex.getColumnNumber());
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
  XMLString::release(&name1Helper);
  XMLString::release(&name2Helper);
  return ret;
}

string SaxHandlerBase::getStartElementAsString(const XMLCh* const name, AttributeList& attrs) const
{
   string ret = "";
   try {
      int len = attrs.getLength();
      ret += string("<") + getStringValue(name) + string(" ");
      for (int i = 0; i < len; i++) {
         ret += getStringValue(attrs.getName(i)) + string("='") + getStringValue(attrs.getValue(i)) + string("' ");
      }
   }
   catch (...) { // to avoid memory leaks
   }
   ret += ">";
   return ret;
}


/**
 * returns a trimmed value (usually from an attribute) as a string
 */
string SaxHandlerBase::getStringValue(const XMLCh* const value) const
{
   char* help = 0;
   try {
      help = XMLString::transcode(value);
      if (help != 0) {
         string ret = StringTrim::trim(help);
         XMLString::release(&help);
         return ret;
      }
   }
   catch (...) {
      if (help != 0)
         XMLString::release(&help);
      cerr << "Caught exception in getStringValue(XMLCh=" << value << ")" << endl;
      // throw;
   }

   return string();
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool SaxHandlerBase::getStringAttr(const AttributeList& list, const XMLCh* const name, string& value, bool doTrim) const
{
   const XMLCh* tmp = list.getValue(name);
   if (!tmp) return false;

   char* help1 = NULL;
   try {
      help1 = XMLString::transcode(tmp);
      if (!help1) return false;
      if (doTrim) {
         value.assign(StringTrim::trim(help1));
      }
      else value.assign(help1);
   }
   catch (...) {}
   XMLString::release(&help1);
   return true;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool SaxHandlerBase::getIntAttr(const AttributeList& list, const XMLCh* const name, int& value) const
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
bool SaxHandlerBase::getLongAttr(const AttributeList& list, const XMLCh* const name, long& value) const
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
bool SaxHandlerBase::getTimestampAttr(const AttributeList& list, const XMLCh* const name, Timestamp& value) const
{
   string buf;
   bool ret = getStringAttr(list, name, buf);
   if (ret) {
//      value = STRING_TO_TIMESTAMP(buf.c_str());
      value = lexical_cast<Timestamp>(buf); 
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
bool SaxHandlerBase::getBoolAttr(const AttributeList& list, const XMLCh* const name, bool& value) const
{
   string buf;
   bool ret = getStringAttr(list, name, buf);
   if (ret) {
      value = ( (buf == "true") || (buf == "TRUE") );
      return true;
   }
   return false;
}


/**
 * returns a value (usually from an attribute) as an integer
 */
int
SaxHandlerBase::getIntValue(const XMLCh* const value) const
{
   if ( value == 0 ) return 0;
   try {
      return lexical_cast<int>(getStringValue(value));
   }
   catch (...) {
      cerr << "SaxHandlerBase:: Conversion from " << value << " to int failed" << endl;
   }
   return 0;
}

/**
 * returns a value (usually from an attribute) as a long
 */
long
SaxHandlerBase::getLongValue(const XMLCh* const value) const
{
   if ( value == 0 ) return 0l;
   try {
      return lexical_cast<long>(getStringValue(value));
   }
   catch (...) {
      cerr << "SaxHandlerBase:: Conversion from " << value << " to long failed" << endl;
   }
   return 0l;
}

/**
 * returns a value (usually from an attribute) as a Timestamp
 */
Timestamp
SaxHandlerBase::getTimestampValue(const XMLCh* const value) const
{
   Timestamp ret = 0l;
   try {
      ret = lexical_cast<Timestamp>(getStringValue(value));
   }
   catch (...) {
      cerr << "SaxHandlerBase:: Conversion from " << value << " to Timestamp failed" << endl;
   }
   return ret;
}

/**
 * returns a value (usually from an attribute) as a bool
 */
bool SaxHandlerBase::getBoolValue(const XMLCh* const value) const
{
   try {
      return StringTrim::isTrue(getStringValue(value));
   }
   catch (...) {
      cerr << "SaxHandlerBase:: Conversion from " << value << " to bool failed" << endl;
   }
   return false;
}


void SaxHandlerBase::releaseXMLCh(XMLCh** data) const
{
#ifdef OLDXERCES
   delete[] data;
#else
   XMLString::release(data);
#endif
}

#endif
