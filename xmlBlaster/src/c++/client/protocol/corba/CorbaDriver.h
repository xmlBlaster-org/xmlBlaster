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
#include <client/protocol/corba/DefaultCallback.h>
#include <string>
#include <vector>
#include <util/MessageUnit.h>
#include <client/I_Callback.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/XmlBlasterException.h>

using org::xmlBlaster::util::MessageUnit;
using org::xmlBlaster::util::Global;
using namespace org::xmlBlaster::util::qos;
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
      CorbaConnection* connection_;
      DefaultCallback* defaultCallback_;
      const string     ME;
      Global&          global_;

      /**
       * frees the resources used. It only frees the resource specified with
       * 'true'.
       */
      void freeResources(bool deleteConnection=true, bool deleteCallback=true);

   public:
      CorbaDriver(Global& global, bool connectionOwner = false);

      virtual ~CorbaDriver();

      // methods inherited from I_CallbackServer
      void initialize(const string& name, I_Callback &client);
      string getCbProtocol();
      string getCbAddress();
      bool shutdownCb();

      // methods inherited from I_XmlBlasterConnection
      ConnectReturnQos connect(const ConnectQos& qos);
//      string connect(const string& qos);
      bool disconnect(const string& qos="");
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

      // following methods are not defined in any parent class
      static void usage();
      // Exception conversion ....
      static org::xmlBlaster::util::XmlBlasterException
        convertFromCorbaException(const serverIdl::XmlBlasterException& ex);
      static serverIdl::XmlBlasterException
        convertToCorbaException(org::xmlBlaster::util::XmlBlasterException& ex);
   };

}}}}} // namespaces

#endif
