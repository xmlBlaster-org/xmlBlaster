/*-----------------------------------------------------------------------------
Name:      Sax2Parser.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_SAX2PARSER_H
#define _UTIL_PARSER_SAX2PARSER_H

#include <util/xmlBlasterDef.h>
#include <util/parser/I_Parser.h>
#include <util/plugin/I_Plugin.h>
#include <string>
#include <util/TransService.hpp>
#include <sax2/DefaultHandler.hpp>
#include <util/XMLString.hpp>
#include <util/StopParseException.h>

#if defined(XERCES_HAS_CPP_NAMESPACE)
        // Since Xerces 2.2 namespace is introduced:
   XERCES_CPP_NAMESPACE_USE
#endif

namespace org { namespace xmlBlaster { namespace util { namespace parser {
    
/**
 * Default xmlBlaster handling of Sax callbacks and errors.<p />
 * You may use this as a base class for your SAX handling.
 * <p>The encoding can be changed with <tt>xmlBlaster/encoding=<enc></tt> where
 * this is typically "iso-8859-1" or "UTF-8"
 * </p>
 * <p>
 * NOTE: Multibyte encoding "UTF-8" is currently not supported for xmlBlaster internal xml key and QoS markup!
 * </p>
 */
class Dll_Export Sax2Parser : public I_Parser, public DefaultHandler,
                              public virtual org::xmlBlaster::util::plugin::I_Plugin
{
   
private:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;
   XMLTranscoder* xmlBlasterTranscoder_;
   std::string encoding_;

   // Private copy ctor and assignement
   Sax2Parser(const Sax2Parser& data);
   Sax2Parser& operator=(const Sax2Parser& data);

public:
   /**
    * Constructs an new object.
    * You need to call the init() method to parse the XML std::string.
    */
   Sax2Parser(org::xmlBlaster::util::Global& global, XmlHandlerBase *handler);

   ~Sax2Parser();
   
   /*
    * This method parses the XML std::string using the SAX parser.
    * @param xmlLiteral The XML std::string
    */
   void init(const std::string &xmlLiteral);
   
   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
    */
   
   void parse(const std::string &xmlData);

protected:

   std::string getLocationString(const SAXParseException &ex);

   AttributeMap& getAttributeMap(AttributeMap& attrMap, const Attributes &attrs);

   /**
    * Compares two std::strings (where name1 is a Unicode3.0 std::string!!) for 
    * unsensitive case compare. It returns true if the content of the
    * std::strings is equal (no matter what the case is). Using this method to
    * compare the std::strings should be portable to all platforms supported by
    * xerces.
    */
   bool caseCompare(const XMLCh *name1, const char *name2) ;

   /**
    * returns a trimmed value (usually from an attribute) as a standard C++ std::string
    */
   std::string getStringValue(const XMLCh* const value, bool doTrim=true) const;

   /**
    * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
    * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
    * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
    * method. If the 'doTrim' argument is set to true, the std::string is trimmed before it is given back.
    */
   bool getStringAttr(const Attributes& attrs, const XMLCh* const name, std::string& value, bool doTrim=true) const;

 public:

   /**
    *  Helper method which encapsulates either the delete[] operator for Xerces-c versions older than 
    * ver. 2.2.0 or which invokes Sax2Parser::releaseXMLCh(XMLCh**) for versions from 2.2.0 and higher. Per
    * default it assumes you have 2.2.0 or higher. If you have an older version please set in your 
    * build.properties or in your system environment the variable OLDXERCES.
    */
   static void releaseXMLCh(XMLCh** data);
 
   /**
    *  Helper method which encapsulates either the delete[] operator for Xerces-c versions older than 
    * ver. 2.2.0 or which invokes Sax2Parser::releaseXMLCh(XMLCh**) for versions from 2.2.0 and higher. Per
    * default it assumes you have 2.2.0 or higher. If you have an older version please set in your 
    * build.properties or in your system environment the variable OLDXERCES.
    */
   static void releaseXMLCh(char** data);

   /** Receive notification of character data inside an element. */
   void characters(const XMLCh *const chars, const unsigned int length);
   
   /** Receive notification of the end of the document. */
   void endDocument();

   /** Receive notification of the end of an element. */
   void   endElement(const XMLCh *const uri, const XMLCh *const localname, const XMLCh *const qname);

   /** Receive notification of the beginning of the document. */
   void   startDocument();

   /** Receive notification of the start of an element. */
   void   startElement(const XMLCh *const uri, const XMLCh *const localname, const XMLCh *const qname, const Attributes &attrs);


   // implementation of the ErrorHandler interface
   /** Receive notification of a recoverable parser error. */
   void   error(const SAXParseException &exc);

   /** Report a fatal XML parsing error. */
   void   fatalError(const SAXParseException &exc);

   /** Receive notification of a parser warning. */
   void   warning(const SAXParseException &exc);

   /** Receive notification of the end of a CDATA section. */
   void   endCDATA();

   /** Receive notification of the end of the DTD declarations. */
   // void   endDTD()

   /** Receive notification of the end of an entity. */
   // void   endEntity(const XMLCh *const name)

   /** Receive notification of the start of a CDATA section. */
   void   startCDATA();

   // these are probably not really needed here ...

   /** Receive notification of the start of the DTD declarations. */
   // void startDTD (const XMLCh *const name, const XMLCh *const publicId, const XMLCh *const systemId) {};

   /** Receive notification of the start of an entity. */
   // void startEntity(const XMLCh *const name) {};

   /** Report an element type declaration. */
   // void elementDecl(const XMLCh *const name, const XMLCh *const model) {};

   /** Report an attribute type declaration. */
   // void attributeDecl (const XMLCh *const eName, const XMLCh *const aName, const XMLCh *const type, const XMLCh *const mode, const XMLCh *const value) {};

   /** Report an internal entity declaration. */
   // void internalEntityDecl (const XMLCh *const name, const XMLCh *const value) {};
   // void externalEntityDecl (const XMLCh *const name, const XMLCh *const publicId, const XMLCh *const systemId) {};

   /** Receive notification of a processing instruction. */
   // void processingInstruction(const XMLCh *const target, const XMLCh *const data) {};

   /** Reset the Docuemnt object on its reuse. */
   // void resetDocument() {};

   /** Receive a Locator object for document events. */
   // void setDocumentLocator(const Locator *const locator) {};

   /** Receive notification of the start of an namespace prefix mapping. */
   // void startPrefixMapping(const XMLCh *const prefix, const XMLCh *const uri) {};

   /** Receive notification of the end of an namespace prefix mapping. */
   // void endPrefixMapping(const XMLCh *const prefix) {};

   /** Receive notification of a skipped entity. */
   // void skippedEntity(const XMLCh *const name) {};

   // implementation of the EntityResolver interface.
   /** Resolve an external entity. */
   // InputSource* resolveEntity(const XMLCh *const publicId, const XMLCh *const systemId) { return NULL; };

   /** Reset the Error handler object on its reuse. */
   // void resetErrors() {};

   // implementation of DTDHandler interface.
   /** Receive notification of a notation declaration. */
   // void notationDecl(const XMLCh *const name, const XMLCh *const publicId, const XMLCh *const systemId) {};

   /** Reset the DTD object on its reuse. */
   // void resetDocType() {};

   /** Receive notification of an unparsed entity declaration. */
   // void unparsedEntityDecl(const XMLCh *const name, const XMLCh *const publicId, const XMLCh *const systemId, const XMLCh *const notationName) {};

   //implementation of LexicalHandler interface.
   /** Receive notification of comments. */
   // void comment(const XMLCh *const chars, const unsigned int length) {};

   /** Receive notification of ignorable whitespace in element content. */
   // void ignorableWhitespace(const XMLCh *const chars, const unsigned int length);

   /**
    * Get the name of the plugin. 
    * @return "XERCES"
    * @enforcedBy I_Plugin
    */
   std::string getType() { static std::string type = "XERCES"; return type; }

   /**
    * Get the version of the plugin. 
    * @return "1.0"
    * @enforcedBy I_Plugin
    */
   std::string getVersion() { static std::string version = "1.0"; return version; }

   /**
    * Command line usage.
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" prefix there.
    */
   static std::string usage();
};
}}}} // namespace

#endif
