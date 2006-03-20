/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Author:    laghi
------------------------------------------------------------------------------*/


/**
 * Factory to construct org::xmlBlaster::util::queue::QueueProperty objects
 * <p />
 * See org::xmlBlaster::util::qos::ConnectQos for XML syntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
#ifndef _UTIL_QUEUE_QUEUEPROPERTYFACTORY_H
#define _UTIL_QUEUE_QUEUEPROPERTYFACTORY_H

#include <util/qos/storage/QueuePropertyBase.h>
#include <util/parser/XmlHandlerBase.h>
#include <util/qos/address/AddressFactory.h>
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export QueuePropertyFactory : public parser::XmlHandlerBase
{
private:
   const std::string  ME;
   QueuePropertyBase  prop_;
   bool               inAddress_;
   org::xmlBlaster::util::qos::address::AddressFactory addressFactory_;
   std::string  RELATING;

public:
   QueuePropertyFactory(org::xmlBlaster::util::Global& global);

   ~QueuePropertyFactory();

   QueuePropertyBase getQueueProperty();

   /**
    * Called for XML callback start tag
    */
   void startElement(const std::string &name, const parser::AttributeMap& attrs);

   void characters(const std::string &ch);

   /** End element. */
   void endElement(const std::string &name);

/*
   QueuePropertyBase&
   readQueueProperty(const std::string& literal, QueuePropertyBase& prop);
*/
   QueuePropertyBase readObject(const std::string& literal);

};

}}}}} // namespaces

#endif
