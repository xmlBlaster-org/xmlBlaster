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
#include <client/qos/PublishQos.h>
#include <client/key/PublishKey.h>
#include <util/qos/QosData.h>
#include <util/key/KeyData.h>




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
class Dll_Export MessageUnit 
{

   private:
      std::string me() {
         return "MessageUnit";
      }

      org::xmlBlaster::util::key::KeyDataRef key_;

      //std::vector<unsigned char> contentVec_;
      unsigned long len_;
      unsigned char *content_;

      org::xmlBlaster::util::qos::QosDataRef qos_;

      /** Remembers the size of this message, needed e.g. for persistent storage calculation */
      mutable size_t immutableSizeInBytes_;

   public:
      /**
       * Constructs with a 'char *' and its length 'len'. 
       */
      MessageUnit(const org::xmlBlaster::util::key::KeyData &key,
                  unsigned long len,
                  const unsigned char * content, 
                  const org::xmlBlaster::util::qos::QosData &qos);

      /**
       * Constructs a MessageUnit with a std::string. 
       */
      MessageUnit(const org::xmlBlaster::util::key::KeyData &key,
                  const std::string &content, 
                  const org::xmlBlaster::util::qos::QosData &qos);

      /**
       * Constructs a MessageUnit with a std::string and a org::xmlBlaster::client::qos::PublishQos object
       */
      MessageUnit(const org::xmlBlaster::client::key::PublishKey& xmlKey,
                  const std::string &content, 
                  org::xmlBlaster::client::qos::PublishQos& publishQos);

      /**
       * Constructs the message unit. 
       */
      MessageUnit(const org::xmlBlaster::util::key::KeyData &xmlKey,
                  const std::vector<unsigned char> &contentVec, 
                  const org::xmlBlaster::util::qos::QosData &qos);

      /**
       * Constructs the message unit by taking a org::xmlBlaster::client::qos::PublishQos object.
       */
      MessageUnit(const org::xmlBlaster::client::key::PublishKey &xmlKey,
                  const std::vector<unsigned char> &contentVec, 
                  org::xmlBlaster::client::qos::PublishQos& publishQos);

      /**
       * Copy constructor. 
       * <p />
       * The content is cloned but the KeyData and QosData is referenced.
       */
      MessageUnit(const MessageUnit& rhs);

      /**
       * Assignment constructor
       * <p />
       * The content is cloned but the KeyData and QosData is referenced.
       */
      MessageUnit& operator=(const MessageUnit& rhs);

      /**
       * Destructor
       */
      virtual ~MessageUnit();

      /**
       * @return The xml based key, reference counted
       */
      const org::xmlBlaster::util::key::KeyDataRef getKeyRef() const;

      /**
       * The life cycle of the returned object is limited to this MessageUnit. 
       * @return The xml based key
       */
      const org::xmlBlaster::util::key::KeyData& getKey() const;

      /**
       * @return The user data carried with this message
       *         This is created for each invocation so 
       *         use it sparingly
       */
      std::vector<unsigned char> getContentVec() const;

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
       * @return The user data carried with this message as a std::string
       */
      std::string getContentStr() const;

      /**
       * @return The xml based QoS, reference counted
       */
      const org::xmlBlaster::util::qos::QosDataRef getQosRef() const;

      /**
       * The life cycle of the returned object is limited to this MessageUnit. 
       * @return The quality of service of this message. 
       */
      const org::xmlBlaster::util::qos::QosData& getQos() const;

      /**
       * Get the immutable size of this message. 
       */
      size_t getSizeInBytes() const;

      /**
       * Dump state of this object into a XML ASCII std::string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The MessageUnit as a XML ASCII std::string
       */
      std::string toXml(const std::string &extraOffset="") const;
};
}}} // namespace

#endif
