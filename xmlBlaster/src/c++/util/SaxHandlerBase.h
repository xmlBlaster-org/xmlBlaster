/*-----------------------------------------------------------------------------
Name:      SaxHandlerBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_SAXHANDLERBASE_H
#define _UTIL_SAXHANDLERBASE_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/lexical_cast.h>
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

#include <stdlib.h>



#if defined(XERCES_HAS_CPP_NAMESPACE)
        // Since Xerces 2.2 namespace is introduced:
   XERCES_CPP_NAMESPACE_USE
#endif

namespace org { namespace xmlBlaster {
namespace util {
    
/**
 * Default xmlBlaster handling of Sax callbacks and errors.<p />
 * You may use this as a base class for your SAX handling.
 */
class Dll_Export SaxHandlerBase : public DocumentHandler, public ErrorHandler,
                                  public DTDHandler {
   
private:
   
   std::string me() {
      return "SaxHandlerBase";
   }
   
protected:
   std::string            character_;
   /**
    * The original XML std::string in ASCII representation, for example:
    * <code>   &lt;qos>&lt;/qos>"</code>
    */
   std::string  xmlLiteral_;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::Log&    log_;

public:
   /**
    * Constructs an new object.
    * You need to call the init() method to parse the XML std::string.
    */
    // SaxHandlerBase(int args=0, const char * const argc[]=0);
   SaxHandlerBase(org::xmlBlaster::util::Global& global);

   
   /*
    * This method parses the XML std::string using the SAX parser.
    * @param xmlLiteral The XML std::string
    */
   void init(const std::string &xmlLiteral);
   

private:      
   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
    */
   
   void parse(const std::string &xmlData);

public:      
   /**
    * @return returns the literal xml std::string
    */
   std::string toString() {
      return xmlLiteral_;
   }


   /**
    * @return returns the literal xml std::string
    */
   std::string toXml() {
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
                   const unsigned int length);


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
   void warning(const SAXParseException &ex) ;
   
   
   /** Error. */
   void error(const SAXParseException &ex) ;


   /** Fatal error. */
   void fatalError(const SAXParseException &ex) ;

   void notationDecl(const XMLCh* const name, const XMLCh* const publicId, 
                     const XMLCh* const systemId) ;
   

   /** Fatal error. */
   void unparsedEntityDecl(const XMLCh* const name, 
                           const XMLCh* const publicId, 
                           const XMLCh* const systemId, 
                           const XMLCh* const notationName) ;

   
private:
   /** Returns a std::string of the location. */
   std::string getLocationString(const SAXParseException &ex) ;

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
    * Compares two std::strings (where name1 is a Unicode3.0 std::string!!) for 
    * unsensitive case compare. It returns true if the content of the
    * std::strings is equal (no matter what the case is). Using this method to
    * compare the std::strings should be portable to all platforms supported by
    * xerces.
    */
   bool caseCompare(const XMLCh *name1, const char *name2) ;

   /**
    * Gets the start element parameters, reads them and builds a std::string.
    */
    std::string getStartElementAsString(const XMLCh* const name, AttributeList& attrs) const;

    /**
     * returns a trimmed value (usually from an attribute) as a standard C++ std::string
     */
    std::string getStringValue(const XMLCh* const value) const;

    /**
     * returns a value (usually from an attribute) as an integer
     */
    int getIntValue(const XMLCh* const value) const;

    /**
     * returns a value (usually from an attribute) as a long
     */
    long getLongValue(const XMLCh* const value) const;

    /**
     * returns a value (usually from an attribute) as a Timestamp
     */
     Timestamp getTimestampValue(const XMLCh* const value) const;

    /**
     * returns a value (usually from an attribute) as a bool
     */
     bool getBoolValue(const XMLCh* const value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method. If the 'doTrim' argument is set to true, the std::string is trimmed before it is given back.
      */
     bool getStringAttr(const AttributeList& list, const XMLCh* const name, std::string& value, bool doTrim=true) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getIntAttr(const AttributeList& list, const XMLCh* const name, int& value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getLongAttr(const AttributeList& list, const XMLCh* const name, long& value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getTimestampAttr(const AttributeList& list, const XMLCh* const name, Timestamp& value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getBoolAttr(const AttributeList& list, const XMLCh* const name, bool& value) const;

public:
     /**
      *  Helper method which encapsulates either the delete[] operator for Xerces-c versions older than 
      * ver. 2.2.0 or which invokes SaxHandlerBase::releaseXMLCh(XMLCh**) for versions from 2.2.0 and higher. Per
      * default it assumes you have 2.2.0 or higher. If you have an older version please set in your 
      * build.properties or in your system environment the variable OLDXERCES.
      */
     static void releaseXMLCh(XMLCh** data);

     /**
      *  Helper method which encapsulates either the delete[] operator for Xerces-c versions older than 
      * ver. 2.2.0 or which invokes SaxHandlerBase::releaseXMLCh(XMLCh**) for versions from 2.2.0 and higher. Per
      * default it assumes you have 2.2.0 or higher. If you have an older version please set in your 
      * build.properties or in your system environment the variable OLDXERCES.
      */
     static void releaseXMLCh(char** data);
   };
}}} // namespace

#endif
