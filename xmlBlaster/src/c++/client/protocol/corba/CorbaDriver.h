/*------------------------------------------------------------------------------
Name:      CorbaDriver.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_CORBA_DRIVER
#define _CLIENT_PROTOCOL_CORBA_CORBA_DRIVER

#include <util/xmlBlasterDef.h>
#include <client/protocol/corba/CorbaConnection.h>
#include <client/DefaultCallback.h>
#include <string>
#include <vector>
#include <util/MessageUnit.h>
#include <client/I_Callback.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/I_XmlBlasterConnection.h>

using org::xmlBlaster::util::MessageUnit;
using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

   using namespace org::xmlBlaster::util::qos;

   class CorbaDriver : public I_CallbackServer, public I_XmlBlasterConnection
   {
   private:
      CorbaConnection connection_;
      DefaultCallback defaultCallback_;
   public:

      CorbaDriver(int args=0,
                  const char * const argc[]=0,
                  bool connectionOwner = false);

      virtual ~CorbaDriver();

      // methods inherited from I_CallbackServer
      void initialize(const string& name, I_Callback &client);
      string getCbProtocol();
      string getCbAddress();
      bool shutdownCb();

      // methods inherited from I_XmlBlasterConnection
      ConnectReturnQos connect(const ConnectQos& qos);
//      string connect(const string& qos);
      bool disconnect(const string& qos);
      string getProtocol();
      string loginRaw();
      bool shutdown();
      void resetConnection();
      string getLoginName();
      bool isLoggedIn();
      string ping(const string& qos);
      string subscribe(const string& xmlKey, const string& qos);
      vector<MessageUnit> get(const string& xmlKey, const string& qos);
      vector<string> unSubscribe(const string& xmlKey, const string& qos);
      string publish(const MessageUnit& msgUnit);
      void publishOneway(const vector<MessageUnit> &msgUnitArr);
      vector<string> publishArr(vector<MessageUnit> msgUnitArr);
      vector<string> erase(const string& xmlKey, const string& qos);
   };

}}}}} // namespaces

#endif
