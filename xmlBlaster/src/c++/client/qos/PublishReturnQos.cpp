/*------------------------------------------------------------------------------
Name:      PublishReturnQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Data container handling of status returned by subscribe(), unSubscribe(), erase() and ping(). 
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>SubscribeReturnQos Returned QoS of a subscribe() invocation (Client side)</i>
 * <li>UnSubscribeReturnQos Returned QoS of a unSubscribe() invocation (Client side)</i>
 * <li>EraseReturnQos Returned QoS of an erase() invocation (Client side)</i>
 * </ul>
 * <p>
 * For the xml representation see StatusQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.StatusQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#include <client/qos/PublishReturnQos.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

   PublishReturnQos::PublishReturnQos(Global& global, const StatusQosData& data)
      : ME("PublishReturnQos"), global_(global), data_(data)
   {
   }

   PublishReturnQos::PublishReturnQos(const PublishReturnQos& data)
     : ME(data.ME), global_(data.global_), data_(data.data_)
   {
   }

   PublishReturnQos PublishReturnQos::operator =(const PublishReturnQos& data)
   {
      return *this;
   }

   string PublishReturnQos::getState() const
   {
      return data_.getState();
   }

   string PublishReturnQos::getStateInfo() const
   {
      return data_.getStateInfo();
   }

   string PublishReturnQos::getKeyOid() const
   {
      return data_.getKeyOid();
   }

   string PublishReturnQos::toXml(const string& extraOffset="") const
   {
      return data_.toXml(extraOffset);
   }

}}}} // namespace
