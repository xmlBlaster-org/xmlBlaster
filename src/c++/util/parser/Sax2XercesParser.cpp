/*-----------------------------------------------------------------------------
Name:      Sax2XercesParser.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_SAX2XERCESPARSER_C
#define _UTIL_PARSER_SAX2XERCESPARSER_C

#if defined(XMLBLASTER_MSXML_PLUGIN)
#  error Implement Microsoft XML parser for /DXMLBLASTER_MSXML_PLUGIN
#else  // XMLBLASTER_XERCES_PLUGIN

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif

#include <util/parser/Sax2XercesParser.h>
#include <xercesc/sax/SAXException.hpp>
#include <xercesc/sax2/SAX2XMLReader.hpp>
#include <xercesc/sax2/XMLReaderFactory.hpp>
#include <xercesc/util/PlatformUtils.hpp>
#include <xercesc/framework/MemBufInputSource.hpp>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <iostream>
//#include <cstdlib> //<stdlib.h>

namespace org { namespace xmlBlaster { namespace util { namespace parser {

using namespace std;

static const int ENCODERBUFFERSIZE = 16*1024;

Sax2Parser::Sax2Parser(org::xmlBlaster::util::Global& global, XmlHandlerBase *handler) : 
    I_Parser(handler), ME("Sax2Parser"), global_(global), log_(global.getLog("org.xmlBlaster.util.xml")),
    xmlBlasterTranscoder_(0)
{
   if (log_.call()) log_.trace(ME, "Creating new Sax2Parser");

   //"UTF-8" is currently not supported with our std::string usage!
   encoding_ = global_.getProperty().getStringProperty("xmlBlaster/encoding", "iso-8859-1");

   XMLTransService::Codes resCode;
   xmlBlasterTranscoder_ = XMLPlatformUtils::fgTransService->makeNewTranscoderFor(encoding_.c_str(), resCode, ENCODERBUFFERSIZE);
   if (resCode != 0/*XMLTransService::Codes::Ok*/) {
      log_.error(ME, "Creation of XMLTranscoder with encoding='" + encoding_ + "' failed with error code " + lexical_cast<string>((int)resCode) +
                     ". Please check your SAX parser setting '-xmlBlaster/encoding'");
      throw XmlBlasterException(USER_CONFIGURATION, ME, "Creation of XMLTranscoder with encoding='" + encoding_ + "' failed with " + lexical_cast<string>((int)resCode));
   }
   else {
      if (log_.trace()) log_.trace(ME, "Created XMLTranscoder res=" + lexical_cast<string>((int)resCode) + " with encoding=" + encoding_);
   }
}

Sax2Parser::~Sax2Parser()
{
   delete xmlBlasterTranscoder_;
}

std::string Sax2Parser::usage() 
{
   std::string text = string("");
   //text += string("\n");
   text += string("\nThe xerces SAX XML parser plugin configuration:");
   text += string("\n   -xmlBlaster/encoding [iso-8859-1]");
   text += string("\n                       The parser encoding to use for xmlBlaster specific QoS and key SAX parsing");
   text += string("\n");
   return text;
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
   //XMLCh* encodingHelper = NULL;
   try {
      parser = XMLReaderFactory::createXMLReader();
      parser->setContentHandler(this);
      parser->setErrorHandler(this);
      parser->setLexicalHandler(this);

      // "UTF-8"  "iso-8859-1"
      //string xmlData1 = string("<?xml version=\"1.0\" encoding=\""+encoding_+"\"?>\n") + xmlData;
      const string &xmlData1 = xmlData;
      //log_.info(ME, "Parsing now: " + xmlData1);
      
      MemBufInputSource inSource((const XMLByte*)xmlData1.c_str(), (unsigned int)(xmlData1.size()), "xmlBlaster", false);
      
      XMLCh tempStr[100];
      XMLString::transcode(encoding_.c_str(), tempStr, 99);
      inSource.setEncoding(tempStr);
      //encodingHelper = XMLString::transcode(encoding.c_str());
      //inSource.setEncoding(encodingHelper);
      //Sax2Parser::releaseXMLCh(&encodingHelper);

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
   catch (XmlBlasterException& ex) {
      throw ex;
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
   catch (const std::exception& err) { // catches all of bad_alloc, bad_cast, runtime_error, ...
      string msg = err.what() + string(": ") + xmlData;
      delete parser;
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::parse", string("std:exception: ") + msg);
   }
   catch (const string& err) {
     string msg = err;
     delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("string exception. message:") + err + ": " + xmlData);
   }
   catch (const char* err) {
     string msg = err;
     delete parser;
     throw XmlBlasterException(INTERNAL_UNKNOWN, ME + "::parse", string("char *exception. message:") + err + ": " + xmlData);
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
    string::size_type index = systemId.find_last_of('/');
    if (index != string::npos) systemId = systemId.substr(index + 1);
    str = systemId + ":";
  }
  string message = Sax2Parser::getStringValue(ex.getMessage(), true);
  return str + "line=" + lexical_cast<std::string>(ex.getLineNumber()) 
      + "/col=" + lexical_cast<std::string>(ex.getColumnNumber()) + " " + message;
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
   /* Works only with US-ASCII:
   char* help = 0;
   try {
      string ret;
      help = XMLString::transcode(value);
      if (help != 0) {
         if (doTrim) ret = StringTrim::trim(help);
         else ret = string(help);
         Sax2Parser::releaseXMLCh(&help);
      }
   }
   catch (...) {
      if (help != 0)
         Sax2Parser::releaseXMLCh(&help);
      cerr << "Caught exception in getStringValue(XMLCh=" << value << ")" << endl;
      // throw;
   }
   */
   if (value == NULL) {
      return "";
   }

/*
Converts from the encoding of the service to the internal XMLCh* encoding.
unsigned int
XMLUTF8Transcoder::transcodeFrom(const  XMLByte* const          srcData
                                , const unsigned int            srcCount
                                ,       XMLCh* const            toFill
                                , const unsigned int            maxChars
                                ,       unsigned int&           bytesEaten
                                ,       unsigned char* const    charSizes)
*/
/*
Converts from the internal XMLCh* encoding to the encoding of the service.
Parameters:
    srcData     the source buffer to be transcoded
    srcCount    number of characters in the source buffer
    toFill      the destination buffer
    maxBytes    the max number of bytes in the destination buffer
    charsEaten  after transcoding, this will hold the number of chars that were processed from the source buffer
    options     options to pass to the transcoder that explain how to respond to an unrepresentable character

Returns:
    Returns the number of chars put into the target buffer 
unsigned int
XMLUTF8Transcoder::transcodeTo( const   XMLCh* const    srcData
                                , const unsigned int    srcCount
                                ,       XMLByte* const  toFill
                                , const unsigned int    maxBytes
                                ,       unsigned int&   charsEaten
                                , const UnRepOpts       options)

*/

   unsigned int charsEatenFromSource = 0;
   unsigned int counter = 0;
   string result;
   unsigned int charsToRead = XMLString::stringLen(value);
   do {
      char resultXMLString_Encoded[ENCODERBUFFERSIZE+4];
      *resultXMLString_Encoded = 0;
      charsEatenFromSource = 0;
      int charsPutToTarget = xmlBlasterTranscoder_->transcodeTo(value+counter,
                                    XMLString::stringLen(value)-counter,
                                    (XMLByte*) resultXMLString_Encoded,
                                    ENCODERBUFFERSIZE,
                                    charsEatenFromSource,
                                    XMLTranscoder::UnRep_Throw );

      /*
      log_.info(ME,"TRANSCODE TMP: got '" + result +
                   "' charsToRead= " + lexical_cast<string>(charsToRead) +
                   "' ENCODERBUFFERSIZE= " + lexical_cast<string>(ENCODERBUFFERSIZE) +
                   " charsEaten=" + lexical_cast<string>(charsEatenFromSource) +
                   " counter=" + lexical_cast<string>(counter) +
                   " charsPutToTarget=" + lexical_cast<string>(charsPutToTarget));
      */
      if (charsEatenFromSource < 1)
         break;
      result += string(resultXMLString_Encoded, charsPutToTarget);
      counter += charsEatenFromSource;
   }
   while(charsEatenFromSource < charsToRead); //charsEatenFromSource== ENCODERBUFFERSIZE || charsPutToTarget == ENCODERBUFFERSIZE);

   //log_.info(ME,"TRANSCODE DONE: got '" + result + "' ENCODERBUFFERSIZE= " + lexical_cast<string>(ENCODERBUFFERSIZE) + " charsEaten=" + lexical_cast<string>(charsEatenFromSource));

   if (doTrim) StringTrim::trim(result);

   return result;
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

#endif  // XMLBLASTER_XERCES_PLUGIN

}}}} // namespace
#endif // _UTIL_PARSER_SAX2XERCESPARSER_C

