/*-----------------------------------------------------------------------------
Name:      UpdateQoS.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service),knows how to parse it with SAX
Version:   $Id: UpdateQoS.h,v 1.4 2000/09/15 17:16:11 ruff Exp $
-----------------------------------------------------------------------------*/

//  package org.xmlBlaster.client;

//  import org.xmlBlaster.util.Log;
//  import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
//  import org.xml.sax.AttributeList;

#ifndef _CLIENT_UPDATEQOS_H
#define _CLIENT_UPDATEQOS_H

#include <util/XmlQoSBase.h>

namespace client {
   
   /**
    * QoS (quality of service) informations sent from server to client<br />
    * via the update() method from the BlasterCallback interface. <p />
    * If you are a c++ client you may use this class to parse the QoS 
    * argument.
    */
   class UpdateQoS : public util::XmlQoSBase {
      
   private:
      
      string me() {
	    return "UpdateQoS";
      }
      
      
      /** helper flag for SAX parsing: parsing inside <state> ? */
      bool inState_; //  = false;
      /** the state of the message */
      string state_; //  = null;
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
      UpdateQoS(const string &xmlQoS_literal, int args=0, char *argc[]=0) 
	 : util::XmlQoSBase(args, argc) {
	 if (log_.CALL) log_.call(me(), string("Creating UpdateQoS(") + 
				    xmlQoS_literal + ")");
	 //if (Log.CALL) Log.call(ME, "Creating UpdateQoS()");
	 inState_          = false;
	 state_            = "";
	 inSender_         = false;
	 sender_           = "";
	 inSubscriptionId_ = false;
	 subscriptionId_   = "";
	 init(xmlQoS_literal);
      }
      
      
      /**
       * Access sender name.
       * @return loginName of sender
       */
      string getSender() {
	 return sender_;
      }
      

      /**
       * Access state of message.
       * @return OK (Other values are not yet supported)
       */
      string getState() {
	 return state_;
      }
      

      /**
       * If Pub/Sub style update: contains the subscribe ID which caused 
       * this update
       * @return subscribeId or null if PtP message
       */
      string getSubscriptionId() {
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
	 util::XmlQoSBase::startElement(name, attrs);
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
	 util::XmlQoSBase::endElement(name);
	 if (caseCompare(name, "state")) {
	    inState_ = false;
	    char *stateHelper = charTrimmer_.trim(character_.c_str());
	    state_ = stateHelper;
	    character_ = "";
	    delete stateHelper;
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
      void setDocumentLocator(const Locator *loc) {
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
	 sb = offset + "<qos>"; //  <!-- UpdateQoS -->";
	 if (state_ != "") {
	    sb += offset + "   <state>" + offset + "      " + state_;
	    sb += offset + "   </state>";
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
};

#endif

