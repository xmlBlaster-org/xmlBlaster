/*------------------------------------------------------------------------------
Name:      CorbaDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#include <client/protocol/corba/CorbaDriver.h>

using org::xmlBlaster::util::MessageUnit;
using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

CorbaDriver::CorbaDriver(int args, const char * const argc[],
    bool connectionOwner) : connection_(args, argc, connectionOwner),
    defaultCallback_("default", NULL)
{
}

CorbaDriver::~CorbaDriver()
{
}

void CorbaDriver::initialize(const string& name, I_Callback &client)
{
   defaultCallback_ =  DefaultCallback(name, &client, 0);
   connection_.createCallbackServer(&defaultCallback_);
}

string CorbaDriver::getCbProtocol()
{
    return "IOR";
}

string CorbaDriver::getCbAddress()
{
   return connection_.getAddress();
}

bool CorbaDriver::shutdownCb()
{
   return connection_.shutdownCb();
}

ConnectReturnQos CorbaDriver::connect(const ConnectQos& qos)
{
   return connection_.connect(qos);
}

bool CorbaDriver::disconnect(const string& qos)
{
   return connection_.disconnect(qos);
}

string CorbaDriver::getProtocol()
{
   return "IOR";
}

string CorbaDriver::loginRaw()
{
   connection_.loginRaw();
   return getLoginName();
}

bool CorbaDriver::shutdown()
{
   return connection_.shutdown();
}

void CorbaDriver::resetConnection()
{
   std::cerr << "'CorbaDriver::resetConnection' not implemented" << std::endl;
}

string CorbaDriver::getLoginName()
{
   return connection_.getLoginName();
}

bool CorbaDriver::isLoggedIn()
{
   return connection_.isLoggedIn();
}

string CorbaDriver::ping(const string& qos)
{
   return connection_.ping(qos);
}

string CorbaDriver::subscribe(const string& xmlKey, const string& qos)
{
   return connection_.subscribe(xmlKey, qos);
}

vector<MessageUnit> CorbaDriver::get(const string& xmlKey, const string& qos)
{
   return connection_.get(xmlKey, qos);
}

vector<string>
CorbaDriver::unSubscribe(const string& xmlKey, const string& qos)
{
   return connection_.unSubscribe(xmlKey, qos);
}

string CorbaDriver::publish(const MessageUnit& msgUnit)
{
   return connection_.publish(msgUnit);
}

void CorbaDriver::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   connection_.publishOneway(msgUnitArr);
}

vector<string> CorbaDriver::publishArr(vector<MessageUnit> msgUnitArr)
{
   return connection_.publishArr(msgUnitArr);
}

vector<string> CorbaDriver::erase(const string& xmlKey, const string& qos)
{
   return connection_.erase(xmlKey, qos);
}

void CorbaDriver::usage()
{
   CorbaConnection::usage();
}


}}}}} // namespaces

