/*------------------------------------------------------------------------------
Name:      xmlBlasterClient.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Can be used for clients to include everything
------------------------------------------------------------------------------*/

#ifndef _CLIENT_XMLBLASTERCLIENT_H
#define _CLIENT_XMLBLASTERCLIENT_H

#include <util/qos/ConnectQos.h>
#include <util/qos/DisconnectQos.h> 

#include <client/qos/EraseQos.h>
#include <client/qos/EraseReturnQos.h>
#include <client/qos/GetQos.h>
#include <client/qos/GetReturnQos.h>
#include <client/qos/PublishQos.h>
#include <client/qos/PublishReturnQos.h>
#include <client/qos/SubscribeQos.h>
#include <client/qos/SubscribeReturnQos.h>
#include <client/qos/UnSubscribeQos.h>
#include <client/qos/UnSubscribeReturnQos.h>
#include <client/qos/UpdateQos.h>
#include <client/qos/UpdateReturnQos.h>

#include <client/key/EraseKey.h>
#include <client/key/GetKey.h>
#include <client/key/GetReturnKey.h>
#include <client/key/PublishKey.h>
#include <client/key/SubscribeKey.h>
#include <client/key/UnSubscribeKey.h>
#include <client/key/UpdateKey.h>

/**
 * Helper namespace for clients which presents all relevant xmlBlaster
 * namespaces in a simple and short namespace 'xmlBlaster'
 */
namespace xmlBlaster {
   using namespace org::xmlBlaster::util;
   //using namespace org::xmlBlaster::util::thread;
   //using namespace org::xmlBlaster::util::dispatch;
   using namespace org::xmlBlaster::util::qos;
   using namespace org::xmlBlaster::util::key;
   using namespace org::xmlBlaster::client;
   using namespace org::xmlBlaster::client::qos;
   using namespace org::xmlBlaster::client::key;
}

#endif
