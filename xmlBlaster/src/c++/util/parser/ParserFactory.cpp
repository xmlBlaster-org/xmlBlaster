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

#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/PlatformUtils.hpp>
#include <util/parser/ParserFactory.h>
#include <util/parser/Sax2Parser.h>


 
namespace org { namespace xmlBlaster { namespace util { namespace parser {

using namespace org::xmlBlaster::util;
    
ParserFactory* ParserFactory::factory_ = NULL;

ParserFactory& ParserFactory::getFactory()
{
   if (factory_ == NULL) {
      factory_ = new ParserFactory();
      org::xmlBlaster::util::Object_Lifetime_Manager::instance()->manage_object("XB_ParserFactory", factory_);  // if not pre-allocated.
   }
   return *factory_;
}

ParserFactory::ParserFactory() :
     ME("ParserFactory")
{
   isUsingXerces_ = false;
}

ParserFactory::ParserFactory(const ParserFactory& factory) :
     ME(factory.ME)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

ParserFactory& ParserFactory::operator =(const ParserFactory&)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

ParserFactory::~ParserFactory()
{
   if (isUsingXerces_) {
      std::cerr << "ParserFactory destructor" << std::endl;
      XMLPlatformUtils::Terminate();
   }
}

/**
   * Creates a parser implementation. It is the responsibility of the user to delete the I_Parser
   * object once it is not needed anymore.
   */
I_Parser* ParserFactory::createParser(org::xmlBlaster::util::Global& global, XmlHandlerBase *handler)
{
   try {
      if (!isUsingXerces_) {
         std::cerr << "initializing xerces" << std::endl;
         XMLPlatformUtils::Initialize();
         isUsingXerces_ = true;
      }
   }
   catch (const XMLException& toCatch) {
      char* message = XMLString::transcode(toCatch.getMessage());
      std::string txt = std::string("Constructor - error during initialization. Exception message is: ") + std::string(message);
      Sax2Parser::releaseXMLCh(&message);
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
   try {
      return new Sax2Parser(global, handler);
   }
   catch (const XMLException& toCatch) {
      char* message = XMLString::transcode(toCatch.getMessage());
      std::string txt = std::string("createParser: error during SAX parser initialization. Exception message is: ") + std::string(message);
      Sax2Parser::releaseXMLCh(&message);
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
}


}}}} // namespace

#endif
