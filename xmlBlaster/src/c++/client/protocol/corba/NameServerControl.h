/*-----------------------------------------------------------------------------
Name:      NameServerControl.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to handle the NameServer stuff (bind, unbind, resolve ...)
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_NAMESERVERCONTROL_H
#define _CLIENT_PROTOCOL_CORBA_NAMESERVERCONTROL_H
using namespace std;
using namespace org::xmlBlaster::util;

#include <vector>
#ifndef STLPORT // Is automatically set by STLport if used, problem is on Linux/g++: STLport-4.5.1/stlport/stl/_algo.h:180: declaration of `operator MICO_LongDouble' as non-function
#  include <algorithm>
#endif

#include <client/protocol/corba/CompatibleCorba.h> // client side headers
#include COSNAMING

#include <util/StringStripper2.h>
#include <util/XmlBlasterException.h>

typedef vector<string> ListType;

namespace org { namespace xmlBlaster {
namespace client { namespace protocol { namespace corba {

/**
 * Class NameServerControl is used to encapsulate methods to access a Name
 * Server (like binding, unbinding, name resolving...). It is a helper which 
 * makes such accesses easier (and less exceptions to take care of).
 */   

   class Dll_Export NameServerControl {

   private:

   string me() {
      return "NameServerControl";
   }

   CosNaming::NamingContext_var namingContext_;
   StringStripper2              stripper_;

   public:
   /**
    * This contructor takes the orb (which must be a valid orb) and two
    * string separators. It retrieves a reference to the NameServer
    * sep1: is the main separator, i.e. which separates the names from
    *       each other.
    * sep2: is the string which separates the name (or id) from the kind
    */ 
   NameServerControl(CORBA::ORB_ptr orb, string sep1="/", string sep2=".") : stripper_(sep1,sep2) {
      // Get naming service
      CORBA::Object_var obj; //  = (CORBA::Object_var)0;
      try {
         obj = orb->resolve_initial_references("NameService");
      }

      catch(const CORBA::ORB::InvalidName ex) {
         cerr << "Thrown invalid name exception: " << ex << endl;
         string txt = me() + ".NameServerControl()";
         string msg = "can't resolve the NameService";
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }

      if(CORBA::is_nil(obj.in())) {
         string txt = me() + ".NameServerControl()";
         string msg = "NameService in not a nil reference";
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }

      try {
         namingContext_ = CosNaming::NamingContext::_narrow(obj.in());
      }
      catch (const CORBA::Exception & ex) {
         string msg="Corba Exception " + to_string(ex);
         string txt = me() + ".NameServerControl()";
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }
      
      if(CORBA::is_nil(namingContext_.in())) {
         string txt = me() + ".NameServerControl()";
         string msg = "NameService is not a NamingContext reference";
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }
   }

   /**
    * @param relativeContext A relative context in the name service
    * @see Other constructor
    */ 
   NameServerControl(CosNaming::NamingContext_var &relativeContext, string sep1="/", string sep2=".") :
      namingContext_(relativeContext), stripper_(sep1,sep2) {
   }

   /**
   * @param name of type "xmlBlaster.MOM/heron.MOM"  sep1="/", sep2="."
   * @return never CORBA::nil
   * @exception On problems or if reference is nil
   * @see #resolve(CosNaming::Name &)
   */
   CORBA::Object_ptr resolve(const string &name) {
      vector<pair<string,string> > nameVector = stripper_.strip(name);
      CosNaming::Name objectName;
      objectName.length(nameVector.size());
      for (string::size_type i=0; i < nameVector.size(); i++) {
         objectName[i].id   = 
            CORBA::string_dup(nameVector[i].first.c_str());
         objectName[i].kind = 
            CORBA::string_dup(nameVector[i].second.c_str());
      }
      return resolve(objectName);
   }
      
   /**
   * Used to resolve a given name. Returns a reference to the object if an 
   * object with the given name exists. Otherwise returns zero.
   * The caller is responsible to free the pointer. 
   * @param nameComponent
   * @return never CORBA::nil, you need to free it!
   * @exception On problems or if reference is nil
   */
   CORBA::Object_ptr resolve(CosNaming::Name &nameComponent) {
      try {
         CORBA::Object_ptr obj = namingContext_->resolve(nameComponent);
         if (CORBA::is_nil(obj)) {
            string txt = "Can't resolve CORBA NameService entry for '" +
                           NameServerControl::getString(nameComponent) +"', entry is nil";
            //log_.error(me() + ".NoAuthService", txt);
            throw org::xmlBlaster::util::XmlBlasterException("communication.noConnection", 
                                       "client", me(), "en", txt);
         }
         return obj;
      }
      catch(const CosNaming::NamingContext::NotFound& ex) {
         string txt = me() + ".resolve()";
         string msg = "CORBA CosNaming::NamingContext::NotFound - name not found exception '" + NameServerControl::getString(nameComponent) + "': " + to_string(ex);
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }
      catch(const CosNaming::NamingContext::CannotProceed& ex) {
         string txt = me() + ".bind()";
         string msg = "CORBA CosNaming::NamingContext::CannotProceed '" + NameServerControl::getString(nameComponent) + "': " + to_string(ex);
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }
      catch(const CosNaming::NamingContext::InvalidName & ex) {
         string txt = me() + ".bind()";
         string msg = "CORBA CosNaming::NamingContext::InvalidName '" + NameServerControl::getString(nameComponent) + "': " + to_string(ex);
         throw XmlBlasterException("communication.noConnection", "client", txt, "en", msg);
      }
   }

   /**
    * @param id For example "xmlBlaster"
    * @param kind For example "MOM"
    * @return never CORBA::nil
    * @see #resolve(CosNaming::Name &)
    */
   CORBA::Object_ptr resolve(const string &id, const string &kind) {
      CosNaming::Name objectName;
      objectName.length(1);
      objectName[0].id =  CORBA::string_dup(id.c_str());
      objectName[0].kind = CORBA::string_dup(kind.c_str());
      return resolve(objectName);
   }

   /**
    * Returns the naming service reference
    * Caller needs to free instance.
    */
   CosNaming::NamingContext_ptr getNamingService() {
      return CosNaming::NamingContext::_duplicate(static_cast<CosNaming::NamingContext_ptr>(namingContext_.in()));
   }

   /**
    * Creates a string representation of a NameService name hierarchy. 
    * This is useful for logging
    * @return e.g. "xmlBlaster.MOM/heron.MOM"
    */ 
   static string getString(CosNaming::Name nameComponent, string sep1="/", string sep2=".") {
      string ret = "";
      for(CORBA::ULong i=0; i<nameComponent.length(); i++) {
         if (i > 0) {
            ret += sep1;
         }
         ret += string(nameComponent[i].id) + 
                     ((nameComponent[i].kind != 0 && *nameComponent[i].kind != 0) ?
                     string(sep2) + string(nameComponent[i].kind) : string(""));
      }
      return ret;
   }

   /**
    * Creates a string representation of a NameService name hierarchy. 
    * This is useful for logging
    * @param id "xmlBlaster"
    * @param kind "MOM"
    * @return e.g. "xmlBlaster.MOM" with sep2_=="."
    */ 
   static string getString(const string &id, const string &kind, string sep2=".") {
      return id + ((kind.size() > 0) ? string(sep2) + kind : string(""));
   }

}; // class NameServerControl

}}}}} // namespace

#endif

