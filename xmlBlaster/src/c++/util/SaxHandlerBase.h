/*-----------------------------------------------------------------------------
Name:      SaxHandlerBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_SAXHANDLERBASE_H
#define _UTIL_SAXHANDLERBASE_H

#include <string>
#include <boost/lexical_cast.hpp>
#include <sax/DocumentHandler.hpp> // xerces xml parser (www.apache.org)
#include <sax/ErrorHandler.hpp>
#include <sax/DTDHandler.hpp>
#include <sax/SAXParseException.hpp>
#include <sax/Locator.hpp>
#include <framework/MemBufInputSource.hpp>
#include <util/XMLString.hpp>
#include <parsers/SAXParser.hpp>
#include <util/Log.h>
#include <util/StopParseException.h>
#include <util/StringTrim.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
    
   /**
    * Default xmlBlaster handling of Sax callbacks and errors.<p />
    * You may use this as a base class for your SAX handling.
    */
   class SaxHandlerBase : public DocumentHandler, public ErrorHandler,
                          public DTDHandler {
      
   private:
      
      string me() {
         return "SaxHandlerBase";
      }
      
   protected:
      string            character_;
      Log               log_;
      StringTrim<char>  charTrimmer_; // wrappers for the java String.trim
      StringTrim<XMLCh> xmlChTrimmer_;
      /**
       * The original XML string in ASCII representation, for example:
       * <code>   &lt;qos>&lt;/qos>"</code>
       */
      string xmlLiteral_;
      
   public:
      /**
       * Constructs an new object.
       * You need to call the init() method to parse the XML string.
       */
      SaxHandlerBase(int args=0, char *argc[]=0) : log_(args, argc),
         charTrimmer_(), xmlChTrimmer_() {
         if (log_.CALL) log_.trace(me(), "Creating new SaxHandlerBase");
      }

      
      /*
       * This method parses the XML string using the SAX parser.
       * @param xmlLiteral The XML string
       */
      void init(const string &xmlLiteral) {
         xmlLiteral_ = xmlLiteral;
         if (xmlLiteral_.size() > 0) {
            parse(xmlLiteral_);
         }
      }
      

   private:      
      /**
       * Does the actual parsing
       * @param xmlData Quality of service in XML notation
       */
      
      void parse(const string &xmlData) {
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

   public:      
      /**
       * @return returns the literal xml string
       */
      string toString() {
         return xmlLiteral_;
      }


      /**
       * @return returns the literal xml string
       */
      string toXml() {
         return xmlLiteral_;
      }


      /** Processing instruction. (do we really need this ?) */
      void processingInstruction ( const XMLCh* const /*target*/, 
                                   const XMLCh* const /*data*/ ) {
      }


      /** Start document. (and this ?)*/
      void startDocument() {
      }


      /** Start element. */
      
      void startElement(const XMLCh* const /*name*/, AttributeList& /*attrs*/) {
         log_.warn(me(),"Please provide your startElement() impl.");
      }

      /**
       * This characters emulates the java version but keep in mind that it is
       * not the virtual method inherited from DocumentHandler !!
       */
      void characters(const XMLCh* const ch, const unsigned int start,
                      const unsigned int length) {
         char *chHelper = XMLString::transcode(ch);
         character_.assign(string(chHelper), start, length);
         delete chHelper;
      }

      /**
       * Characters.
       * The text between two tags, in the following example 'Hello':
       * <key>Hello</key>. This method is different from the java version
       * since the c++ parser always starts at the first character, so you
       * don't specify start.
       */
      void characters(const XMLCh* const ch, const unsigned int length) {
         characters(ch, 0, length);
      }

      /** Ignorable whitespace. */
      void ignorableWhitespace(const XMLCh* const /*ch*/, 
                               const unsigned int /*length*/) {
      }
      

      /** End element. */
      void endElement(const XMLCh* const /*name*/) {
         log_.warn(me(),"Please provide your startElement() impl.");
      }
      

      /** End document. */
      void endDocument() {
      }


      //
      // ErrorHandler methods
      //
      
      /** Warning. */
      void warning(const SAXParseException &ex) {
         string txt = getLocationString(ex);
         txt += string("\n") + xmlLiteral_;
         log_.warn(me(), txt);
      }
      
      
      /** Error. */
      void error(const SAXParseException &ex) {
         string txt = getLocationString(ex);
         txt += string("\n") + xmlLiteral_;
         log_.warn(me(), txt);
      }


      /** Fatal error. */
      void fatalError(const SAXParseException &ex) {
         string txt = getLocationString(ex);
         txt += string("\n") + xmlLiteral_;
         log_.warn(me(), txt);
         throw ex;
      }

      void notationDecl(const XMLCh* const name, const XMLCh* const publicId, 
                        const XMLCh* const systemId) {
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
      void unparsedEntityDecl(const XMLCh* const name, 
                              const XMLCh* const publicId, 
                              const XMLCh* const systemId, 
                              const XMLCh* const notationName) {

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

      
   private:
      /** Returns a string of the location. */
      string getLocationString(const SAXParseException &ex) {
         string str;
         char*  systemIdHelper = XMLString::transcode(ex.getSystemId()); 
         string systemId       = systemIdHelper;
         delete systemIdHelper;
         if (systemId != "") {
            int index = systemId.find_last_of('/');
            if (index != -1) systemId = systemId.substr(index + 1);
            str += systemId;
         }
         return str + ":" + boost::lexical_cast<string>(ex.getLineNumber()) + ":" + boost::lexical_cast<string>(ex.getColumnNumber());
      }

      /**
       * These overwrite some virtual functions 
       */
   public:
      void resetErrors() {
      }
      
      void resetDocType() {
      }
      
      void resetDocument() {
      }

      void setDocumentLocator(const Locator* const ) {
      }
 

   protected:
   
      /**
       * Compares two strings (where name1 is a Unicode3.0 string!!) for 
       * unsensitive case compare. It returns true if the content of the
       * strings is equal (no matter what the case is). Using this method to
       * compare the strings should be portable to all platforms supported by
       * xerces.
       */
      bool caseCompare(const XMLCh *name1, const char *name2) {
         XMLCh* name1Helper = XMLString::replicate(name1);
         XMLString::upperCase(name1Helper);
         XMLCh* name2Helper = XMLString::transcode(name2);
         XMLString::upperCase(name2Helper);
         bool ret = (XMLString::compareIString(name1Helper, name2Helper) == 0);
         delete name1Helper;
         delete name2Helper;
         return ret;
      }
   };
}}} // namespace

#endif
