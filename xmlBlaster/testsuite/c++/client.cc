#define MICO_CONF_IMR
#include <CORBA-SMALL.h>
#include "xmlBlaster.h"    // xmlBlaster.h generated from 'xmlBlaster.idl'

using namespace authenticateIdl;
using namespace serverIdl;

namespace clientIdl {
   /**
    * This is the callback implementation
    */
   class BlasterCallback_impl : virtual public BlasterCallback_skel   {
      MessageUnit messageUnit;  // private Data of this object-implementation
   public:
      BlasterCallback_impl() {}
      ~BlasterCallback_impl() {}

      /*
       * implement the update method, which was promised in "xmlBlaster.idl"
       */
      void update(const serverIdl::MessageUnitArr& messageUnitArr, const serverIdl::XmlTypeArr& qosArr)
      {
         cout << "******* Callback invoked *******" << endl;
      }
   };
}


/**
 */
int main(int argc, char* argv[])
{
   AuthServer_var  authServer_obj;
   CORBA::ORB_var  orb;
  CORBA::BOA_var  boa;
   char            objref_str[1024];

   try {
      // initialize
      orb = CORBA::ORB_init(argc, argv, "mico-local-orb");

      //--------------- find server object reference -------
      if (argc == 2) {
         strncpy(objref_str, argv[1], 1024);
      }
      else {
        cout << "Enter IOR from AuthServer-Server: ";
        cin  >> objref_str;
      }

      // narrow IOR-String to Obj-Ref
      authServer_obj= AuthServer::_narrow(orb->string_to_object(objref_str));


      //--------------- create my BlasterCallback server ----
      clientIdl::BlasterCallback_impl *callback_impl;
      boa= orb->BOA_init(argc, argv, "mico-local-boa");
      callback_impl = new clientIdl::BlasterCallback_impl();

      // dump object-reference IOR (as string) (for fun only)
      cout << "Objekt-Referenz dieser xmlBlaster-Implementation:" << endl;
      cout << "\t" << orb->object_to_string(callback_impl) << endl;
      cout.flush();
 
      // 
      boa->impl_is_ready (CORBA::ImplementationDef::_nil());



      //-------------- login() to AuthServer_obj ---------
      serverIdl::Server_ptr xmlBlaster = authServer_obj->login("Ben", "secret", callback_impl, "");
      cout << "Successfull login!" << endl;

      
      //-------------- publish() a message -------------
      string xmlKey("<?xml version='1.0' encoding='ISO-8859-1' ?>\n"
                    "<key oid=''>\n"
                    "<AGENT id='192.168.124.10' subId='1' type='generic'>"
                    "<DRIVER id='FileProof' pollingFreq='10'>"
                    "</DRIVER>"
                    "</AGENT>"
                    "</key>");

      MessageUnit message;
      message.xmlKey = xmlKey.c_str();
      string content = "Hello xmlBlaster, i'm a C++ client";
      //??? message.content(MICO_ULong(content.size()), MICO_ULong(content.size()), (CORBA::Octet*)content.c_str());

      string publishOid = xmlBlaster->publish(message, "");
      cout << "Successfull published message with new oid=" << publishOid << endl;


      //-------------- subscribe() to the previous message OID -------
      cout << "Subscribing using the exact oid ..." << endl;
      xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n"
               "<key oid='" + publishOid + "'>\n"
               "</key>";
      string qualityOfService = "";
      try {
         xmlBlaster->subscribe(xmlKey.c_str(), qualityOfService.c_str());
      } catch(XmlBlasterException e) {
         cerr << "XmlBlasterException: " << e.reason << endl;
      }
      cout << "Subscribed to '" << publishOid << "' ..." << endl;   
                                                                    

      //-------------- wait for something to happen -------------------
      orb->run ();

   } catch(serverIdl::XmlBlasterException e) {
      cerr << "Caught Server Exception: " << e.reason << endl;
   } catch( ... ) {
      cerr << "Login to xmlBlaster failed" << endl;
   }
   return 0;
}

