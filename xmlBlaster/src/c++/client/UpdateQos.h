/*-----------------------------------------------------------------------------
Name:      UpdateQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service),knows how to parse it with SAX
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_UPDATEQOS_H
#define _CLIENT_UPDATEQOS_H

#include <util/Log.h>
#include <util/Constants.h>
#include <util/XmlQoSBase.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster {
   
   /**
    * QoS (quality of service) informations sent from server to client<br />
    * via the update() method from the BlasterCallback interface. <p />
    * If you are a c++ client you may use this class to parse the QoS 
    * argument.
    */
   class UpdateQos : public util::XmlQoSBase {
      
   private:
      
      string me() {
            return "UpdateQos";
      }
      
      
      /** helper flag for SAX parsing: parsing inside <state> ? */
      bool inState_; //  = false;
      /** the state of the message */
      string state_; //  = Constants::STATE_OK;
      /** helper flag for SAX parsing: parsing inside <sender> ? */
      bool inSender_; //  = false;
      /** the sender (publisher) of this message (unique loginName) */
      string sender_; //  = null;
      /** helper flag for SAX parsing: parsing inside <subscriptionId> ? */
      bool inSubscriptionId_; //  = false;
      /** If Pub/Sub style update: contains the subscribe ID which caused 
       * this update 
       */
      string subscriptionId_; //  = null;
      
      
   public:
      
      /**
       * Constructs the specialized quality of service object for a 
       * update() call.
       */
      UpdateQos(Global& global, const string &xmlQoS_literal)
               : util::XmlQoSBase(global),
               inState_(false), state_(util::Constants::STATE_OK),  
               inSender_(false), sender_(""),
               inSubscriptionId_(false), subscriptionId_("")
               {

         if (log_.CALL) log_.call(me(), string("Creating UpdateQos(") + 
                                    xmlQoS_literal + ")");
         init(xmlQoS_literal);
      }
      
      
      /**
       * Access sender name.
       * @return loginName of sender
       */
      const string &getSender() const {
         return sender_;
      }
      

      /**
       * Access state of message.
       * @return OK (Other values are not yet supported)
       */
      const string &getState() const {
         return state_;
      }
      

      /**
       * If Pub/Sub style update: contains the subscribe ID which caused 
       * this update
       * @return subscribeId or null if PtP message
       */
      const string &getSubscriptionId() const {
         return subscriptionId_;
      }
      


   private:

      /**
       * returns true if the caller should continue operation, false if the
       * caller should stop the ongoing operation (the caller is startElement).
       */
      bool setFlagForAttribute(const XMLCh *name, const char* attrName, 
                               bool &flag, AttributeList &attrs) {
         if (caseCompare(name, attrName)) {
            if (!inQos_) return false;
            flag = true;
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               char* nameStr   = XMLString::transcode(attrs.getName(i));
               char* valueStr1 = XMLString::transcode(attrs.getValue(i));
               char* valueStr  = charTrimmer_.trim(valueStr);
               delete valueStr1;
               string msg = "Ignoring sent <";
               msg += string(attrName) + "> attribute ";
               msg += string(nameStr) + " = " + valueStr;
               log_.warn(me(), msg);
               delete nameStr;
               delete valueStr;
            }
            return false;
         }
         return true;
      }

   public:

      /**
       * Start element, event from SAX parser.
       * <p />
       * @param name Tag name
       * @param attrs the attributes of the tag
       */
      void startElement(const XMLCh* const name, AttributeList &attrs) {
         XmlQoSBase::startElement(name, attrs);

         if (caseCompare(name,"state")) {
            inState_ = true;
            
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               XMLCh* attrName = XMLString::replicate(attrs.getName(i));
               if ( caseCompare(attrName, "id") ) {
                  XMLCh* oidHelper = xmlChTrimmer_.trim(attrs.getValue(i));
                  char *buffer = XMLString::transcode(oidHelper);
                  state_       = buffer;
                  //log_.warn(me(), "state id = " + state_);
                  delete buffer;
                  delete oidHelper;
               }
               delete attrName;
            }
            return;
         }


         if (!setFlagForAttribute(name, "state" , inState_,  attrs)) return;
         if (!setFlagForAttribute(name, "sender", inSender_, attrs)) return;
         setFlagForAttribute(name,"subscriptionId", inSubscriptionId_, attrs);
      }


      /**
       * End element, event from SAX parser.
       * <p />
       * @param name Tag name
       */
      void endElement(const XMLCh* const name) {
         XmlQoSBase::endElement(name);
         if (caseCompare(name, "state")) {
            inState_ = false;
            /*
            char *stateHelper = charTrimmer_.trim(character_.c_str());
            state_ = stateHelper;
            character_ = "";
            delete stateHelper;
            */
            return;
         }

         if (caseCompare(name, "sender")) {
            inSender_ = false;
            char *senderHelper = charTrimmer_.trim(character_.c_str());
            sender_ = senderHelper;
            character_ = "";
            delete senderHelper;
            return;
         }

         if (caseCompare(name, "subscriptionId")) {
            inSubscriptionId_ = false;
            char *subscriptionHelper = charTrimmer_.trim(character_.c_str());
            subscriptionId_ = subscriptionHelper;
            character_ = "";
            delete subscriptionHelper;
            return;
         }
      }

      
      /**
       * Since xerces ver 1.2
       */
      void setDocumentLocator(const Locator * /*loc*/) {
      }

      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @return internal state of the RequestBroker as a XML ASCII string
       */
      string printOn() {
         return printOn("");
      }


      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return internal state of the RequestBroker as a XML ASCII string
       */
      string printOn(const string &extraOffset) {
         string sb, offset = "\n   ";
         offset += extraOffset;
         sb = offset + "<qos>"; //  <!-- UpdateQos -->";
         if (state_ != "") {
            sb += offset + "   <state id='" + state_ + "'/>";
         }
         if (sender_ != "") {
            sb += offset + "   <sender>" + offset + "      " + sender_;
            sb += offset + "   </sender>";
         }
         if (subscriptionId_ != "") {
            sb += offset + "   <subscriptionId>";
            sb += offset + "      " + subscriptionId_;
            sb += offset + "   </subscriptionId>";
         }
         sb += offset + "</qos>\n";
         return sb;
      }


      string toString() {
         return printOn("");
      }
   };
}} // namespace

#endif

