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

#include <stdlib.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
    
/**
 * Default xmlBlaster handling of Sax callbacks and errors.<p />
 * You may use this as a base class for your SAX handling.
 */
class Dll_Export SaxHandlerBase : public DocumentHandler, public ErrorHandler,
                                  public DTDHandler {
   
private:
   
   string me() {
      return "SaxHandlerBase";
   }
   
protected:
   string            character_;
   StringTrim<char>  charTrimmer_; // wrappers for the java String.trim
   StringTrim<XMLCh> xmlChTrimmer_;
   /**
    * The original XML string in ASCII representation, for example:
    * <code>   &lt;qos>&lt;/qos>"</code>
    */
   string  xmlLiteral_;
   Global& global_;
   Log&    log_;

public:
   /**
    * Constructs an new object.
    * You need to call the init() method to parse the XML string.
    */
     SaxHandlerBase(int args=0, const char * const argc[]=0);
   SaxHandlerBase(Global& global);

   
   /*
    * This method parses the XML string using the SAX parser.
    * @param xmlLiteral The XML string
    */
   void init(const string &xmlLiteral);
   

private:      
   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
    */
   
   void parse(const string &xmlData);

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
   /** Returns a string of the location. */
   string getLocationString(const SAXParseException &ex) ;

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
   bool caseCompare(const XMLCh *name1, const char *name2) ;

   /**
    * Gets the start element parameters, reads them and builds a string.
    */
    string getStartElementAsString(const XMLCh* const name, AttributeList& attrs) const;

    /**
     * returns a value (usually from an attribute) as a string
     */
    string getStringValue(const XMLCh* const value);

    /**
     * returns a value (usually from an attribute) as an integer
     */
    int getIntValue(const XMLCh* const value);

    /**
     * returns a value (usually from an attribute) as a long
     */
    long getLongValue(const XMLCh* const value);

    /**
     * returns a value (usually from an attribute) as a Timestamp
     */
     Timestamp getTimestampValue(const XMLCh* const value);

    /**
     * returns a value (usually from an attribute) as a bool
     */
     bool getBoolValue(const XMLCh* const value);

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method. If the 'doTrim' argument is set to true, the string is trimmed before it is given back.
      */
     bool getStringAttr(const AttributeList& list, const XMLCh* const name, string& value, bool doTrim=true);

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getIntAttr(const AttributeList& list, const XMLCh* const name, int& value);

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getLongAttr(const AttributeList& list, const XMLCh* const name, long& value);

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getTimestampAttr(const AttributeList& list, const XMLCh* const name, Timestamp& value);

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getBoolAttr(const AttributeList& list, const XMLCh* const name, bool& value);

     /**
      * returns the input string trimmed
      */
     string stringTrim(const string& str) const;

   };
}}} // namespace

#endif
