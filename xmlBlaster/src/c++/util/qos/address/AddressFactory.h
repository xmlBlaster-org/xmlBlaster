/*------------------------------------------------------------------------------
Name:      AddressFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory Object for parsing Address objects.
Version:   $Id: AddressFactory.h,v 1.1 2002/12/20 19:43:27 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Factory for the creation (SAX parsing from string) of AddressBase objects.
 * The created AddressBase objects can easely be converted to Address and
 * CallbackAddress objects.
 * See classes of the object it creates.
 * @see AddressBase
 * @see Address
 * @see CallbackAddress
 */

#ifndef _UTIL_CFG_ADDRESSFACTORY_H
#define _UTIL_CFG_ADDRESSFACTORY_H

#include <util/SaxHandlerBase.h>
#include <util/qos/address/AddressBase.h>

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

class Dll_Export AddressFactory : public SaxHandlerBase
{
private:
   const string ME;
   AddressBase* address_;

public:
   AddressFactory(Global& global);

   void reset(AddressBase& address);

   AddressBase& getAddress();

   /**
    * Called for SAX callback start tag
    */
   // void startElement(const string& uri, const string& localName, const string& name, const string& character, Attributes attrs)
   void startElement(const XMLCh* const name, AttributeList& attrs);

   /** End element. */
   // public final void endElement(String uri, String localName, String name, StringBuffer character) {
   void endElement(const XMLCh* const name);

   AddressBase& readAddress(const string& litteral, AddressBase& address);
};

}}}}} // namespaces

#endif
