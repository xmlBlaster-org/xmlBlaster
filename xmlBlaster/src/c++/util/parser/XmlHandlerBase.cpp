/*-----------------------------------------------------------------------------
Name:      XmlHandlerBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_XMLHANDLERBASE_C
#define _UTIL_XMLHANDLERBASE_C

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif

#include <util/parser/XmlHandlerBase.h>
#include <util/StopParseException.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/StopWatch.h>
#include <util/lexical_cast.h>
#include <iostream>
#include <util/parser/ParserFactory.h>

namespace org { namespace xmlBlaster { namespace util { namespace parser {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;

XmlHandlerBase::XmlHandlerBase(Global& global) :
            ME("XmlHandlerBase"),
            global_(global),
            log_(global.getLog("org.xmlBlaster.util.xml")),
            invocationMutex_()
{
   doTrimStrings_ = true;
   if (log_.call()) log_.trace(ME, "Creating new XmlHandlerBase");
}

void XmlHandlerBase::init(const string &xmlLiteral)
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
void XmlHandlerBase::parse(const string &xmlData)
{
   log_.call(ME, "parse");
   if (log_.trace()) {
      log_.trace(ME, string("parse content:'") + xmlData + string("'"));
   }
   StopWatch stopWatch;
   I_Parser *parser = NULL;
   try {
      parser = ParserFactory::getFactory(global_).createParser(this);
      Lock lock(invocationMutex_);
      parser->parse(xmlData);
      delete parser;
      parser = NULL;
   }
   catch (StopParseException&) {
      // If it does not work, it could be wrapped into SAXParseException
      log_.error(ME, string("StopParseException: ") + "Parsing execution stopped half the way ");
      if (log_.trace()) {
         string help = XmlBlasterException::getStackTrace();
         log_.plain(ME, help);
      }
      delete parser;
      return;
   }
   catch (XmlBlasterException& ex) {
      delete parser;
      throw ex;
   }
   catch (const exception& err) {
      delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("parse: exception. message:") + err.what());
   }
   catch (const string& err) {
      delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("parse: exception. message:") + err);
   }
   catch (const char* err) {
      delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("parse: exception. message:") + err);
   }
   catch (...) {
      delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("parse: unknown exception."));
   }
   if (log_.trace()) log_.trace(ME, "Time used for parsing: " + stopWatch.nice());
}

/**
 * This characters emulates the java version but keep in mind that it is
 * not the virtual method inherited from DocumentHandler !!
 */
void XmlHandlerBase::characters(const string &ch) 
{
   if (doTrimStrings_) {
      character_ += trimmer_.trim(ch);
   }
   else character_ += ch;
   //if (log_.trace()) log_.trace(ME, string("characters, character:'") + character_ + string("'"));
}

void XmlHandlerBase::endCDATA()
{
   character_ += "]]>";
   doTrimStrings_ = true;
   if (log_.trace()) log_.trace(ME, "end of cdata");
}

void XmlHandlerBase::startCDATA()
{
   character_ += "<![CDATA[";
   doTrimStrings_ = false;
   if (log_.trace()) log_.trace(ME, "start of cdata");
}

void XmlHandlerBase::startDocument()
{
   if (log_.trace()) log_.trace(ME, "startDocument");
}

void XmlHandlerBase::endDocument()
{
   if (log_.trace()) log_.trace(ME, "endDocument");
}

void XmlHandlerBase::startElement(const string &name, const AttributeMap& attrs) 
{
   log_.warn(ME,"Please provide your startElement() impl. for: " + getStartElementAsString(name, attrs));
}

/** End element. */
void XmlHandlerBase::endElement(const string &/*name*/)
{
   log_.warn(ME,"Please provide your endElement() impl.");
}
   
//
// ErrorHandler methods
//

/** Warning. */
void XmlHandlerBase::warning(const string &exTxt) 
{
   string txt = exTxt + xmlLiteral_;
   log_.warn(ME, txt);
}
      
      
/** Error. */
void XmlHandlerBase::error(const string &exTxt) 
{
   string txt = exTxt + xmlLiteral_;
   log_.warn(ME, txt);
}


/** Fatal error. */
void XmlHandlerBase::fatalError(const string &exTxt) 
{
   string txt = exTxt + xmlLiteral_;
   log_.warn(ME, txt);
   throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("parse: exception. message:") + exTxt);
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool XmlHandlerBase::getStringAttr(const AttributeMap& attrs, const string &name, string& value, bool doTrim) const
{
   AttributeMap::const_iterator iter = attrs.find(name);
   if (iter == attrs.end()) return false;
   if (doTrim) {
      value.assign(StringTrim::trim((*iter).second));
   }
   else value.assign((*iter).second);
   return true;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool XmlHandlerBase::getIntAttr(const AttributeMap &attrs, const string &name, int& value) const
{
   string buf;
   bool ret = getStringAttr(attrs, name, buf);
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
bool XmlHandlerBase::getLongAttr(const AttributeMap &attrs, const string &name, long& value) const
{
   string buf;
   bool ret = getStringAttr(attrs, name, buf);
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
bool XmlHandlerBase::getTimestampAttr(const AttributeMap& attrs, const string &name, Timestamp& value) const
{
   string buf;
   bool ret = getStringAttr(attrs, name, buf);
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
bool XmlHandlerBase::getBoolAttr(const AttributeMap &attrs, const string &name, bool& value) const
{
   string buf;
   bool ret = getStringAttr(attrs, name, buf);
   if (ret) {
      value = ( (buf == "true") || (buf == "TRUE") );
      return true;
   }
   return false;
}


/**
 * returns a value (usually from an attribute) as an integer
 */
int XmlHandlerBase::getIntValue(const string &value) const
{
   if (value.length() < 1) return 0;
   try {
      return lexical_cast<int>(value);
   }
   catch (...) {
      cerr << "XmlHandlerBase:: Conversion from " << value << " to int failed" << endl;
   }
   return 0;
}

/**
 * returns a value (usually from an attribute) as a long
 */
long XmlHandlerBase::getLongValue(const string &value) const
{
   if (value.length() < 1) return 0l;
   try {
      return lexical_cast<long>(value);
   }
   catch (...) {
      cerr << "XmlHandlerBase:: Conversion from " << value << " to long failed" << endl;
   }
   return 0l;
}

/**
 * returns a value (usually from an attribute) as a Timestamp
 */
Timestamp XmlHandlerBase::getTimestampValue(const string &value) const
{
   Timestamp ret = 0l;
   try {
      ret = lexical_cast<Timestamp>(value);
   }
   catch (...) {
      cerr << "XmlHandlerBase:: Conversion from " << value << " to Timestamp failed" << endl;
   }
   return ret;
}

/**
 * returns a value (usually from an attribute) as a bool
 */
bool XmlHandlerBase::getBoolValue(const string &value) const
{
   try {
      return StringTrim::isTrue(value);
   }
   catch (...) {
      cerr << "XmlHandlerBase:: Conversion from " << value << " to bool failed" << endl;
   }
   return false;
}

std::string XmlHandlerBase::getStartElementAsString(const std::string &name, const AttributeMap &attrMap)
{
   string ret = string("<") + name + string(" ");
   AttributeMap::const_iterator iter = attrMap.begin();
   while (iter != attrMap.end()) {
      ret += (*iter).first + string("='") + (*iter).second + string("' ");
      iter++;
   }
   ret += string(">");
   return ret;
}

#endif

}}}} // namespace

