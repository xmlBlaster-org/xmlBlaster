/*------------------------------------------------------------------------------
Name:      StatusQosSaxFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#include <util/MethodName.h>
#include <util/qos/StatusQosFactory.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;

void StatusQosFactory::prep()
{
   inState_        = false;
   inSubscribe_    = false;
   inKey_          = false;
   inQos_          = false;
}

StatusQosFactory::StatusQosFactory(Global& global)
   : XmlHandlerBase(global),
     ME("StatusQosFactory"),
     global_(global),
     log_(global.getLog("org.xmlBlaster.util.qos")),
     statusQosData_(global)
{
   prep();
}

void StatusQosFactory::startElement(const string &name, const AttributeMap& attrs)
{
   //if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));

   if (name.compare("qos") == 0) {
     statusQosData_ = StatusQosData(global_); // kind of reset
     prep();
     inQos_ = true;
     return;
   }

   if (name.compare("rcvTimestamp") == 0) {
      if (!inQos_) return;
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         if (((*iter).first).compare("nanos") == 0) {
            statusQosData_.setRcvTimestamp(getTimestampValue((*iter).second));
         }
         iter++;
      }
      return;
   }
 
   if (name.compare("state") == 0) {
      if (!inQos_) return;
      inState_ = true;

      AttributeMap::const_iterator iter = attrs.begin();
      string tmpName = (*iter).first;
      string tmpValue = (*iter).second;
      while (iter != attrs.end()) {
         if (tmpName.compare("id") == 0) {
            statusQosData_.setState(tmpValue);
         }
         if (tmpName.compare("info") == 0) {
            statusQosData_.setStateInfo(tmpValue);
         }
         iter++;
      }
      return;
   }

   if (name.compare(MethodName::SUBSCRIBE) == 0) {
      if (!inQos_) return;
      inSubscribe_ = true;
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         if (((*iter).first).compare("id") == 0) {
            statusQosData_.setSubscriptionId((*iter).second);
         }
         iter++;
      }
      return;
   }

   if (name.compare("key") == 0) {
      if (!inQos_) return;
      inKey_ = true;
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         if (((*iter).first).compare("oid") == 0) {
            statusQosData_.setKeyOid((*iter).second);
         }
         iter++;
      }
      return;
   }

   if (name.compare("persistent") == 0) {
      if (!inQos_) return;
      statusQosData_.setPersistent(true);
      return;
   }
}

void StatusQosFactory::endElement(const string &name)
{
   if (name.compare("qos") == 0) {
      character_.erase();
      inQos_ = false;
      return;
   }
   if (name.compare("state") == 0) {
      inState_ = false;
      character_.erase();
      return;
   }
   if (name.compare(MethodName::SUBSCRIBE) == 0) {
      inSubscribe_ = false;
      character_.erase();
      return;
   }
   if (name.compare("key") == 0) {
      inKey_ = false;
      character_.erase();
      return;
   }
   character_.erase();

   if(name.compare("persistent") == 0) {
      inIsPersistent_ = false;
      statusQosData_.setPersistent(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }
}

StatusQosData StatusQosFactory::readObject(const string& qos)
{
   init(qos);
   return statusQosData_;
}

}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos;

int main(int args, char* argv[])
{
    try
    {
       Global& glob = Global::getInstance();
       glob.initialize(args, argv);

       StatusQosData    data1(glob);
       StatusQosFactory factory(glob);
       string           qos   = data1.toXml();
       StatusQosData    data2 = factory.readObject(qos);

       cout << "data before parsing: " << data1.toXml() << endl;
       cout << "data after parsing : " << data2.toXml() << endl;
    }
    catch(...)  {
       cout << "Error occured";
       return 1;
    }

   return 0;
}

#endif

