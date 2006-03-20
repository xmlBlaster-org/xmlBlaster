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
 * Abstraction for the xml handling. 
 * <p />
 * You may use this as the interface to extend in your specific XML handling (example SAX2).
 * <p />
 * It is a singleton class and has for
 * that reason private constructors, destructor and assignment operator. 
 * To get a reference to the singleton instance you must invoke getFactory(...).
 */
class Dll_Export ParserFactory {
   friend class Sax2Parser; // g++ 2.95.3 warning: `class org::xmlBlaster::util::parser::ParserFactory' only defines private constructors and has no friends

   private:
   const std::string ME;
   bool isInitialized_;
   std::string locale_;

   static ParserFactory* factory_;
   
   ParserFactory();
   ParserFactory(const ParserFactory& factory);
   ParserFactory& operator =(const ParserFactory& factory);

   public:
   ~ParserFactory();

   /**
    * Static access to the factory. 
    * @exception XmlBlasterException
    */
   static ParserFactory& getFactory();

   /**
    * The locale used to initialize the parser. 
    * Xerces defaults to "en_US", configurable with
    * for example <tt>-xmlBlaster/locale</tt> "de_DE.iso-8859-1" or "en_US.UTF-8"
    */
   std::string getLocale(org::xmlBlaster::util::Global& global);

   /**
    * Initialize the used parser. 
	 * The locale is set if the Initialize() is invoked for the very first time,
	 * to ensure that each and every message loaders, in the process space, share the same locale.
	 *
    * All subsequent invocations of Initialize(), with a different locale,
	 * have no effect on the message loaders, either instantiated, or to be instantiated. 
	 * @see http://xml.apache.org/xerces-c/apiDocs/classXMLPlatformUtils.html#z489_0
    */
   void initialize(org::xmlBlaster::util::Global& global);

   /**
    * Creates a parser implementation. 
    * <p />
    * It is the responsibility of the user to delete the I_Parser
    * object once it is not needed anymore.
    * @exception XmlBlasterException
    */
   I_Parser* createParser(org::xmlBlaster::util::Global& global, XmlHandlerBase *handler);
};

}}}} // namespace

#endif
