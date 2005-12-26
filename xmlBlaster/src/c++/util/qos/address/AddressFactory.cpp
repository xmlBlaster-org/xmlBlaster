/*------------------------------------------------------------------------------
Name:      AddressFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory Object for parsing Address objects.
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Factory for the creation (SAX parsing from string) of AddressBase objects.
 * The created AddressBase objects can easely be converted to Address and
 * CallbackAddress objects.
 * See classes of the object it creates.
 * @see AddressBase
 * @see Address
 * @see CallbackAddress
 */

#include <util/qos/address/AddressFactory.h>
#include <util/Global.h>
#include <util/lexical_cast.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;


AddressFactory::AddressFactory(Global& global)
   : XmlHandlerBase(global), ME("AddressFactory"),
   address_(new Address(global)), // dummy allocation
   inAttribute_(false),
   attribute_(0)
{
}

AddressFactory::~AddressFactory()
{
   log_.call(ME, "destructor");
   if (attribute_ != 0) {
      delete(attribute_);
   }
}

void AddressFactory::reset(const AddressBaseRef& address)
{
   address_ = address;
   inAttribute_ = false;
   if (attribute_ != 0) {
      delete(attribute_);
   }
}

AddressBaseRef AddressFactory::getAddress()
{
   return address_;
}

/**
 * Called for SAX callback start tag
 */
// void startElement(const string& uri, const string& localName, const string& name, const string& character, Attributes attrs)
void AddressFactory::startElement(const string &name, const AttributeMap& attrs)
{
   //if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));

   if (character_.length() > 0) {
      StringTrim::trim(character_);
      if (!character_.empty()) {
         address_->setAddress(character_);
      }
      character_.erase();
   }

   if (name.compare(address_->rootTag_) == 0) { // callback
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         string tmpName = (*iter).first;
         string tmpValue = (*iter).second;
         if (tmpName.compare("type") == 0) {
               address_->setType(tmpValue);
         }
         else if (tmpName.compare("version") == 0) {
            address_->setVersion(tmpValue);
         }
         else if (tmpName.compare("bootstrapHostname") == 0) {
            address_->setHostname(tmpValue);
         }
         else if (tmpName.compare("bootstrapPort") == 0) {
            address_->setPort(XmlHandlerBase::getIntValue(tmpValue));
         }
         else if (tmpName.compare("sessionId") == 0) {
            address_->setSecretSessionId(tmpValue);
         }
         else if (tmpName.compare("pingInterval") == 0) {
            address_->setPingInterval(XmlHandlerBase::getLongValue(tmpValue));
         }
         else if (tmpName.compare("retries") == 0) {
            address_->setRetries(XmlHandlerBase::getIntValue(tmpValue));
         }
         else if (tmpName.compare("delay") == 0) {
            address_->setDelay(XmlHandlerBase::getLongValue(tmpValue));
         }
         else if (tmpName.compare("oneway") == 0) {
            address_->setOneway(XmlHandlerBase::getBoolValue(tmpValue));
         }
         else if (tmpName.compare("dispatcherActive") == 0) {
            address_->setDispatcherActive(XmlHandlerBase::getBoolValue(tmpValue));
         }
         else if (tmpName.compare("useForSubjectQueue") == 0) {
            address_->useForSubjectQueue_ = XmlHandlerBase::getBoolValue(tmpValue);
         }
         else if (tmpName.compare("dispatchPlugin") == 0) {
            address_->dispatchPlugin_ = tmpValue;
         }
         else {
            log_.warn(ME, string("Ignoring unknown attribute ") +
              tmpName +  string(" in ") + address_->rootTag_ + string(" section."));
         }
         iter++;
      }
      if (address_->getType() == "") {
         log_.error(ME, string("Missing '") + address_->rootTag_ + string("' attribute 'type' in QoS"));
         address_->setType(Global::getDefaultProtocol());
      }

      if (address_->getSecretSessionId() == "") {
         log_.warn(ME, string("Missing '") + address_->rootTag_ + string("' attribute 'sessionId' QoS"));
      }
      return;
   }

   if (name.compare("burstMode") == 0) {
      AttributeMap::const_iterator iter = attrs.begin();
      bool found = false;
      while (iter != attrs.end()) {
         if (((*iter).first).compare("collectTime") == 0) {
            address_->setCollectTime(XmlHandlerBase::getLongValue((*iter).second));
            found = true;
         }
         else if (((*iter).first).compare("maxEntries") == 0) {
            address_->setBurstModeMaxEntries(XmlHandlerBase::getIntValue((*iter).second));
            found = true;
         }
         else if (((*iter).first).compare("maxBytes") == 0) {
            address_->setBurstModeMaxBytes(XmlHandlerBase::getLongValue((*iter).second));
            found = true;
         }
         iter++;
      }
      if (!found) log_.warn(ME, "Missing attributes in login-qos <burstMode>");
      return;
   }

   if (name.compare("compress") == 0) {
      AttributeMap::const_iterator iter = attrs.begin();
      string tmpName = (*iter).first;
      string tmpValue = (*iter).second;
      bool found = false;
      while (iter != attrs.end()) {
         if (tmpName.compare("type") == 0) {
            address_->setCompressType(tmpValue);
            found = true;
         }
         else if (tmpName.compare("minSize") == 0) {
            address_->setMinSize(XmlHandlerBase::getLongValue(tmpValue));
         }
         iter++;
      }
      if (!found) log_.error(ME, "Missing 'type' attribute in qos <compress>");
      return;
   }
   if (name.compare("ptp") == 0) {
      return;
   }

   if (name.compare(ATTRIBUTE_TAG) == 0) { // "attribute"
      inAttribute_ = true;
      attributeCharacter_.erase();
      string nameAttr;
      AttributeMap::const_iterator iter = attrs.find("name");
      if (iter != attrs.end()) nameAttr = (*iter).second;
      string encoding;
      iter = attrs.find("encoding");
      if (iter != attrs.end()) encoding = (*iter).second;
      string type;
      iter = attrs.find("type");
      if (iter != attrs.end()) type = (*iter).second;
      attribute_ = new ClientProperty(true, nameAttr, type, encoding);
   }
}

/**
 * Handle SAX parsed end element
 */

/** End element. */
// public final void endElement(String uri, String localName, String name, StringBuffer character) {
void AddressFactory::endElement(const string &name)
{
   // if (log_.trace()) log_.trace(ME, string("::endElement: '") + name + string("'"));
   if (name.compare(address_->rootTag_) == 0) { // callback
      StringTrim::trim(character_);
      if (!character_.empty()) address_->setAddress(character_);
      else if (address_->getRawAddress() == "")
         log_.error(ME, address_->rootTag_ + string(" QoS contains no address data"));

   }
   else if (name.compare("burstMode") == 0) {
   }
   else if (name.compare("compress") == 0) {
   }
   else if (name.compare("ptp") == 0) {
      address_->ptpAllowed_ = StringTrim::isTrueTrim(character_);
   }
   if (name.compare(ATTRIBUTE_TAG) == 0) { // "attribute"
      inAttribute_ = false;
      attribute_->setValueRaw(attributeCharacter_);
      address_->addAttribute(*attribute_);
      delete attribute_;
      attribute_ = 0;
      attributeCharacter_.erase();
   }
   character_.erase();
}


AddressBaseRef AddressFactory::readAddress(const string& litteral, const AddressBaseRef& address)
{
   reset(address);
   init(litteral);
   return getAddress();
}

}}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST
#include <util/qos/address/Address.h>

using namespace std;
using namespace org::xmlBlaster::util::qos::address;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   try {
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      Log& log = glob.getLog("org.xmlBlaster.util.qos");
      log.info("main", "This is a simple info");
      Address a(glob);
      a.setType("SOCKET");
      a.setAddress("127.0.0.1:7600");
      a.setCollectTime(12345l);
      a.setPingInterval(54321l);
      a.setRetries(17);
      a.setDelay(7890l);
      a.setOneway(true);
      a.setDispatcherActive(false);
      a.setSecretSessionId("0x4546hwi89");
      cout << a.toXml() << endl;

      AddressFactory factory(glob);
      Address addr(glob);
      AddressBaseRef ptr = &factory.readAddress(a.toXml(), addr);
      cout << "parsed one: " << endl << ptr->toXml() << endl;

      string nodeId = "heron";
      int                nmax = 8;
      const char** argc = new const char*[nmax];
      argc[0] = "-sessionId";
      argc[1] = "ERROR";
      string help = string("-sessionId[") + nodeId + string("]");
      argc[2] = string(help).c_str();
      argc[3] = "OK";
      argc[4] = "-pingInterval";
      argc[5] = "8888";
      help = string("-delay[") + nodeId + string("]");
      argc[6] = help.c_str();
      argc[7] = "8888";

      Global& glob = Global::getInstance();
      glob.initialize(nmax, argc);
      Address a(glob, "RMI", nodeId);
      cout << a.toXml() << endl;
   }
   catch(...) {
      cout << "unknown uncatched exception" << endl;
   }
}

#endif


                            
