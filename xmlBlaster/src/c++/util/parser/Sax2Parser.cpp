/*-----------------------------------------------------------------------------
Name:      Sax2Parser.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_SAX2PARSER_C
#define _UTIL_PARSER_SAX2PARSER_C

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif

#include <util/parser/Sax2Parser.h>
#include <sax/SAXException.hpp>
#include <sax2/SAX2XMLReader.hpp>
#include <sax2/XMLReaderFactory.hpp>
#include <framework/MemBufInputSource.hpp>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <iostream>
//#include <cstdlib> //<stdlib.h>

namespace org { namespace xmlBlaster { namespace util { namespace parser {

using namespace std;

Sax2Parser::Sax2Parser(org::xmlBlaster::util::Global& global, XmlHandlerBase *handler) : 
    I_Parser(handler), ME("Sax2Parser"), global_(global), log_(global.getLog("org.xmlBlaster.util.xml"))
{
   if (log_.call()) log_.trace(ME, "Creating new Sax2Parser");
}

void Sax2Parser::init(const string &xmlLiteral) 
{
   if (xmlLiteral.size() > 0) {
      parse(xmlLiteral);
   }
}
      
/**
 * Does the actual parsing
 * @param xmlData Quality of service in XML notation
 */
void Sax2Parser::parse(const string &xmlData) 
{
   //if (log_.call()) log_.call(ME, "parse");
   //if (log_.trace()) log_.trace(ME, string("parse content:'") + xmlData + string("'"));
 
   SAX2XMLReader *parser = NULL;
   try {
      parser = XMLReaderFactory::createXMLReader();
      parser->setContentHandler(this);
      parser->setErrorHandler(this);
      parser->setLexicalHandler(this);
      MemBufInputSource inSource((const XMLByte*)xmlData.c_str(), xmlData.size(), "xmlBlaster", false);
      parser->parse(inSource);
      delete parser;
   }
   catch (StopParseException&) {
      // If it does not work, it could be wrapped into SAXParseException
      log_.error(ME, string("StopParseException: ") +
                              "Parsing execution stopped half the way ");
      if (log_.trace()) {
         string help = XmlBlasterException::getStackTrace();
         log_.plain(ME, help);
      }
      delete parser; // just in case it did not 
      return;
   }
   catch (SAXParseException &err) {
      string loc = getLocationString(err) + string(": ") + getStringValue(err.getMessage());
      delete parser;
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::parse", string("SAXParseException") + loc);
   }
   catch (SAXNotRecognizedException &err) {
      string msg = getStringValue(err.getMessage());
      delete parser;
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::parse", string("SAXNotRecognizedException: ") + msg);
	}
   catch (SAXNotSupportedException &err) {
      string msg = getStringValue(err.getMessage());
      delete parser;
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::parse", string("SAXNotSupportedException: ") + msg);
	}
   catch (const XMLException &err) {
      string msg = getStringValue(err.getMessage());
      delete parser;
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::parse", string("XMLException: ") + msg);
   }
   catch (SAXException &err) {
      string msg = getStringValue(err.getMessage());
      delete parser;
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::parse", string("SAXException: ") + msg);
   }
   catch (...) {
     delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("Unknown parse exception: ") + xmlData);
   }
}

/** Receive notification of the end of the document. */
void Sax2Parser::endDocument() 
{
   if (log_.call()) log_.call(ME, string("endDocument"));
   handler_->endDocument();
}

/** Receive notification of the end of an element. */
void Sax2Parser::endElement(const XMLCh *const /*uri*/, const XMLCh *const /*localname*/, const XMLCh *const qname)
{
   //if (log_.call()) log_.call(ME, string("endElement"));
   handler_->endElement(getStringValue(qname));
}

/** Receive notification of the beginning of the document. */
void Sax2Parser::startDocument()
{
   //if (log_.call()) log_.call(ME, string("startDocument"));
   handler_->startDocument();
}

/** Receive notification of the start of an element. */
void Sax2Parser::startElement(const XMLCh *const /*uri*/, const XMLCh *const /*localname*/, const XMLCh *const qname, const Attributes &attrs)
{
   //if (log_.call()) log_.call(ME, "startElement <" + name + ">");
   AttributeMap tmpMap;
   handler_->startElement(getStringValue(qname), getAttributeMap(tmpMap, attrs));
}

/** Receive notification of the end of a CDATA section. */
void Sax2Parser::endCDATA()
{
   //if (log_.call()) log_.call(ME, string("endCDATA"));
   handler_->endCDATA();
}

/** Receive notification of the start of a CDATA section. */
void Sax2Parser::startCDATA()
{
   //if (log_.call()) log_.call(ME, string("startCDATA"));
   handler_->startCDATA();
}

/** Receive notification of character data inside an element. */
void Sax2Parser::characters(const XMLCh *const chars, const unsigned int length)
{
   //if (log_.call()) log_.call(ME, string("characters"));
   string tmp;
   bool doTrim = false;
   tmp.assign(getStringValue(chars, doTrim), 0, length);
   handler_->characters(tmp);
}

//
// ErrorHandler methods
//

/** Warning. */
void Sax2Parser::warning(const SAXParseException &ex) 
{
   if (log_.call()) log_.call(ME, string("warning"));
   string txt = getLocationString(ex) + "\n";
   handler_->warning(txt);
}
      
      
/** Error. */
void Sax2Parser::error(const SAXParseException &ex) 
{
   if (log_.call()) log_.call(ME, string("error"));
   string txt = getLocationString(ex) + "\n";
   handler_->error(txt);
}


/** Fatal error. */
void Sax2Parser::fatalError(const SAXParseException &ex) 
{
   if (log_.call()) log_.call(ME, string("fatalError"));
   string txt = getLocationString(ex) + "\n";
   handler_->fatalError(txt);
}


/** Returns a string of the location. */
string Sax2Parser::getLocationString(const SAXParseException &ex) 
{
  string systemId = getStringValue(ex.getSystemId());
  string str;
  if (systemId != "") {
    int index = systemId.find_last_of('/');
    if (index != -1) systemId = systemId.substr(index + 1);
    str = systemId + ":";
  }
  return str +lexical_cast<std::string>(ex.getLineNumber()) 
      + ":" + lexical_cast<std::string>(ex.getColumnNumber());
}


/**
 * Compares two strings (where name1 is a Unicode3.0 string!!) for 
 * unsensitive case compare. It returns true if the content of the
 * strings is equal (no matter what the case is). Using this method to
 * compare the strings should be portable to all platforms supported by
 * xerces.
 */
bool Sax2Parser::caseCompare(const XMLCh *name1, const char *name2) 
{
  XMLCh* name1Helper = XMLString::replicate(name1);
  XMLString::upperCase(name1Helper);
  XMLCh* name2Helper = XMLString::transcode(name2);
  XMLString::upperCase(name2Helper);
  bool ret = (XMLString::compareIString(name1Helper, name2Helper) == 0);
  Sax2Parser::releaseXMLCh(&name1Helper);
  Sax2Parser::releaseXMLCh(&name2Helper);
  return ret;
}


/**
 * returns a trimmed value (usually from an attribute) as a string
 */
string Sax2Parser::getStringValue(const XMLCh* const value, bool doTrim) const
{
   char* help = 0;
   try {
      help = XMLString::transcode(value);
      if (help != 0) {
         string ret;
         if (doTrim) ret = StringTrim::trim(help);
         else ret = string(help);
         Sax2Parser::releaseXMLCh(&help);
         return ret;
      }
   }
   catch (...) {
      if (help != 0)
         Sax2Parser::releaseXMLCh(&help);
      cerr << "Caught exception in getStringValue(XMLCh=" << value << ")" << endl;
      // throw;
   }

   return string();
}


AttributeMap& Sax2Parser::getAttributeMap(AttributeMap& attrMap, const Attributes &attrs)
{
   int len = attrs.getLength();
   for (int i = 0; i < len; i++) {
      attrMap[getStringValue(attrs.getQName(i))] = getStringValue(attrs.getValue(i));
   }
   return attrMap;
}

/**
 * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
 * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
 * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
 * method.
 */
bool Sax2Parser::getStringAttr(const Attributes& attrs, const XMLCh* const name, string& value, bool doTrim) const
{
   const XMLCh* tmp = attrs.getValue(name);
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
   Sax2Parser::releaseXMLCh(&help1);
   return true;
}


void Sax2Parser::releaseXMLCh(XMLCh** data)
{
#if XERCES_VERSION_MAJOR > 1 && XERCES_VERSION_MINOR > 1
   XMLString::release(data);
#else
   delete [] *data;
   *data = 0;
#endif
}

void Sax2Parser::releaseXMLCh(char** data)
{
#if XERCES_VERSION_MAJOR > 1 && XERCES_VERSION_MINOR > 1
   XMLString::release(data);
#else
   delete [] *data;
   *data = 0;
#endif
}

#endif

}}}} // namespace

