/*
 * A simple "Hello World" example that uses the POA
 */

#include <CORBA.h>
#include "xmlBlaster.h"
#include <mico/template_impl.h>

using namespace clientIdl;
using namespace serverIdl;
using namespace authenticateIdl;

/*
 * Hello World implementation inherits the POA skeleton class
 */

class BlasterCallback_impl : virtual public POA_clientIdl::BlasterCallback
{
public:
  void update( const serverIdl::MessageUnitArr& msgUnitArr, const serverIdl::XmlTypeArr& qosArr );
};

void
BlasterCallback_impl::update( const serverIdl::MessageUnitArr& msgUnitArr, const serverIdl::XmlTypeArr& qosArr )
{
  printf ("********* Callback invoked ************\n");
}

int
main (int argc, char *argv[])
{
  /*
   * Initialize the ORB
   */

  CORBA::ORB_var orb = CORBA::ORB_init (argc, argv, "mico-local-orb");

  /*
   * Obtain a reference to the RootPOA and its Manager
   */

  CORBA::Object_var poaobj = orb->resolve_initial_references ("RootPOA");
  PortableServer::POA_var poa = PortableServer::POA::_narrow (poaobj);
  PortableServer::POAManager_var mgr = poa->the_POAManager();

  /*
   * Create a Hello World object
   */

  BlasterCallback_impl * hello = new BlasterCallback_impl;

  /*
   * Activate the Servant
   */

  PortableServer::ObjectId_var oid = poa->activate_object (hello);

  /*
   * Activate the POA and start serving requests
   */

  printf ("Running.\n");

  mgr->activate ();
  orb->run();

  /*
   * Shutdown (never reached)
   */

  poa->destroy (TRUE, TRUE);
  delete hello;

  return 0;
}
