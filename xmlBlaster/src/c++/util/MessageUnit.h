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

using namespace std;
using org::xmlBlaster::client::qos::PublishQos;

namespace org { namespace xmlBlaster {
namespace util {
   
   /**
    * Holding a message. 
    * <p />
    * This class corresponds to the CORBA serverIdl::MessageUnit struct
    * but uses standard STL only.
    * @since 0.79e
    * @author xmlBlaster@marcelruff.info
    */
   class MessageUnit {

   private:
      string me() {
         return "MessageUnit";
      }

      string key_;

      //vector<unsigned char> contentVec_;
      unsigned long len_;
      unsigned char *content_;

      string qos_;

   public:
      /**
       * Constructs with a 'char *' and its length 'len'. 
       */
      MessageUnit(const string &xmlKey,
                  unsigned long len,
                  const unsigned char * content, 
                  const string &qos="<qos/>");

      /**
       * Constructs a MessageUnit with a string. 
       */
      MessageUnit(const string &xmlKey,
                  const string &content, 
                  const string &qos="<qos/>");

      /**
       * Constructs a MessageUnit with a string and a PublishQos object
       */
      MessageUnit(const string &xmlKey,
                  const string &content, 
                  PublishQos& publishQos);

      /**
       * Constructs the message unit. 
       */
      MessageUnit(const string &xmlKey,
                  const vector<unsigned char> &contentVec, 
                  const string &qos="<qos/>");

      /**
       * Constructs the message unit by taking a PublishQos object.
       */
      MessageUnit(const string &xmlKey,
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
      const string& getKey() const {
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
      const string& getQos() const {
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
