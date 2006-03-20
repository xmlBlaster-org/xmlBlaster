/*------------------------------------------------------------------------------
Name:      SubscribeReturnQos.cpp
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

#include <client/qos/SubscribeReturnQos.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

   SubscribeReturnQos::SubscribeReturnQos(Global& global, const StatusQosData& data)
      : ME("SubscribeReturnQos"), global_(global), data_(data)
   {
   }

   SubscribeReturnQos::SubscribeReturnQos(Global& global)
      : ME("SubscribeReturnQos"), global_(global), data_(global)
   {
   }

   SubscribeReturnQos::SubscribeReturnQos(const SubscribeReturnQos& data)
     : ME(data.ME), global_(data.global_), data_(data.data_)
   {
   }

   SubscribeReturnQos SubscribeReturnQos::operator =(const SubscribeReturnQos& other)
   {
      if (this != &other) data_ = other.data_;
      return *this;
   }

   string SubscribeReturnQos::getState() const
   {
      return data_.getState();
   }

   string SubscribeReturnQos::getStateInfo() const
   {
      return data_.getStateInfo();
   }

   string SubscribeReturnQos::getSubscriptionId() const
   {
      return data_.getSubscriptionId();
   }

   string SubscribeReturnQos::toXml(const string& extraOffset) const
   {
      return data_.toXml(extraOffset);
   }

   StatusQosData& SubscribeReturnQos::getData()
   {
      return data_;
   }

   bool SubscribeReturnQos::isPersistent() const {
      return data_.isPersistent();
   }    

}}}} // namespace
