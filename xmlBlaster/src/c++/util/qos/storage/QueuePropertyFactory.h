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
#include <util/SaxHandlerBase.h>
#include <util/qos/address/AddressFactory.h>
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export QueuePropertyFactory : public SaxHandlerBase
{
private:
   const std::string       ME;
   QueuePropertyBase  prop_;
   bool               inAddress_;
   org::xmlBlaster::util::qos::address::AddressFactory     addressFactory_;
   org::xmlBlaster::util::qos::address::Address*           address_;
   org::xmlBlaster::util::qos::address::CallbackAddress*   cbAddress_;
   XMLCh*             RELATING;

public:
   QueuePropertyFactory(org::xmlBlaster::util::Global& global);

   ~QueuePropertyFactory();

//   void reset(QueuePropertyBase& prop);

   QueuePropertyBase getQueueProperty();

   /**
    * Called for SAX callback start tag
    */
   void startElement(const XMLCh* const name, AttributeList& attrs);

   void characters(const XMLCh* const ch, const unsigned int length);

   /** End element. */
   void endElement(const XMLCh* const name);

/*
   QueuePropertyBase&
   readQueueProperty(const std::string& literal, QueuePropertyBase& prop);
*/
   QueuePropertyBase readObject(const std::string& literal);

};

}}}}} // namespaces

#endif
