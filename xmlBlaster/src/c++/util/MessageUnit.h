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

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
   
   /**
    * Holding a message. 
    * <p />
    * This class corresponds to the CORBA serverIdl::MessageUnit struct
    * but uses standard STL only.
    * @since 0.79e
    * @author ruff@swand.lake.de
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
                  const string &qos="<qos/>") :
                        key_(xmlKey), len_(len), qos_(qos) {
         
         content_ = new unsigned char[len_];
         memcpy(content_, content, len_);

         //contentVec_.reserve(len);
         //for (unsigned int ii=0; ii<len; ii++) {
         //   contentVec_.push_back(content[ii]);
         //}
      }

      /**
       * Constructs a MessageUnit with a string. 
       */
      MessageUnit(const string &xmlKey,
                  const string &content, 
                  const string &qos="<qos/>") :
                        key_(xmlKey), len_(content.size()), qos_(qos) {
         
         content_ = new unsigned char[len_];
         memcpy(content_, content.c_str(), len_);

         //contentVec_.reserve(len_);
         //for (unsigned int ii=0; ii<len_; ii++) {
         //   contentVec_.push_back(content[ii]);
         //}
      }

      /**
       * Constructs the message unit. 
       */
      MessageUnit(const string &xmlKey,
                  const vector<unsigned char> &contentVec, 
                  const string &qos="<qos/>") :
                    key_(xmlKey), /*contentVec_(contentVec),*/ len_(contentVec.size()), qos_(qos) {
         content_ = new unsigned char[len_];
         for (unsigned int ii=0; ii<len_; ii++) {
            content_[ii] = contentVec[ii];
         }
      }

      /**
       * Copy constructor
       */
      MessageUnit(const MessageUnit& rhs) {
         key_ = rhs.getKey();
         //contentVec = rhs.getContentVec();
         len_ = rhs.getContentLen();
         content_ = new unsigned char[len_];
         memcpy(content_, rhs.getContent(), len_);
         qos_ = rhs.getQos();
      }

      /**
       * Assignment constructor
       */
      MessageUnit& operator=(const MessageUnit& rhs) {
         if (this != &rhs) {
            key_ = rhs.getKey();
            //contentVec = rhs.getContentVec();
            len_ = rhs.getContentLen();
            content_ = new unsigned char[len_];
            memcpy(content_, rhs.getContent(), len_);
            qos_ = rhs.getQos();
         }
         return *this;
      }

      /**
       * Destructor
       */
      virtual ~MessageUnit() {
         delete [] content_;
      }

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
      vector<unsigned char> getContentVec() const {
         //return contentVec_;
         vector<unsigned char> vec;
         vec.reserve(len_);
         for (unsigned int ii=0; ii<len_; ii++) {
            vec.push_back(content_[ii]);
         }
         return vec;
      }

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
      string toXml(const string &extraOffset="") {
         string ret    = "\n   ";
         string offset = extraOffset;

         ret += offset + "<MessageUnit>";
         ret += offset + "  <key>" + getKey() + "</key>";
         ret += offset + "  <content>" + getContentStr() + "</content>";
         ret += offset + "  <qos>" + getQos() + "</qos>";
         ret += offset + "</MessageUnit>";
         return ret;
      }
   };
}}} // namespace

#endif
