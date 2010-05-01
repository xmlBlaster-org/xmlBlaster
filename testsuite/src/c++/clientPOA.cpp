
#define  SERVER_HEADER 1 // does #include <generated/xmlBlasterS.h> with CompatibleCorba.h, OMNIORB: use -Wbh=.h to force this extension
#include <client/protocol/corba/CompatibleCorba.h>
#include COSNAMING
#include <generated/xmlBlaster.h>
#include <fstream>
#include <string>
#include <iostream>

using namespace std;
using namespace authenticateIdl;
using namespace serverIdl;


/**
 * Callback implementation
 */
class BlasterCallback_impl : virtual public POA_clientIdl::BlasterCallback  {

private:
  ostream& print_msg(ostream &out, const serverIdl::MessageUnit &msg) {
    for (string::size_type i=0; i < msg.content.length(); i++) {
      out << msg.content[i];
    }
    return out;
  };

public:
  BlasterCallback_impl()  {}
  ~BlasterCallback_impl() {}

  serverIdl::XmlTypeArr* update(const char* /*sessionId*/, const serverIdl::MessageUnitArr& messageUnitArr) {
    int nmax = messageUnitArr.length();
    serverIdl::XmlTypeArr *res = new serverIdl::XmlTypeArr(nmax);
    res->length(nmax);
    cout << endl;
    cout << "Callback invoked: there are " << nmax << " messages" << endl;
    cout << "messages: " << endl;
    for (int i=0; i < nmax; i++) {
      print_msg(cout,messageUnitArr[i]);
      cout << endl;

      CORBA::String_var str = CORBA::string_dup("<qos><state id='OK'/></qos>");
      (*res)[i] = str;
    }
    return res;
  };

  void updateOneway(const char* /*sessionId*/, const serverIdl::MessageUnitArr& messageUnitArr) {
    int nmax = messageUnitArr.length();
    cout << endl;
    cout << "Oneway callback invoked: there are " << nmax << " messages" << endl;
    cout << "messages: " << endl;
    for (int i=0; i < nmax; i++) {
      print_msg(cout,messageUnitArr[i]);
      cout << endl;
    }
  };

  char *ping(const char * /*qos*/) {
   return CORBA::string_dup("");
  };
};



int main(int argc, char* argv[]) {

  AuthServer_var authServer_obj;
  CORBA::ORB_var orb;

  try {
    // Create the ORB
    orb = CORBA::ORB_init(argc, argv);

    // Get the naming service
    CORBA::Object_var obj0;
    try {
      obj0 = orb->resolve_initial_references("NameService");
    } catch(const CORBA::ORB::InvalidName&) {
      cerr << argv[0] << ": can't resolve `NameService'" << endl << "Start naming service and try" << endl << "   ./clientPOA -ORBNamingIOR `cat ${DocumentRoot}/NS_Ref`" << endl << "(read README file)" << endl;
      return 1;
    }

    if(CORBA::is_nil(obj0.in())) {
      cerr << argv[0] << ": `NameService' is a nil object reference"
           << endl;
      return 1;
    }

    CosNaming::NamingContext_var nc =
      CosNaming::NamingContext::_narrow(obj0.in());

    if(CORBA::is_nil(nc.in())) {
      cerr << argv[0] << ": `NameService' is not a NamingContext";
      cerr << "object reference" << endl;
      return 1;
    }

    // now the Naming Context is known: get the objects by name

    CORBA::Object_var aObj;
    try {
      // Resolve names with the Naming Service
      CosNaming::Name aName;
      aName.length(1);
      aName[0].id   = CORBA::string_dup("xmlBlaster-Authenticate");
      aName[0].kind = CORBA::string_dup("MOM");
      aObj          = nc->resolve(aName);
      cout << "Resolved the authentication" << endl;

    } catch(const CosNaming::NamingContext::NotFound& ex) {
      cerr << argv[0] << ": Got a `NotFound' exception (";
      switch(ex.why)
        {
        case CosNaming::NamingContext::missing_node: cerr << "missing node";
          break;
        case CosNaming::NamingContext::not_context: cerr << "not context";
          break;
        case CosNaming::NamingContext::not_object: cerr << "not object";
          break;
        }
      cerr << ")" << endl;
      return 1;
    } catch(const CosNaming::NamingContext::CannotProceed&) {
      cerr << argv[0] << ": Got a `CannotProceed' exception" << endl;
      return 1;
    } catch(const CosNaming::NamingContext::InvalidName&) {
      cerr << argv[0] << ": Got an `InvalidName' exception" << endl;
      return 1;
    } catch(const CosNaming::NamingContext::AlreadyBound&) {
      cerr << argv[0] << ": Got an `AlreadyBound' exception" << endl;
      return 1;
    } catch(const CosNaming::NamingContext::NotEmpty&) {
      cerr << argv[0] << ": Got a `NotEmpty' exception" << endl;
      return 1;
    }

    // narrow IOR-String to object reference
    authServer_obj= AuthServer::_narrow(aObj);

    // get the rootPOA
    CORBA::Object_var obj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(obj);
    PortableServer::POAManager_var poa_mgr = poa->the_POAManager();

    // create an instance of the Callback Servant
    BlasterCallback_impl *impl = new BlasterCallback_impl();

    // Implicitly activate the Servant & get a reference to it
    clientIdl::BlasterCallback_ptr callback = impl->_this();

    // activate the poa
    poa_mgr->activate();

    string xmlQos("<qos><callback type='IOR'>");
    xmlQos += orb->object_to_string(callback);
    xmlQos += "</callback></qos>";

    serverIdl::Server_ptr
      xmlBlaster = authServer_obj->login("Fritz", "simple", xmlQos.c_str());

    cout << "Successful login!" << endl;

    //-------------- publish() a message -------------
    string xmlKey("<?xml version='1.0' encoding='ISO-8859-1' ?>\n"
                  "<key oid='' contentMime='text/xml'>\n </key>");

    MessageUnit message;
    message.xmlKey    = xmlKey.c_str();

    // is there a better way to fill the message.content ??
    char content[100] = "ti che ta tacat i tac tacum i tac!";
    message.content   = ContentType(sizeof(content),sizeof(content),
                                    (CORBA::Octet*)content);
    message.qos = "<qos></qos>";

    string publishOid = xmlBlaster->publish(message);

    cout << "Successfully published message with new oid=";
    cout << publishOid << endl;

    //-------------- subscribe() to the previous message OID -------
    cout << "Subscribing using the exact oid ..." << endl;
    xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n"
      "<key oid='" + publishOid + "'>\n </key>";
    string qualityOfService = "";

    try {
      xmlBlaster->subscribe(xmlKey.c_str(), qualityOfService.c_str());
    } catch(XmlBlasterException e) {
      cerr << "XmlBlasterException: " << e.errorCodeStr << ": " << e.message << endl;
    }

    cout << "Subscribed to '" << publishOid << "' ..." << endl;


    //-------------- wait for something to happen -------------------
    orb->run ();

  } catch(serverIdl::XmlBlasterException e) {
    cerr << "Caught Server Exception: " << e.errorCodeStr << ": " << e.message << endl;
  } catch(const CORBA::Exception &ex) {
    cerr << "CORBA: " << ex << endl;
  } catch (...) {
    cerr << "some other error" << endl;
  }

  return 0;
}




