/*-----------------------------------------------------------------------------
  Name:      PublishQosWrapper.h
  Project:   xmlBlaster.org
  Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
  Comment:   Handling one xmlQoS
  Version:   $Id: PublishQosWrapper.h,v 1.3 2001/11/26 09:20:59 ruff Exp $
  ---------------------------------------------------------------------------*/

#ifndef _CLIENT_PUBLISHQOSWRAPPER_H
#define _CLIENT_PUBLISHQOSWRAPPER_H

#include <vector>
#include <string>
#include <strstream.h>
#include <client/QosWrapper.h>
#include <util/Destination.h>

namespace org { namespace xmlBlaster {
   
   /**
    * This class encapsulates the qos of a publish() message.
    * <p />
    * So you don't need to type the 'ugly' XML ASCII string by yourself.
    * After construction access the ASCII-XML string with the toXml() method.
    * <br />
    * A typical <b>publish</b> qos could look like this:<br />
    * <pre>
    *     &lt;qos>
    *        &lt;destination queryType='EXACT'>
    *           Tim
    *        &lt;/destination>
    *        &lt;isDurable />    
    *        &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
    *     &lt;/qos>
    * </pre>
    * <p />
    * see xmlBlaster/src/dtd/XmlQoS.xml
    */
   
   class PublishQosWrapper : public QosWrapper {
      
      typedef vector<util::Destination> Vector;
      
   private:
      string me() {
	 return "PublishQosWrapper";
      }
      
      Vector destVec_; 
      bool   isDurable_;
      bool   forceUpdate_;
      bool   readonly_;
      long   expires_;
      long   erase_;
      
      void init() {
	 isDurable_   = false;
	 forceUpdate_ = false;
	 readonly_    = false;
	 expires_     = -99;
	 erase_       = -99;
      }
      
   public:
      /**
       * Default constructor for transient messages.
       */
      PublishQosWrapper() : QosWrapper(), destVec_() {
	 init();
      }
      
      
      /**
       * Default constructor for transient PtP messages.
       * <p />
       * To make the message persistent, use the
       * @param destination The object containing the destination address.
       * <br />
       *        To add more destinations, us the addDestination() method.
       */
      
      PublishQosWrapper(const util::Destination &destination) : 
	 QosWrapper(), destVec_() {
	 init();
	 addDestination(destination);
      }
      
      
      /**
       * @param isDurable Store the message persistently
       */
      PublishQosWrapper(bool isDurable) : QosWrapper(), destVec_() {
	 init();
	 isDurable_ = isDurable;
      }
      
      
      /**
       * Mark a message to be updated even that the content didn't change.
       * <br />
       * Default is that xmlBlaster doesn't send messages to subscribed 
       * clients, if the message didn't change.
       */
      void setForceUpdate() {
	 forceUpdate_ = true;
      }

      
      /**
       * Mark a message to be readonly.
       * <br />
       * Only the first publish() will be accepted, followers are denied.
       */
      void setReadonly() {
	 readonly_ = true;
      }

      
      /**
       * Mark a message to be persistent.
       */
      void setDurable() {
	 isDurable_ = true;
      }


      /**
       * Add a destination where to send the message.
       * <p />
       * Note you can invoke this multiple times to send to multiple 
       * destinations.
       * @param destination  The loginName of a receiver or some destination 
       * XPath query
       */
      void addDestination(const util::Destination &destination) {
	 destVec_.insert(destVec_.end(), destination);
      }
      
      
      /**
       * Converts the data into a valid XML ASCII string.
       * @return An XML ASCII string
       */
      string toString() {
	 return toXml();
      }


      /**
       * Converts the data into a valid XML ASCII string.
       * @return An XML ASCII string
       */
      string toXml() {
	 string ret = "<qos>\n";
	 int nmax = destVec_.size();
	 
	 for (int i=0; i<nmax; i++) ret += destVec_[i].toXml("    ") + "\n";
	 
	 char buffer[512];
	 ostrstream out(buffer, 511);
	 if (expires_ >= 0) 
	    out << "   <expires>\n      " << expires_ << "\n   </expires>\n";
	 if (erase_ >= 0) 
	    out << "   <erase>\n      " << erase_ << "\n   </erase>\n";
	 out << (char)0;
	 ret += buffer;
	 if (isDurable_  ) ret += "   <isDurable />\n";
	 if (forceUpdate_) ret += "   <forceUpdate />\n";
	 if (readonly_   ) ret += "   <readonly />\n";
	 ret += "</qos>"; 
	 return ret;
      }
   };
}} // namespace

#endif

