/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: QueuePropertyFactory.h,v 1.2 2002/12/09 23:19:14 laghi Exp $
------------------------------------------------------------------------------*/


/**
 * Factory to construct QueueProperty objects
 * <p />
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#ifndef _UTIL_QUEUE_QUEUEPROPERTYFACTORY_H
#define _UTIL_QUEUE_QUEUEPROPERTYFACTORY_H

#include <util/queue/QueuePropertyBase.h>
#include <util/SaxHandlerBase.h>
#include <util/cfg/AddressFactory.h>
#include <util/cfg/Address.h>
#include <util/cfg/CallbackAddress.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export QueuePropertyFactory : public SaxHandlerBase
{
private:
   const string       ME;
   QueuePropertyBase* prop_;
   bool               inAddress_;
   AddressFactory     addressFactory_;
   Address*           address_;
   CallbackAddress*   cbAddress_;

public:
   QueuePropertyFactory(Global& global);

   ~QueuePropertyFactory();

   void reset(QueuePropertyBase& prop);

   QueuePropertyBase& getQueueProperty();

   /**
    * Called for SAX callback start tag
    */
   void startElement(const XMLCh* const name, AttributeList& attrs);

   void characters(const XMLCh* const ch, const unsigned int length);

   /** End element. */
   void endElement(const XMLCh* const name);

   QueuePropertyBase&
   readQueueProperty(const string& literal, QueuePropertyBase& prop);

};

}}}} // namespaces

#endif
