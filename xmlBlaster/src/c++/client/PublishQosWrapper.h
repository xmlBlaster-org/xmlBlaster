/*-----------------------------------------------------------------------------
  Name:      PublishQosWrapper.h
  Project:   xmlBlaster.org
  Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
  Comment:   Handling one xmlQoS
  ---------------------------------------------------------------------------*/

#ifndef _CLIENT_PUBLISHQOSWRAPPER_H
#define _CLIENT_PUBLISHQOSWRAPPER_H

#include <vector>
#include <string>
#include <boost/lexical_cast.hpp>
#include <client/QosWrapper.h>
#include <util/Destination.h>

using namespace std;
using boost::lexical_cast;

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
    *        &lt;persistent />    
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
      bool   persistent_;
      bool   forceUpdate_;
      bool   readonly_;
      long   expires_;
      long   erase_;
      
      void init() {
         persistent_  = false;
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
       * @param persistent Store the message persistently
       */
      PublishQosWrapper(bool persistent) : QosWrapper(), destVec_() {
         init();
         persistent_ = persistent;
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
      void setPersistent() {
         persistent_ = true;
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
         
         if (expires_ >= 0) 
            ret += "   <expires>\n      " + lexical_cast<string>(expires_) + "\n   </expires>\n";
         if (erase_ >= 0) 
            ret += "   <erase>\n      " + lexical_cast<string>(erase_) + "\n   </erase>\n";
         if (persistent_  )ret += "   <persistent />\n";
         if (forceUpdate_) ret += "   <forceUpdate />\n";
         if (readonly_   ) ret += "   <readonly />\n";
         ret += "</qos>"; 
         return ret;
      }
   };
}} // namespace

#endif

