/*------------------------------------------------------------------------------
Name:      AddressFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory Object for parsing Address objects.
Version:   $Id: AddressFactory.cpp,v 1.4 2003/02/07 11:41:42 laghi Exp $
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
#include <boost/lexical_cast.hpp>

using namespace org::xmlBlaster::util;
using namespace boost;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

AddressFactory::AddressFactory(Global& global)
   : SaxHandlerBase(global), ME("AddressFactory")
{
   address_ = NULL;
}

void AddressFactory::reset(AddressBase& address)
{
   address_ = &address;
}

AddressBase& AddressFactory::getAddress()
{
   return *address_;
}

/**
 * Called for SAX callback start tag
 */
// void startElement(const string& uri, const string& localName, const string& name, const string& character, Attributes attrs)
void AddressFactory::startElement(const XMLCh* const name, AttributeList& attrs)
{
//   log_.info(ME, string("startElement(rootTag=") + address_->rootTag_ +
//    string("): name=") + name + " character='" + character_ + "'");

   if (log_.call()) log_.call(ME, "::startElement");
   if (log_.trace()) {
     char* txt = XMLString::transcode(name);
     log_.trace(ME, string("::startElement: '") + string(txt) + string("'"));
     delete txt;
   }

   if (character_.length() > 0) {
      char* help = charTrimmer_.trim(character_.c_str());
      if (help != NULL) {
         string tmp(help);
         delete help;
         if (tmp.length() > 0) {
            address_->setAddress(tmp);
         }
      }
      character_.erase();
   }

   if (SaxHandlerBase::caseCompare(name, address_->rootTag_.c_str())) { // callback
      int len = attrs.getLength();
      if (len > 0) {
         for (int i = 0; i < len; i++) {
            if (SaxHandlerBase::caseCompare(attrs.getName(i), "type")) {
                  address_->setType(SaxHandlerBase::getStringValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "version")) {
               address_->setVersion(SaxHandlerBase::getStringValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "hostname")) {
               address_->setHostname(SaxHandlerBase::getStringValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "port")) {
               address_->setPort(SaxHandlerBase::getIntValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "sessionId")) {
               address_->setSecretSessionId(SaxHandlerBase::getStringValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "pingInterval")) {
               address_->setPingInterval(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "retries")) {
               address_->setRetries(SaxHandlerBase::getLongValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "delay")) {
               address_->setDelay(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "oneway")) {
               string str1 = SaxHandlerBase::getStringValue(attrs.getValue(i));
               bool ret = "false";
               if (str1 == "true") ret = true;
               address_->setOneway(ret);
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "useForSubjectQueue")) {
               string str1 = SaxHandlerBase::getStringValue(attrs.getValue(i));
               bool ret = "false";
               if (str1 == "true") ret = true;
               address_->useForSubjectQueue_ = ret;
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "dispatchPlugin")) {
               address_->dispatchPlugin_ = SaxHandlerBase::getStringValue(attrs.getValue(i));
            }
            else {
               log_.error(ME, string("Ignoring unknown attribute ") +
                 SaxHandlerBase::getStringValue(attrs.getName(i)) +
                 string(" in ") + address_->rootTag_ + string(" section."));
            }
         }
      }
      if (address_->getType() == "") {
         log_.error(ME, string("Missing '") + address_->rootTag_ + string("' attribute 'type' in QoS"));
         address_->setType("IOR");
      }

      if (address_->getSecretSessionId() == "") {
         log_.warn(ME, string("Missing '") + address_->rootTag_ + string("' attribute 'sessionId' QoS"));
      }
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "burstMode")) {
      int len = attrs.getLength();
      if (len > 0) {
         int i=0;
         for (i = 0; i < len; i++) {
            if (SaxHandlerBase::caseCompare(attrs.getName(i), "collectTime")) {
               address_->setCollectTime(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "collectTimeOneway")) {
               address_->setCollectTimeOneway(SaxHandlerBase::getTimestampValue(attrs.getValue(i)));
            }
         }
      }
      else {
         log_.error(ME, "Missing 'collectTime' or 'collectTimeOneway' attribute in login-qos <burstMode>");
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "compress")) {
      int len = attrs.getLength();
      if (len > 0) {
         for (int i = 0; i < len; i++) {
            if (SaxHandlerBase::caseCompare(attrs.getName(i), "type")) {
               address_->setCompressType(SaxHandlerBase::getStringValue(attrs.getValue(i)));
            }
            else if (SaxHandlerBase::caseCompare(attrs.getName(i), "minSize")) {
               address_->setMinSize(SaxHandlerBase::getLongValue(attrs.getValue(i)));
            }
         }
      }
      else {
         log_.error(ME, "Missing 'type' attribute in qos <compress>");
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "ptp")) {
      return;
   }
}

/**
 * Handle SAX parsed end element
 */

/** End element. */
// public final void endElement(String uri, String localName, String name, StringBuffer character) {
void AddressFactory::endElement(const XMLCh* const name)
{
   if (log_.call()) log_.call(ME, "::endElement");
   if (log_.trace()) {
     char* txt = XMLString::transcode(name);
     log_.trace(ME, string("::endElement: '") + string(txt) + string("'"));
     delete txt;
   }
   if (SaxHandlerBase::caseCompare(name, address_->rootTag_.c_str())) { // callback
      string tmp = "";
      if (character_.length() > 0) {
         char* help = charTrimmer_.trim(character_.c_str());
         if (help != NULL) {
            tmp = string(help);
            delete help;
         }
      }
      if (tmp.length() > 0) address_->setAddress(tmp);
      else if (address_->getAddress() == "")
         log_.error(ME, address_->rootTag_ + string(" QoS contains no address data"));

   }
   else if (SaxHandlerBase::caseCompare(name, "burstMode")) {
   }
   else if (SaxHandlerBase::caseCompare(name, "compress")) {
   }
   else if (SaxHandlerBase::caseCompare(name, "ptp")) {
      if (character_.length() > 0) {
         char *help = charTrimmer_.trim(character_.c_str());
         if (help != NULL) {
            string tmp(help);
            delete help;
            bool ret = false;
            if (tmp == "true") ret = true;
            address_->ptpAllowed_ = ret;
         }
      }
   }
   character_.erase();
}


AddressBase& AddressFactory::readAddress(const string& litteral, AddressBase& address)
{
   reset(address);
   init(litteral);
   return getAddress();
}

}}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST
#include <util/qos/address/Address.h>
#include <util/PlatformUtils.hpp>

using namespace std;
using namespace org::xmlBlaster::util::qos::address;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   try {
      {
         XMLPlatformUtils::Initialize();

         Global& glob = Global::getInstance();
         glob.initialize(args, argv);
         Log& log = glob.getLog("core");
         log.info("main", "This is a simple info");
         Address a(glob);
         a.setType("SOCKET");
         a.setAddress("127.0.0.1:7600");
         a.setCollectTime(12345l);
         a.setPingInterval(54321l);
         a.setRetries(17);
         a.setDelay(7890l);
         a.setOneway(true);
         a.setSecretSessionId("0x4546hwi89");
         cout << a.toXml() << endl;

         AddressFactory factory(glob);
         Address addr(glob);
         AddressBase* ptr = &factory.readAddress(a.toXml(), addr);
         cout << "parsed one: " << endl << ptr->toXml() << endl;

      }
      {
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
   }
   catch(...) {
      cout << "unknown uncatched exception" << endl;
   }
}

#endif


