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

ParserFactory& ParserFactory::getFactory(Global& global)
{
   if (factory_ == NULL) {
      factory_ = new ParserFactory(global);
      org::xmlBlaster::util::Object_Lifetime_Manager::instance()->manage_object("XB_ParserFactory", factory_);  // if not pre-allocated.
   }
   return *factory_;
}

ParserFactory::ParserFactory(Global& global) :
     ME("ParserFactory"), 
     global_(global), 
     log_(global_.getLog("xml"))
{
   try {
      if (!global.isUsingXerces()) {
         XMLPlatformUtils::Initialize();
         global.setUsingXerces();
      }   
   }
   catch (const XMLException& toCatch) {
      char* message = XMLString::transcode(toCatch.getMessage());
      std::string txt = std::string("Constructor - error during initialization. Exception message is: ") + std::string(message);
      log_.error(ME, txt);
      XMLString::release(&message);
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
}

ParserFactory::ParserFactory(const ParserFactory& factory) :
               ME(factory.ME),
               global_(factory.global_),
               log_(factory.log_)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

ParserFactory& ParserFactory::operator =(const ParserFactory&)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

ParserFactory::~ParserFactory()
{
   if (global_.isUsingXerces()) {
      XMLPlatformUtils::Terminate();
      global_.setUsingXerces(false);
   }
   if (log_.call()) log_.call(ME, "~ParserFactory()");
}

/**
   * Creates a parser implementation. It is the responsibility of the user to delete the I_Parser
   * object once it is not needed anymore.
   */
I_Parser* ParserFactory::createParser(XmlHandlerBase *handler)
{
   try {
      return new Sax2Parser(global_, handler);
   }
   catch (const XMLException& toCatch) {
      char* message = XMLString::transcode(toCatch.getMessage());
      std::string txt = std::string("createParser: error during SAX parser initialization. Exception message is: ") + std::string(message);
      log_.error(ME, txt);
      XMLString::release(&message);
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
}


}}}} // namespace

#endif
