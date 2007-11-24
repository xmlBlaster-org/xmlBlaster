/*------------------------------------------------------------------------------
Name:      FileWriterCallback.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _CONTRIB_FILEWRITER_H
#define _CONTRIB_FILEWRITER_H

#include <util/xmlBlasterDef.h>
#include <contrib/FileWriterCallback.h>
#include <util/Global.h>
#include <util/qos/ConnectQos.h>
#include <client/I_ConnectionProblems.h>
#include <client/I_Callback.h>
#include <util/thread/ThreadImpl.h>
#include <util/dispatch/I_PostSendListener.h>
#include <util/ReferenceCounterBase.h>
#include <util/ReferenceHolder.h>
#include <client/XmlBlasterAccess.h>

#include <string>
#include <vector>
#include <map>

/* The following comment is used by doxygen for the main html page: */
/*! \mainpage Hints about the C++ client library usage.
 *
 * \section intro_sec The C++ client library
 *
 * The xmlBlaster C++ client library supports access to xmlBlaster with asynchronous callbacks,
 * client side queuing and fail safe reconnect using the CORBA or SOCKET protocol plugin.
 * Details about compilation and its usage can be found in the 
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.cpp.html requirement.
 *
 * As a C++ developer your entry point to use is the class org::xmlBlaster::client::FileWriterCallback and
 * a complete overview demo code is HelloWorld2.cpp
 *
 * \section c_sec The C client library
 * The C client library offers some basic functionality like the SOCKET protocol with
 * the struct #FileWriterCallbackUnparsed or persistent queues with struct #I_Queue.
 * These features are heavily used by the C++ library.
 * If you need a tiny xmlBlaster access you can choose to use the C client library directly
 * without any C++ code.
 *
 * For details read the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html requirement and the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html requirement.
 * and look at the API documentation at http://www.xmlblaster.org/xmlBlaster/doc/doxygen/c/html/index.html
 */

// Note: I_ConnectionProblems.h includes I_ConnectionsHandler.h includes I_XmlBlasterConnection.h
//       which includes all EraseQos, SubscribeKey etc.
//       -> We could try to avoid this by forward declaration, but all cpp files must
//          then include them thereselves.

// Declare classes without the need to include them in this header file
namespace org { namespace xmlBlaster { namespace util {
   class MessageUnit;
}}}
namespace org { namespace xmlBlaster { namespace util { namespace dispatch {
   class DispatchManager;
   class ConnectionsHandler;
}}}}
namespace org { namespace xmlBlaster { namespace client { namespace protocol {
   class I_CallbackServer;
}}}}

namespace org { namespace xmlBlaster { namespace contrib {

/*
 * The interface org::xmlBlaster::client::I_CallbackRaw/I_Callback/I_CallbackExtended are enforced by AbstractCallbackExtended
 * is for the protocol drivers.
 */
typedef std::map<std::string, org::xmlBlaster::client::I_Callback*> CallbackMapType;
typedef std::map<std::string, std::string> StringMap;

/**
 * This is the main entry point for programmers to the C++ client library. 
 * 
 * Exactly one Global instance and one instance of this are a pair which can't be
 * mixed with other instances. 
 */
class Dll_Export FileWriter : public org::xmlBlaster::client::I_Callback
{
private:

   std::string ME;
   org::xmlBlaster::util::Global& global_;
	org::xmlBlaster::util::I_Log& log_;

   std::string subscribeKey_;
   std::string subscribeQos_;

   /** only used as a default login name and logging */
   std::string name_;
	bool momAdministered_;

   org::xmlBlaster::client::XmlBlasterAccess *access_;
   org::xmlBlaster::util::qos::ConnectQosRef connectQos_;
   FileWriterCallback *callback_;
   
   /**
    * Connects to the xmlBlaster.
    *
    * @throws XmlBlasterException
    */
	void initConnection();

public:
	FileWriter(org::xmlBlaster::util::Global &globOrig, std::string &name);

	void init();

   /**
    * If an exception occurs it means it could not publish the entry
    * @throws XmlBlasterException
    */
   void shutdown();

   std::string update(const std::string &sessionId,
                       org::xmlBlaster::client::key::UpdateKey &updateKey,
                       const unsigned char *content, long contentSize,
                       org::xmlBlaster::client::qos::UpdateQos &updateQos);

   virtual ~FileWriter();

};

typedef org::xmlBlaster::util::ReferenceHolder<org::xmlBlaster::contrib::FileWriter> FileWriterRef;

}}} // namespaces

#endif
