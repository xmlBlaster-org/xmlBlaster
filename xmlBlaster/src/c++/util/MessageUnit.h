/*-----------------------------------------------------------------------------
Name:      MessageUnit.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding a message
-----------------------------------------------------------------------------*/

#ifndef _UTIL_MESSAGEUNIT_H
#define _UTIL_MESSAGEUNIT_H
 
#include <string>
#include <vector>
#include <util/Log.h>
#include <client/qos/PublishQos.h>
#include <client/key/PublishKey.h>
#include <util/qos/MsgQosData.h>
#include <util/key/MsgKeyData.h>


using namespace std;
using org::xmlBlaster::client::qos::PublishQos;
using org::xmlBlaster::client::key::PublishKey;
using org::xmlBlaster::util::key::MsgKeyData;
using org::xmlBlaster::util::qos::MsgQosData;

namespace org { namespace xmlBlaster { namespace util {
   
   /**
    * Holding a message. 
    * <p />
    * This class corresponds to the CORBA serverIdl::MessageUnit struct
    * but uses standard STL only.
    * @since 0.79e
    * @author xmlBlaster@marcelruff.info
    * @author laghi@swissinfo.org
    */
   class MessageUnit 
   {

   private:
      string me() {
         return "MessageUnit";
      }

//      string key_;
      MsgKeyData key_;

      //vector<unsigned char> contentVec_;
      unsigned long len_;
      unsigned char *content_;

//      string qos_;
      MsgQosData qos_;

   public:
      /**
       * Constructs with a 'char *' and its length 'len'. 
       */
      MessageUnit(const MsgKeyData &key,
                  unsigned long len,
                  const unsigned char * content, 
                  const MsgQosData &qos);

      /**
       * Constructs a MessageUnit with a string. 
       */
      MessageUnit(const MsgKeyData &key,
                  const string &content, 
                  const MsgQosData &qos);

      /**
       * Constructs a MessageUnit with a string and a PublishQos object
       */
      MessageUnit(const PublishKey& xmlKey,
                  const string &content, 
                  PublishQos& publishQos);

      /**
       * Constructs the message unit. 
       */
      MessageUnit(const MsgKeyData &xmlKey,
                  const vector<unsigned char> &contentVec, 
                  const MsgQosData &qos);

      /**
       * Constructs the message unit by taking a PublishQos object.
       */
      MessageUnit(const PublishKey &xmlKey,
                  const vector<unsigned char> &contentVec, 
                  PublishQos& publishQos);

      /**
       * Copy constructor
       */
      MessageUnit(const MessageUnit& rhs);

      /**
       * Assignment constructor
       */
      MessageUnit& operator=(const MessageUnit& rhs);

      /**
       * Destructor
       */
      virtual ~MessageUnit();

      /**
       * @return The xml based key
       */
      const MsgKeyData& getKey() const {
         return key_;
      }

      /**
       * @return The user data carried with this message
       *         This is created for each invocation so 
       *         use it sparingly
       */
      vector<unsigned char> getContentVec() const;

      /**
       * Access the raw user data,
       * use getContentLen() to access the length
       */
      const unsigned char *getContent() const {
         return content_;
      }

      /**
       * Access the length of the raw user data
       */
      unsigned long getContentLen() const {
         return len_;
      }

      /**
       * @return The user data carried with this message as a string
       */
      string getContentStr() const {
         //return string(contentVec_.begin(), contentVec_.end());
         return string(reinterpret_cast<const char * const>(content_), static_cast<unsigned int>(len_));
      }

      /**
       * @return The quality of service of this message. 
       */
      const MsgQosData& getQos() const {
         return qos_;
      }

      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The MessageUnit as a XML ASCII string
       */
      string toXml(const string &extraOffset="");
   };
}}} // namespace

#endif
