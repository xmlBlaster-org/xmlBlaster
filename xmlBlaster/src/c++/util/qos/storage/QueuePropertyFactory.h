/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: QueuePropertyFactory.h,v 1.3 2003/01/12 00:47:48 laghi Exp $
------------------------------------------------------------------------------*/


/**
 * Factory to construct QueueProperty objects
 * <p />
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_QUEUE_QUEUEPROPERTYFACTORY_H
#define _UTIL_QUEUE_QUEUEPROPERTYFACTORY_H

#include <util/qos/storage/QueuePropertyBase.h>
#include <util/SaxHandlerBase.h>
#include <util/qos/address/AddressFactory.h>
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

class Dll_Export QueuePropertyFactory : public SaxHandlerBase
{
private:
   const string       ME;
   QueuePropertyBase  prop_;
   bool               inAddress_;
   AddressFactory     addressFactory_;
   Address*           address_;
   CallbackAddress*   cbAddress_;
   XMLCh*             RELATING;

public:
   QueuePropertyFactory(Global& global);

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
   readQueueProperty(const string& literal, QueuePropertyBase& prop);
*/
   QueuePropertyBase readObject(const string& literal);

};

}}}}} // namespaces

#endif
