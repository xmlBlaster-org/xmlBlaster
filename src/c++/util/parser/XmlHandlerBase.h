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
#include <util/I_Log.h>
#include <util/Timestamp.h>
#include <string>
#include <map>
#include <util/StringTrim.h>
#include <util/thread/ThreadImpl.h>

namespace org { namespace xmlBlaster { namespace util { namespace parser {
    
typedef std::map<std::string, std::string> AttributeMap;

/**
 * Abstraction for the xml handling<p />
 * You may use this as the interface to extend in your specific XML handling (example SAX2).
 */
class Dll_Export XmlHandlerBase {

private:
   std::string ME; // SaxHandlerBase

   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
    */
   void parse(const std::string &xmlData);

protected:
   std::string character_;
   std::string attributeCharacter_;
   bool inAttribute_; // Nested <attribute>WITH OWN VALUES (attributeCharacter_)</attribute>
   bool doTrimStrings_;
   org::xmlBlaster::util::StringTrim trimmer_;
   std::string locale_;

   std::string getStartElementAsString(const std::string &name, const AttributeMap &attrMap);

   /**
    * The original XML std::string in ASCII representation, for example:
    * <code>   &lt;qos>&lt;/qos>"</code>
    */
   std::string  xmlLiteral_;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   org::xmlBlaster::util::thread::Mutex invocationMutex_;

public:
   /**
    * Constructs an new object.
    * You need to call the init() method to parse the XML std::string.
    */
   XmlHandlerBase(org::xmlBlaster::util::Global& global);

   virtual ~XmlHandlerBase() { };

   /*
    * This method parses the XML std::string using the SAX parser.
    * @param xmlLiteral The XML std::string
    */
   void init(const std::string &xmlLiteral);

   /**
    * Get the locale (the xerces default is "en_US"). 
    * Others are "de_DE.iso-8859-1" or "en_US.UTF-8"
    */
   std::string getLocale();

   /**
    * @return returns the literal xml std::string
    */
   std::string toString() 
   {
      return xmlLiteral_;
   }

   /**
    * @return returns the literal xml std::string
    */
   std::string toXml() 
   {
      return xmlLiteral_;
   }

   /** Start document */
   virtual void startDocument();

   /** Start element. */
   virtual void startElement(const std::string &name, const AttributeMap& attrs);

   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>. This method is different from the java version
    * since the c++ parser always starts at the first character, so you
    * don't specify start.
    */
   virtual void characters(const std::string &ch);

   /** End element. */
   virtual void endElement(const std::string &name);

   /** End document. */
   virtual void endDocument();

   /** Warning. */
   virtual void warning(const std::string &exTxt);
   
   /** Error. */
   virtual void error(const std::string &exTxt);

   /** Fatal error. */
   virtual void fatalError(const std::string &exTxt);

   virtual void endCDATA();
   
   virtual void startCDATA();
   
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
   bool getStringAttr(const AttributeMap &attrs, const std::string &name, std::string& value, bool doTrim=true) const;

   /**
    * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
    * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
    * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
    * method.
    */
   bool getIntAttr(const AttributeMap &attrs, const std::string &name, int& value) const;

   /**
    * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
    * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
    * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
    * method.
    */
   bool getLongAttr(const AttributeMap &attrs, const std::string &name, long& value) const;

   /**
    * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
    * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
    * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
    * method.
    */
   bool getTimestampAttr(const AttributeMap &attrs, const std::string &name, Timestamp& value) const;

   /**
    * gets the attribute specified by 'name' in the attribute list specified by 'list'. The result is put in 
    * the 'value' argument which is passed by reference. It returns 'true' if the attribute was found in the
    * specified attribute list or 'false' if it was not. In the later case, the value is untouched by this 
    * method.
    */
   bool getBoolAttr(const AttributeMap &attrs, const std::string &name, bool& value) const;

};
}}}} // namespace

#endif
