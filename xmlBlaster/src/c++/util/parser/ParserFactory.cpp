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
#include <util/PanicHandler.hpp>
#include <util/parser/ParserFactory.h>
#include <util/parser/Sax2Parser.h>


 
namespace org { namespace xmlBlaster { namespace util { namespace parser {

using namespace org::xmlBlaster::util;
//using namespace std;
    
ParserFactory* ParserFactory::factory_ = NULL;


/**
 * Xerces panic handler
 */
class  XmlBlasterPanicHandler : public PanicHandler
{
 public:
 
   XmlBlasterPanicHandler(){};
 
   virtual ~XmlBlasterPanicHandler(){};
 
    /*
    Receive notification of panic.
    This method is called when an unrecoverable error has occurred in the Xerces library.
    This method must not return normally, otherwise, the results are undefined.
    Ways of handling this call could include throwing an exception or exiting the process.
    Once this method has been called, the results of calling any other Xerces API, or using any existing Xerces objects are undefined.
    */
   virtual void panic(const PanicHandler::PanicReasons reason) {

      std::string txt = std::string("Got panic reason '") + lexical_cast<std::string>((int)reason) + "': " + 
                    PanicHandler::getPanicReasonString(reason);

      std::cerr << "Xerces::PanicHandler: " << txt << std::endl;
      throw XmlBlasterException(INTERNAL_UNKNOWN, "Xerces::PanicHandler", txt);
   }
 
 private:
 
     /* Unimplemented Constructors and operators */
     /* Copy constructor */
     XmlBlasterPanicHandler(const PanicHandler&);
     
     XmlBlasterPanicHandler& operator=(const XmlBlasterPanicHandler&);
 
};

static XmlBlasterPanicHandler xmlBlasterPanicHandler;


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
      //std::cerr << "ParserFactory destructor" << std::endl;
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
         std::cerr << "initializing xerces with '" << handler->getLocale() << "'" << std::endl;
         // "en_US" is default inside xerces
         XMLPlatformUtils::Initialize(handler->getLocale().c_str(), 0, &xmlBlasterPanicHandler, 0);
         isUsingXerces_ = true;
      }
   }
   catch (const XMLException& e) {
      char* message = XMLString::transcode(e.getMessage());
      std::string txt = std::string("XMLPlatformUtils::Initialize() - XMLException during initialization. Exception message is: ") + std::string(message);
      Sax2Parser::releaseXMLCh(&message);
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
   catch (const std::exception& e) {
      std::string txt = std::string("XMLPlatformUtils::Initialize() - std::exception during initialization. Exception message is: ") + std::string(e.what());
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }

   try {
      return new Sax2Parser(global, handler);
   }
   catch (const XmlBlasterException& e) {
      throw e;
   }
   catch (const XMLException& e) {
      char* message = XMLString::transcode(e.getMessage());
      std::string txt = std::string("Sax2Parser(): error during SAX parser initialization. Exception message is: ") + std::string(message);
      Sax2Parser::releaseXMLCh(&message);
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
   catch (const std::exception& e) {
      std::string txt = std::string("Sax2Parser() - std::exception during initialization. Exception message is: ") + std::string(e.what());
      throw util::XmlBlasterException(INTERNAL_UNKNOWN, ME, txt);
   }
}


}}}} // namespace

#endif
