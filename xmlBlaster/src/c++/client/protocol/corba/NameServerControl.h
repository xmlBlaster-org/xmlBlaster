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

#define CLIENT_HEADER generated/xmlBlaster
#include <client/protocol/corba/CompatibleCorba.h>
#include COSNAMING

#include <util/StringStripper2.h>

typedef vector<string> ListType;

//  namespace util {

//  /**
//   * NSControlException is THE exception class used by NameServerControl. It 
//   * encapsulates all exceptions which might be thrown by the NameServer in
//   * such a way that when one of the methods of NameServerControl get an 
//   * exception, it catches it (evaluates it) and throws then a 
//   * NSControlException. This way, when using NameServerControl you only need to
//   * bother about this exception.
//   */
   
//        class NSControlException {

//       /**
//        * List of all known exceptions
//        * ==========================================================
//        * 1, "can't resolve `NameService'"
//        * 2, "`NameService' is a nil obj.ref"
//        * 3, "`NameService' is not a NamingContext object reference"
//        * 4, "Invalid Name `NameService'"
//        * 5, "can't proceed `NameService`"
//        * 6, "Got a `NotFound' exception"
//        * 7, "Already bound"
//        */

//        private:
//       unsigned short code_;
//       string         text_;

//        public:
//       NSControlException(unsigned short code, string text) {
//          text_ = text;
//          code_ = code;
//       }

//       NSControlException(const NSControlException &ex) {
//          text_ = ex.text_;
//          code_ = ex.code_;
//       }

//       NSControlException& operator =(const NSControlException &ex) {
//          text_ = ex.text_;
//          code_ = ex.code_;
//          return *this;
//       }

//       unsigned short getCode() const {
//          return code_;
//       }

//       string getReason() const {
//          return text_;
//       }

//        };
//  };

      
//  /**
//   * Prints the error message to an output stream
//   */
//  ostream &operator << (ostream &out, const util::NSControlException &ex) {
//     out << ex.getCode() << ": " << ex.getReason();
//     return out;
//  }


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

         CORBA::ORB_var               orb_;
         CosNaming::NamingContext_var namingContext_;
         StringStripper2              stripper_;
         ListType                     nameList_;
         bool                         keepBindingsAfterDeath_;

      public:
         /**
          * This contructor takes the orb (which must be a valid orb) and two
          * string separators. It retrieves a reference to the NameServer
          * sep1: is the main separator, i.e. which separates the names from
          *       each other.
          * sep2: is the string which separates the name (or id) from the kind
          */ 
         NameServerControl(CORBA::ORB_ptr orb, string sep1="/", 
                           string sep2=".", bool keepBindingsAfterDeath=false) :
            stripper_(sep1,sep2), nameList_() {
            /*
            //CORBA::Object_var myserv = orb->string_to_object ("corbaname::localhost:7608#xmlBlaster-Authenticate.MOM");
            CORBA::Object_var myserv = orb->string_to_object ("corbaname::localhost:7608/NameService#xmlBlaster-Authenticate.MOM");
            cout << "SUCCESS for corbaname::localhost:7608#xmlBlaster-Authenticate.MOM" << endl;
            */
            orb_ = CORBA::ORB::_duplicate(orb);
            keepBindingsAfterDeath = true;
            // Get naming service
            CORBA::Object_var obj; //  = (CORBA::Object_var)0;
            try {
               obj = orb->resolve_initial_references("NameService");
            }

            catch(const CORBA::ORB::InvalidName ex) {
               cerr << "Thrown invalid name exception: " << ex << endl;
//             throw NSControlException(1, "can't resolve `NameService'");
               string txt = me() + ".NameServerControl()";
               string msg = "can't resolve the NameService";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
      
            if(CORBA::is_nil(obj.in())) {
//             throw NSControlException(2, "`NameService' is a nil obj.ref");
               string txt = me() + ".NameServerControl()";
               string msg = "NameService in not a nil reference";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
      
            try {
               namingContext_ = CosNaming::NamingContext::_narrow(obj.in());
            }
            catch (const CORBA::Exception & /*ex*/) {
               string msg="Corba Exception";
#ifdef ORBACUS // FUTURE_CHECK
               msg = ex._name();
               msg += ex._rep_id();
               cerr << msg << endl;
//             throw NSControlException(20, msg);
#endif
               string txt = me() + ".NameServerControl()";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
            
            if(CORBA::is_nil(namingContext_.in())) {
//             throw NSControlException(3,
//                "`NameService' is not a NamingContext object reference");
               string txt = me() + ".NameServerControl()";
               string msg = "NameService is not a NamingContext reference";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
         }

/**
 * bindContext takes a CosNaming::Name parameter (name) and assumes that it 
 * is a complete name for an object to be bound. So this method just eludes
 * the last element in the name (because it is assumed to be THE object)
 * and binds the rest of the name (if you want the prefix) as a context.
 * If the name is already bound and it is a context, then it just does not 
 * bother about it. If the name is already bound, but it is an object (which 
 * means no context), then a NSControlException is thrown.
 */
         void bindContext(CosNaming::Name &name) {
            CosNaming::NamingContext_var nc = namingContext_;
            
            try {
               for (string::size_type i=1;  i < name.length(); i++) {
                  CosNaming::Name contextName;
                  contextName.length(i);
                  for (string::size_type j=0; j < i; j++) {
                     contextName[j].id   = CORBA::string_dup(name[j].id);
                     contextName[j].kind = CORBA::string_dup(name[j].kind);
                  }
                  nc->bind_new_context(contextName);
               }
            }
            catch(const CosNaming::NamingContext::AlreadyBound&) {
               // ignore this (this happens all the time !!!)
            }
            catch(const CosNaming::NamingContext::InvalidName & /*ex*/ ) {
//             throw NSControlException(4,"1. invalid name exception");
               string txt = me() + ".bindContext(...)";
               string msg = "invalid name exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
            catch(const CosNaming::NamingContext::CannotProceed&) {
//             throw NSControlException(5,"1. can not proceed exception");
               string txt = me() + ".bindContext(...)";
               string msg = "can not proceed exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
            catch(const CosNaming::NamingContext::NotFound&) {
//             throw NSControlException(6,"1. name not found exception");
               string txt = me() + ".bindContext(...)";
               string msg = "name not found exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
         }
            
        
/**
 * binds the name specified in name with the object specified in objVar.
 * rebound=true (default) means that if the binding already exists it is 
 * first removed and then rebound. If rebound=false, then the method throws
 * an exception if the name is already bounded.
 */
         void bind(const string &name, CORBA::Object_ptr objVar, 
                   bool rebound=true) {
            vector<pair<string,string> > nameVector = stripper_.strip(name);
            
            CosNaming::NamingContext_var nc = namingContext_, 

            nc1 = nc;
            pair<string,string> namePair;
            CosNaming::Name     objectName;
            objectName.length(nameVector.size());

            for (string::size_type i=0; i < nameVector.size(); i++) {
               objectName[i].id   = 
                  CORBA::string_dup(nameVector[i].first.c_str());
               objectName[i].kind = 
                  CORBA::string_dup(nameVector[i].second.c_str());
            }

            bindContext(objectName);
           
            if (rebound) {
               try {
                  nc->unbind(objectName);
                  ListType::iterator 
                     iter = find(nameList_.begin(),nameList_.end(),name);
                  if (iter != nameList_.end()) nameList_.erase(iter);
               }

               catch(const CosNaming::NamingContext::NotFound&) {
                  // ignore this (happens all the time)
               }
            }
            try {
               nc->bind(objectName, objVar);
               nameList_.insert(nameList_.end(), name);
            }
            catch(const CosNaming::NamingContext::NotFound&) {
//             throw NSControlException(6,"1. name not found exception");
               string txt = me() + ".bind()";
               string msg = "name not found exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
         }

/**
 * unbind unbinds the name specified in the argument list. If the evenExternal
 * is true (default), then the name is unbound even if the name is not in the
 * (internal) list of names which have been bound by this object. If
 * evenExternal is false, only names which have been bound by this object are
 * unbound.
 */
         void unbind(const string &name, bool evenExternal=true) {
            vector<pair<string,string> > nameVector = stripper_.strip(name);
            CosNaming::NamingContext_var nc = namingContext_;
 
            pair<string,string> namePair;
            CosNaming::Name     objectName;
            objectName.length(nameVector.size());

            for (string::size_type i=0; i < nameVector.size(); i++) {
               objectName[i].id   = 
                  CORBA::string_dup(nameVector[i].first.c_str());
               objectName[i].kind = 
                  CORBA::string_dup(nameVector[i].second.c_str());
            }
           
            ListType::iterator 
               iter = find(nameList_.begin(),nameList_.end(),name);
            if (iter != nameList_.end()) {
               nameList_.erase(iter);
               nc->unbind(objectName);
            }
            else {
               if (evenExternal) nc->unbind(objectName);
            }
         }

/**
 * Used to resolve a given name. Returns a reference to the object if an 
 * object with the given name exists. Otherwise returns zero.
 * The caller is responsible to free the pointer. 
 */
         CORBA::Object_ptr resolve(const string &name) {

            vector<pair<string,string> > nameVector = stripper_.strip(name);
            CosNaming::Name     objectName;
            objectName.length(nameVector.size());

            for (string::size_type i=0; i < nameVector.size(); i++) {
               objectName[i].id   = 
                  CORBA::string_dup(nameVector[i].first.c_str());
               objectName[i].kind = 
                  CORBA::string_dup(nameVector[i].second.c_str());
            }
            
            try {
               return namingContext_->resolve(objectName);
            }
            catch(const CosNaming::NamingContext::NotFound& /*ex*/) {
//             throw NSControlException(6,"resolve: name not found exception");
               string txt = me() + ".resolve()";
               string msg = "name not found exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
            catch(const CosNaming::NamingContext::CannotProceed&) {
//             throw NSControlException(5,"resolve: can't proceed exception");
               string txt = me() + ".bind()";
               string msg = "can't proceed exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
            catch(const CosNaming::NamingContext::InvalidName & /*ex*/ ) {
//             throw NSControlException(4,"resolve: invalid name exception");
               string txt = me() + ".bind()";
               string msg = "invalid name exception";
               throw serverIdl::XmlBlasterException("communication.noConnection", "client", txt.c_str(), "en",
                      msg.c_str(), "", "", "", "", "", "");
            }
         }
         

/**
 * Returns the naming service reference
 */
         CosNaming::NamingContext_ptr getNamingService() {
            return CosNaming::NamingContext::_duplicate(static_cast<CosNaming::NamingContext_ptr>(namingContext_));
         }


/**
 * The distructor unbinds all bindings which have been created by this object
 * unless the keepBindingsAfterDeath parameter is set (which is done in the
 * constructor).
 */
         ~NameServerControl() {
            if (!keepBindingsAfterDeath_) {
               while (nameList_.begin() != nameList_.end()) 
                  unbind(*nameList_.begin(), false);
            }
         }
            
      };
}}}}} // namespace

#endif

