/*-----------------------------------------------------------------------------
Name:      I_Parser.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The abstraction parser for xml literals
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_I_PARSER_H
#define _UTIL_PARSER_I_PARSER_H

#include <util/xmlBlasterDef.h>
#include <util/parser/XmlHandlerBase.h>


namespace org { namespace xmlBlaster { namespace util { namespace parser {
    
/**
 * Abstraction for the xml handling<p />
 * You may use this as the interface to extend in your specific XML handling (example SAX2).
 */
class Dll_Export I_Parser {

protected:
   
   XmlHandlerBase *handler_;

   I_Parser(XmlHandlerBase *handler)
   {
      handler_ = handler;
   }

public:
   virtual ~I_Parser() {}

   /**
    * Does the actual parsing
    * @param xmlData Quality of service in XML notation
    */
   virtual void parse(const std::string &xmlData) = 0;

   };
}}}} // namespace

#endif
