/*------------------------------------------------------------------------------
Name:      UpdateReturnQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/qos/UpdateReturnQos.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

   UpdateReturnQos::UpdateReturnQos(Global& global, const StatusQosData& data)
      : ME("UpdateReturnQos"), global_(global), data_(data)
   {
   }

   UpdateReturnQos::UpdateReturnQos(const UpdateReturnQos& data)
     : ME(data.ME), global_(data.global_), data_(data.data_)
   {
   }

   UpdateReturnQos UpdateReturnQos::operator =(const UpdateReturnQos& /*data*/)
   {
      return *this;
   }

   void UpdateReturnQos::setState(const string& state)
   {
      data_.setState(state);
   }

   void UpdateReturnQos::setStateInfo(const string& stateInfo)
   {
      data_.setStateInfo(stateInfo);
   }

   string UpdateReturnQos::toXml(const string& extraOffset) const
   {
      return data_.toXml(extraOffset);
   }

}}}} // namespace
