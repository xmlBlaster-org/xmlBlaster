/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory which creates objects holding queue properties
Version:   $Id: QueuePropertyFactory.cpp,v 1.7 2003/02/18 21:24:26 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/qos/storage/QueuePropertyFactory.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;


namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {


QueuePropertyFactory::QueuePropertyFactory(Global& global)
   : SaxHandlerBase(global), ME("QueuePropertyFactory"), prop_(global, ""), addressFactory_(global)
{
   RELATING   = XMLString::transcode("relating");
   inAddress_ = false;
   address_   = NULL;
   cbAddress_ = NULL;
}

QueuePropertyFactory::~QueuePropertyFactory()
{
   if (address_   != NULL) delete address_;
   if (cbAddress_ != NULL) delete cbAddress_;
   XMLString::release(&RELATING);
}

/*
void QueuePropertyFactory::reset(QueuePropertyBase& prop)
{
   prop_ = &prop;
}
*/

QueuePropertyBase QueuePropertyFactory::getQueueProperty()
{
   return prop_;
}


/**
 * Called for SAX callback start tag
 */
// void startElement(const string& uri, const string& localName, const string& name, const string& character, Attributes attrs)
void QueuePropertyFactory::startElement(const XMLCh* const name, AttributeList& attrs)
{
   if (log_.call()) {
      char* help = XMLString::transcode(name);
      log_.call(ME, string("startElement: ") + help);
      XMLString::release(&help);
   }
   // in case it is inside or entrering an 'address' or 'callbackAddress'
   if (SaxHandlerBase::caseCompare(name, "address")) {
      if (address_ != NULL) delete address_;
      address_ = NULL;
      address_ = new Address(global_);
      inAddress_ = true;
      addressFactory_.reset(*address_);
      addressFactory_.startElement(name, attrs);
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "callback")) {
      if (cbAddress_ != NULL) delete cbAddress_;
      cbAddress_ = NULL;
      cbAddress_ = new CallbackAddress(global_);
      inAddress_ = true;
      addressFactory_.reset(*cbAddress_);
      addressFactory_.startElement(name, attrs);
      return;
   }
   if (inAddress_) {
      addressFactory_.startElement(name, attrs);
      return;
   }

   // not inside any of the sub-elements (the root element)
   prop_ = QueuePropertyBase(global_, "");
   if (log_.trace()) log_.trace(ME, "queue properties are created");
   int len = attrs.getLength();
   if (log_.trace()) log_.trace(ME, string("length retrieved: ") + lexical_cast<string>(len));
   if (len > 0) {
      int i=0;

      string relating;
      if (getStringAttr(attrs, RELATING, relating)) {
         if (log_.trace()) log_.trace(ME, "attribute 'relating' found. it is '" + relating + "'");
         if (relating == "callback") prop_.initialize("callback");
         else prop_.initialize("");
         prop_.setRelating(relating);
         if (log_.trace()) log_.trace(ME, string("the queue is relating to ") + relating);
      }

      for (i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "relating")) {
             // do nothing since it is already done as the first thing
             // but leave it to avoid warnings
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxMsg")) {
               prop_.setMaxMsg(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "type")) {
               prop_.setType(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "version")) {
               prop_.setVersion(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxMsgCache")) {
               prop_.setMaxMsgCache(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxMsgBytes")) {
               prop_.setMaxBytes(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxBytesCache")) {
               log_.trace(ME, "maxBytesCache found");
               prop_.setMaxBytesCache(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "storeSwapLevel")) {
               prop_.setStoreSwapLevel(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "storeSwapBytes")) {
               prop_.setStoreSwapBytes(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "reloadSwapLevel")) {
               prop_.setReloadSwapLevel(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "reloadSwapBytes")) {
               prop_.setReloadSwapBytes(SaxHandlerBase::getLongValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "expires")) {
               prop_.setExpires(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "onOverflow")) {
               prop_.setOnOverflow(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "onFailure")) {
               prop_.setOnFailure(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }

         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "size")) {
            // not doing anything (this is done outside: only in MsgQos Factory
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "index")) {
            // not doing anything (this is done outside: only in MsgQos Factory
         }
         else {
            char* help = XMLString::transcode(attrs.getName(i));
            log_.warn(ME, string("Ignoring unknown attribute '") + string(help) +
                string("' in connect QoS <queue>"));
            XMLString::release(&help);
         }
      }
   }
   else {
      log_.warn(ME, "Missing 'relating' attribute in connect QoS <queue>");
   }
}


void QueuePropertyFactory::characters(const XMLCh* const ch, const unsigned int length)
{
   if (inAddress_) addressFactory_.characters(ch, length);
}

/** End element. */
void QueuePropertyFactory::endElement(const XMLCh* const name)
{
   // in case it is inside or entrering an 'address' or 'callbackAddress'
   if (SaxHandlerBase::caseCompare(name, "address")) {
      addressFactory_.endElement(name);
      prop_.addressArr_.insert((prop_.addressArr_).begin(), addressFactory_.getAddress());
      inAddress_ = false;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "callback")) {
      addressFactory_.endElement(name);
      prop_.addressArr_.insert((prop_.addressArr_).begin(), addressFactory_.getAddress());
      inAddress_ = false;
      return;
   }
   if (inAddress_) {
      addressFactory_.endElement(name);
      return;
   }
}

/*
QueuePropertyBase&
QueuePropertyFactory::readQueueProperty(const string& literal, QueuePropertyBase& prop)
{
   reset(prop);
   init(literal);
   return getQueueProperty();
}
*/

QueuePropertyBase QueuePropertyFactory::readObject(const string& literal)
{
   init(literal);
   return prop_;
}

}}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/PlatformUtils.hpp>
#include <util/qos/storage/QueueProperty.h>

using namespace std;
using namespace org::xmlBlaster::util::qos::storage;

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
//      QueueProperty prop1(glob, "");

      QueuePropertyBase base= factory.readObject(literal);
      cout << "after reparsing the same object : " << endl;
      cout << base.toXml() << endl;
   }
   catch (...) {
      cout << "an exception occured in the main thread" << endl;
   }
  return 0;
}

#endif
