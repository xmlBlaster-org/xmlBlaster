/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory which creates objects holding queue properties
Version:   $Id: QueuePropertyFactory.cpp,v 1.1 2002/12/09 15:25:29 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/queue/QueuePropertyFactory.h>
#include <boost/lexical_cast.hpp>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;
using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

QueuePropertyFactory::QueuePropertyFactory(Global& global)
   : SaxHandlerBase(global)
{
}

/**
 * Called for SAX callback start tag
 */
// void startElement(const string& uri, const string& localName, const string& name, const string& character, Attributes attrs)
void QueuePropertyFactory::startElement(const XMLCh* const name, AttributeList& attrs)
{
   int len = attrs.getLength();
   if (len > 0) {
      int i=0;
      for (i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "relating")) {
               prop_->setRelating(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxMsg")) {
               prop_->setMaxMsg(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "type")) {
               prop_->setType(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "version")) {
               prop_->setVersion(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxMsgCache")) {
               prop_->setMaxMsgCache(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxMsgSize")) {
               prop_->setMaxSize(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxSizeCache")) {
               prop_->setMaxSizeCache(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "storeSwapLevel")) {
               prop_->setStoreSwapLevel(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "storeSwapSize")) {
               prop_->setStoreSwapSize(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "reloadSwapLevel")) {
               prop_->setReloadSwapLevel(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "reloadSwapSize")) {
               prop_->setReloadSwapSize(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "expires")) {
               prop_->setExpires(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "onOverflow")) {
               prop_->setOnOverflow(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "onFailure")) {
               prop_->setOnFailure(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else {
            char* help = XMLString::transcode(attrs.getName(i));
            log_.warn(ME, string("Ignoring unknown attribute '") + string(help) +
                string("' in connect QoS <queue>"));
            delete help;
         }
      }
   }
   else {
      log_.warn(ME, "Missing 'relating' attribute in connect QoS <queue>");
   }
}


QueuePropertyBase&
QueuePropertyFactory::readQueueProperty(const string& literal, QueuePropertyBase& prop)
{
//   if (prop != NULL) {
      prop_ = &prop;
      init(literal);
      return *prop_;
//   }
}

}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/PlatformUtils.hpp>
#include <util/queue/QueueProperty.h>

using namespace std;
using namespace org::xmlBlaster::util::queue;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   try {
      XMLPlatformUtils::Initialize();

      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      QueueProperty prop(glob, "");
      cout << prop.toXml() << endl;
      Address adr(glob, "EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setAddress(adr);
      string literal = prop.toXml();
      cout << literal << endl;

      QueuePropertyFactory factory(glob);
      QueueProperty prop1(glob, "");

      QueuePropertyBase* ptr = &factory.readQueueProperty(literal, prop1);
      cout << "after reparsing the same object : " << endl;
      cout << ptr->toXml() << endl;
   }
   catch (...) {
      cout << "an exception occured in the main thread" << endl;
   }
  return 0;
}

#endif
