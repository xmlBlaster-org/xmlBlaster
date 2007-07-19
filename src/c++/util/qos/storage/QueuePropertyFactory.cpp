/*------------------------------------------------------------------------------
Name:      QueuePropertyFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory which creates objects holding queue properties
Version:   $Id$
------------------------------------------------------------------------------*/

#include <util/qos/storage/QueuePropertyFactory.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;
using namespace org::xmlBlaster::util::qos::address;


namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {


QueuePropertyFactory::QueuePropertyFactory(Global& global)
   : XmlHandlerBase(global), ME("QueuePropertyFactory"), prop_(global, ""), addressFactory_(global)
{
   RELATING   = "relating";
   inAddress_ = false;
}

QueuePropertyFactory::~QueuePropertyFactory()
{
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
void QueuePropertyFactory::startElement(const string &name, const AttributeMap& attrs)
{
   //if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));

   // in case it is inside or entering an 'address' or 'callbackAddress'
   if (name.compare("address") == 0) {
      inAddress_ = true;
      addressFactory_.reset(new Address(global_));
      addressFactory_.startElement(name, attrs);
      return;
   }
   if (name.compare("callback") == 0) {
      inAddress_ = true;
      addressFactory_.reset(new CallbackAddress(global_));
      addressFactory_.startElement(name, attrs);
      return;
   }
   if (inAddress_) {
      addressFactory_.startElement(name, attrs);
      return;
   }

   // not inside any of the sub-elements (the root element)
   prop_ = QueuePropertyBase(global_, "");
   //if (log_.trace()) log_.trace(ME, "queue properties are created");


   string relating;
   if (getStringAttr(attrs, RELATING, relating)) {
      //if (log_.trace()) log_.trace(ME, "attribute 'relating' found. it is '" + relating + "'");
      if (relating == "callback") prop_.initialize("callback");
      else prop_.initialize("");
      prop_.setRelating(relating);
      if (log_.trace()) log_.trace(ME, string("the queue is relating to ") + relating);
   }

   AttributeMap::const_iterator iter = attrs.begin();
   bool found = false;
   string tmpName = (*iter).first;
   string tmpValue = (*iter).second;

   while (iter != attrs.end()) {

      if (tmpName.compare("relating") == 0) {
          // do nothing since it is already done as the first thing
          // but leave it to avoid warnings
          found = true;
      }
      else if (tmpName.compare("maxEntries") == 0) {
            prop_.setMaxEntries(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("type") == 0) {
            prop_.setType(tmpValue);
      }
      else if (tmpName.compare("version") == 0) {
            prop_.setVersion(tmpValue);
      }
      else if (tmpName.compare("maxEntriesCache") == 0) {
            prop_.setMaxEntriesCache(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("maxBytes") == 0) {
            prop_.setMaxBytes(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("maxBytesCache") == 0) {
            prop_.setMaxBytesCache(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("storeSwapLevel") == 0) {
            prop_.setStoreSwapLevel(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("storeSwapBytes") == 0) {
            prop_.setStoreSwapBytes(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("reloadSwapLevel") == 0) {
            prop_.setReloadSwapLevel(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("reloadSwapBytes") == 0) {
            prop_.setReloadSwapBytes(XmlHandlerBase::getLongValue(tmpValue));
      }
      else if (tmpName.compare("expires") == 0) {
            prop_.setExpires(XmlHandlerBase::getTimestampValue(tmpValue));
      }
      else if (tmpName.compare("onOverflow") == 0) {
            prop_.setOnOverflow(tmpValue);
      }
      else if (tmpName.compare("onFailure") == 0) {
            prop_.setOnFailure(tmpValue);
      }

      else if (tmpName.compare("size") == 0) {
         // not doing anything (this is done outside: only in MsgQos Factory
      }
      else if (tmpName.compare("index") == 0) {
         // not doing anything (this is done outside: only in MsgQos Factory
      }
      else {
         log_.warn(ME, string("Ignoring unknown attribute '") + tmpName +
             string("' in connect QoS <queue>"));
      }
      iter++;
   }
   if (!found) {
      if (log_.trace()) log_.trace(ME, "Missing 'relating' attribute in connect QoS <queue>");
   }
}


void QueuePropertyFactory::characters(const string &ch)
{
   if (inAddress_) addressFactory_.characters(ch);
}

/** End element. */
void QueuePropertyFactory::endElement(const string &name)
{
   // in case it is inside or entering an 'address' or 'callbackAddress'
   if (name.compare("address") == 0) {
      addressFactory_.endElement(name);
      prop_.addressArr_.insert((prop_.addressArr_).begin(), addressFactory_.getAddress());
      inAddress_ = false;
      return;
   }
   if (name.compare("callback") == 0) {
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

#include <util/qos/storage/ClientQueueProperty.h>

using namespace std;
using namespace org::xmlBlaster::util::qos::storage;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   try {
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      ClientQueueProperty prop(glob, "");
      cout << prop.toXml() << endl;
      Address adr(glob, "EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setAddress(adr);
      string literal = prop.toXml();
      cout << literal << endl;

      QueuePropertyFactory factory(glob);
//      ClientQueueProperty prop1(glob, "");

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
