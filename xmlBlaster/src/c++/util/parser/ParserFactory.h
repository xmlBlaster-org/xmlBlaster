/*-----------------------------------------------------------------------------
Name:      ParserFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The abstraction parser for xml literals
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_PARSERFACTORY_H
#define _UTIL_PARSER_PARSERFACTORY_H

#include <util/xmlBlasterDef.h>
#include <util/parser/I_Parser.h>
#include <util/Global.h>
#include <util/parser/XmlHandlerBase.h>

namespace org { namespace xmlBlaster { namespace util { namespace parser {
    
/**
 * Abstraction for the xml handling<p />
 * You may use this as the interface to extend in your specific XML handling (example SAX2).
 */
class Dll_Export ParserFactory {

public:
   
   /**
    * Creates a parser implementation. It is the responsability of the user to delete the I_Parser
    * object once it is not needed anymore.
    */
   static I_Parser* createParser(org::xmlBlaster::util::Global &global, XmlHandlerBase *handler);
};

}}}} // namespace

#endif
