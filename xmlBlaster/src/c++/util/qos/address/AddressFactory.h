/*------------------------------------------------------------------------------
Name:      AddressFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory Object for parsing org::xmlBlaster::util::qos::address::Address objects.
Author:    laghi
------------------------------------------------------------------------------*/

/**
 * Factory for the creation (SAX parsing from std::string) of org::xmlBlaster::util::qos::address::AddressBase objects.
 * The created org::xmlBlaster::util::qos::address::AddressBase objects can easely be converted to org::xmlBlaster::util::qos::address::Address and
 * org::xmlBlaster::util::qos::address::CallbackAddress objects.
 * See classes of the object it creates.
 * @see org::xmlBlaster::util::qos::address::AddressBase
 * @see org::xmlBlaster::util::qos::address::Address
 * @see org::xmlBlaster::util::qos::address::CallbackAddress
 */
#ifndef _UTIL_CFG_ADDRESSFACTORY_H
#define _UTIL_CFG_ADDRESSFACTORY_H

#include <util/SaxHandlerBase.h>
#include <util/qos/address/AddressBase.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

class Dll_Export AddressFactory : public SaxHandlerBase
{
private:
   const std::string ME;
   org::xmlBlaster::util::qos::address::AddressBase* address_;

public:
   AddressFactory(org::xmlBlaster::util::Global& global);

   void reset(AddressBase& address);

   org::xmlBlaster::util::qos::address::AddressBase& getAddress();

   /**
    * Called for SAX callback start tag
    */
   // void startElement(const std::string& uri, const std::string& localName, const std::string& name, const std::string& character, Attributes attrs)
   void startElement(const XMLCh* const name, AttributeList& attrs);

   /** End element. */
   // public final void endElement(String uri, String localName, String name, StringBuffer character) {
   void endElement(const XMLCh* const name);

   org::xmlBlaster::util::qos::address::AddressBase& readAddress(const std::string& litteral, org::xmlBlaster::util::qos::address::AddressBase& address);
};

}}}}} // namespaces

#endif
