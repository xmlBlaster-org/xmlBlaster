/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: QueuePropertyFactory.h,v 1.1 2002/12/09 15:25:29 laghi Exp $
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

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

class Dll_Export QueuePropertyFactory : public SaxHandlerBase
{
private:
   const string ME;
   QueuePropertyBase* prop_;

public:
   QueuePropertyFactory(Global& global);


   /**
    * Called for SAX callback start tag
    */
   void startElement(const XMLCh* const name, AttributeList& attrs);

   QueuePropertyBase&
   readQueueProperty(const string& literal, QueuePropertyBase& prop);

};

}}}} // namespaces

#endif
