/*-----------------------------------------------------------------------------
Name:      LoginQosWrapper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_LOGINQOSWRAPPER_H
#define _CLIENT_LOGINQOSWRAPPER_H

#include <vector>
#include <client/QosWrapper.h>
#include <util/qos/address/CallbackAddress.h>

/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>publish</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;callback type='IOR'>
 *           IOR:10000010033200000099000010....
 *        &lt;/callback>
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
using namespace std;
using namespace org::xmlBlaster::util::qos::address;

namespace org { namespace xmlBlaster {
   
   class LoginQosWrapper : public QosWrapper {
         
      typedef vector<CallbackAddress> CallbackVector;
      
   private:
      string me() const {
         return "LoginQosWrapper";
      }

   protected:
      // <callback type="IOR>IOR:000122200..."</callback>
         CallbackVector addressVec_; //  = new Vector();
      
      /** PtP messages wanted? */
      bool usePtP_; // <noPtP />  
      // <!-- Don't send me any PtP messages (prevents spamming) -->

      void init(const LoginQosWrapper &el) {
         addressVec_ = el.addressVec_;
         usePtP_     = el.usePtP_;
      }
      
   public:
      /**
       * Default constructor for clients without asynchronous callbacks.
       */
      LoginQosWrapper() : addressVec_() {
         usePtP_ = true;
      }
      

      LoginQosWrapper(const LoginQosWrapper &el) : QosWrapper(el) {
         init(el);
      }

      LoginQosWrapper& operator =(const LoginQosWrapper &el) {
         init(el);
         return *this;
      }
      
      /**
       * Default constructor for transient PtP messages.
       * <p />
       * To make the message persistent, use the
       * @param callback The object containing the callback address.<br />
       *        To add more callbacks, us the addCallbackAddress() method.
       */
      LoginQosWrapper(const CallbackAddress &callback)
         : addressVec_() {
         addCallbackAddress(callback);
         usePtP_ = true;
      }
      
      
      /**
       * @param noPtP You are allowing to receive PtP messages?
       */
      LoginQosWrapper(bool usePtP) : addressVec_() {
         usePtP_ = usePtP;
      }
      
      
      /**
       * Allow to receive Point to Point messages (default).
       */
      void allowPtP() {
         usePtP_ = true;
      }
      
      
      /**
       * I don't want to receive any PtP messages.
       */
      void disallowPtP() {
         usePtP_ = false;
      }
      
      
      /**
       * Add a callback address where to send the message.
       * <p />
       * Note you can invoke this multiple times to allow multiple 
       * callbacks.
       * @param callback  An object containing the protocol (e.g. EMAIL) 
       * and the address (e.g. hugo@welfare.org)
       */
      void addCallbackAddress(const CallbackAddress &callback) {
         addressVec_.insert(addressVec_.end(), callback);
      }
      
      
      /**
       * Converts the data into a valid XML ASCII string.
       * @return An XML ASCII string
       */
      string toString() const {
         return toXml();
      }
      
      
      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @return internal state of the RequestBroker as a XML ASCII string
       */
      string toXml() const {
         return toXml("");
      }
      
      
      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return internal state of the RequestBroker as a XML ASCII string
       */
      string toXml(const string &extraOffset) const {
         string offset = "\n   " + extraOffset, sb = "<qos>\n";
         if (!usePtP_) sb += offset + "   <noPtP />";
         for (string::size_type i=0; i<addressVec_.size(); i++) {
            CallbackAddress ad = addressVec_[i];
            sb += ad.toXml("   ") + "\n";
         }
         sb += "</qos>";
         return sb;
      }
   }; // class
}} // namespace

#endif

















