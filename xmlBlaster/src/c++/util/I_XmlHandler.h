/*-----------------------------------------------------------------------------
Name:      XmlHandlerBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default handling of Sax callbacks
-----------------------------------------------------------------------------*/

#ifndef _UTIL_XMLHANDLERBASE_H
#define _UTIL_XMLHANDLERBASE_H

#include <util/xmlBlasterDef.h>
#include <util/XmlBlasterException.h>
#include <util/Timestamp.h>
#include <string>
#include <map>

namespace org { namespace xmlBlaster {
namespace util {
    
typedef std::map<std::string, std::string> Attributes;

/**
 * Abstraction for the xml handling<p />
 * You may use this as the interface to extend in your specific XML handling (example SAX2).
 */
class Dll_Export XmlHandlerBase {
   
public:

   /*
    * This method parses the XML std::string using the SAX parser.
    * @param xmlLiteral The XML std::string
    */
   virtual void init(const std::string &xmlLiteral) = 0;

   /**
    * @return returns the literal xml std::string
    */
   virtual std::string toString() = 0;

   /**
    * @return returns the literal xml std::string
    */
   virtual std::string toXml() = 0;

   /** Start document */
   virtual void startDocument() = 0;

   /** Start element. */
   virtual void startElement(const std::string &name, Attributes& attrs) = 0;

   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>. This method is different from the java version
    * since the c++ parser always starts at the first character, so you
    * don't specify start.
    */
   virtual void characters(const std::string &ch) = 0;

   /** Ignorable whitespace. */
   virtual void ignorableWhitespace(const std::string &ch) = 0;

   /** End element. */
   void endElement(const std::string &name) = 0;

   /** End document. */
   virtual void endDocument() = 0;

   /** Warning. */
   virtual void warning(const XmlBlasterException &ex) = 0;
   
   /** Error. */
   virtual void error(const XmlBlasterException &ex) = 0;

   /** Fatal error. */
   virtual void fatalError(const XmlBlasterException &ex) = 0;

   void endCDATA() = 0;
   
   void startCDATA() = 0;
   
    /**
     * returns a value (usually from an attribute) as an integer
     */
    int getIntValue(const std::string &value) const;

    /**
     * returns a value (usually from an attribute) as a long
     */
    long getLongValue(const std::string &value) const;

    /**
     * returns a value (usually from an attribute) as a Timestamp
     */
     Timestamp getTimestampValue(const std::string &value) const;

    /**
     * returns a value (usually from an attribute) as a bool
     */
     bool getBoolValue(const std::string &value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method. If the 'doTrim' argument is set to true, the std::string is trimmed before it is given back.
      */
     bool getStringAttr(const Attributes &attrs, const std::string &name, std::string& value, bool doTrim=true) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getIntAttr(const Attributes &attrs, const std::string &name, int& value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getLongAttr(const Attributes &attrs, const std::string &name, long& value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getTimestampAttr(const Attributes &attrs, const std::string &name, Timestamp& value) const;

     /**
      * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
      * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
      * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
      * method.
      */
     bool getBoolAttr(const Attributes &attrs, const std::string &name, bool& value) const;

   };
}}} // namespace

#endif
