/*------------------------------------------------------------------------------
Name:      StatusQosSaxFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/qos/StatusQosFactory.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;

void StatusQosFactory::prep()
{
   inState_     = false;
   inSubscribe_ = false;
   inKey_       = false;
   inQos_       = false;
}

StatusQosFactory::StatusQosFactory(Global& global)
   : SaxHandlerBase(global),
     ME("StatusQosFactory"),
     global_(global),
     log_(global.getLog("core")),
     statusQosData_(global)
{
   prep();
}

void StatusQosFactory::startElement(const XMLCh* const name, AttributeList& attrs)
{
   log_.call(ME, "startElement");

   if (SaxHandlerBase::caseCompare(name, "qos")) {
     statusQosData_ = StatusQosData(global_); // kind of reset
     prep();
     inQos_ = true;
     return;
   }

   if (SaxHandlerBase::caseCompare(name, "rcvTimestamp")) {
      if (!inQos_) return;
      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "nanos")) {
            statusQosData_.setRcvTimestamp(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
         }
      }
      return;
   }
 
   if (SaxHandlerBase::caseCompare(name, "state")) {
      if (!inQos_) return;
      inState_ = true;

      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "id")) {
            statusQosData_.setState(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "info")) {
            statusQosData_.setStateInfo(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
      }
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "subscribe")) {
      if (!inQos_) return;
      inSubscribe_ = true;

      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "id")) {
            statusQosData_.setSubscriptionId(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
      }
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "key")) {
      if (!inQos_) return;
      inKey_ = true;

      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "oid")) {
            statusQosData_.setKeyOid(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
      }
      return;
   }
}

void StatusQosFactory::endElement(const XMLCh* const name)
{
   if (SaxHandlerBase::caseCompare(name, "qos")) {
      character_.erase();
      inQos_ = false;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "state")) {
      inState_ = false;
      character_.erase();
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "subscribe")) {
      inSubscribe_ = false;
      character_.erase();
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "key")) {
      inKey_ = false;
      character_.erase();
      return;
   }
   character_.erase();
}

StatusQosData StatusQosFactory::readObject(const string& qos)
{
   init(qos);
   return statusQosData_;
}

}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/PlatformUtils.hpp>

using namespace std;
using namespace org::xmlBlaster::util::qos;

int main(int args, char* argv[])
{
    // Init the XML platform
    try
    {
       XMLPlatformUtils::Initialize();

       Global& glob = Global::getInstance();
       glob.initialize(args, argv);

       StatusQosData    data1(glob);
       StatusQosFactory factory(glob);
       string           qos   = data1.toXml();
       StatusQosData    data2 = factory.readObject(qos);

       cout << "data before parsing: " << data1.toXml() << endl;
       cout << "data after parsing : " << data2.toXml() << endl;
    }
    catch(const XMLException& toCatch)  {
       cout << "Error during platform init! Message:\n";
       cout <<toCatch.getMessage() << endl;
       return 1;
    }

   return 0;
}

#endif

