/*-----------------------------------------------------------------------------
Name:      ParserFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The abstraction parser for xml literals
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_PARSERFACTORY_C
#define _UTIL_PARSER_PARSERFACTORY_C

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif

#include <util/PlatformUtils.hpp>
#include <util/parser/ParserFactory.h>
#include <util/parser/Sax2Parser.h>


 
namespace org { namespace xmlBlaster { namespace util { namespace parser {
    
   /**
    * Creates a parser implementation. It is the responsability of the user to delete the I_Parser
    * object once it is not needed anymore.
    */
I_Parser* ParserFactory::createParser(org::xmlBlaster::util::Global &global, XmlHandlerBase *handler)
{
   try {
      if (!global.isUsingXerces()) {
         XMLPlatformUtils::Initialize();
         global.setUsingXerces();
      }   
      return new Sax2Parser(global, handler);
   }
   catch (const XMLException& toCatch) {
      char* message = XMLString::transcode(toCatch.getMessage());
      std::string txt = std::string("createParser: error during initialization. Exception message is: ") + std::string(message);
      global.getLog("xml").error("ParserFactory", txt);
      XMLString::release(&message);
      return NULL;
   }
}


}}}} // namespace

#endif
