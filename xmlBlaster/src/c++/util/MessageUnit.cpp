/*-----------------------------------------------------------------------------
Name:      MessageUnit.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding a message
-----------------------------------------------------------------------------*/

#ifndef _UTIL_MESSAGEUNIT_C
#define _UTIL_MESSAGEUNIT_C

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif
 
#include <string>
#include <vector>
#include <util/Log.h>
#include <util/MessageUnit.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

namespace org { namespace xmlBlaster { namespace util {

MessageUnit::MessageUnit(const KeyData &key,
                         unsigned long len,
                         const unsigned char * content, 
                         const QosData &qos)
   : key_(key.getClone()),
     len_(len), content_(0),
     qos_(qos.getClone()),
     immutableSizeInBytes_(0)
{
  if (len_ > 0) {
     content_ = new unsigned char[len_];
     memcpy(content_, content, len_);
  }
}

/**
 * Constructs a MessageUnit with a string. 
 */
MessageUnit::MessageUnit(const KeyData &key,
                         const string &content, 
                         const QosData &qos)
   : key_(key.getClone()),
     len_(content.size()), content_(0),
     qos_(qos.getClone()),
     immutableSizeInBytes_(0)
{
   if (len_ > 0) {
      content_ = new unsigned char[len_];
      memcpy(content_, content.c_str(), len_);
   }
}


/**
 * Constructs a MessageUnit with a string and a PublishQos object
 */
MessageUnit::MessageUnit(const PublishKey& xmlKey,
                         const string &content, 
                         PublishQos& publishQos)
   : key_(xmlKey.getData().getClone()),
     len_(content.size()),
     content_(0),
     qos_(publishQos.getData().getClone()),
     immutableSizeInBytes_(0)
{
   if (len_ > 0) {
      content_ = new unsigned char[len_];
      memcpy(content_, content.c_str(), len_);
   }
}


/**
 * Constructs the message unit. 
 */
MessageUnit::MessageUnit(const KeyData &xmlKey,
                         const vector<unsigned char> &contentVec, 
                         const QosData &qos)
     : key_(xmlKey.getClone()),
       len_(contentVec.size()), content_(0),
       qos_(qos.getClone()),
       immutableSizeInBytes_(0)
{
   if (len_ > 0) {
      content_ = new unsigned char[len_];
      for (unsigned int ii=0; ii<len_; ii++) {
         content_[ii] = contentVec[ii];
      }
   }
}


/**
 * Constructs the message unit by taking a PublishQos object.
 */
MessageUnit::MessageUnit(const PublishKey &xmlKey,
                         const vector<unsigned char> &contentVec, 
                         PublishQos& publishQos)
   : key_(xmlKey.getData().getClone()),
     len_(contentVec.size()), content_(0),
     qos_(publishQos.getData().getClone()),
     immutableSizeInBytes_(0)
{
   if (len_ > 0) {
      content_ = new unsigned char[len_];
      for (unsigned int ii=0; ii<len_; ii++) {
         content_[ii] = contentVec[ii];
      }
   }
}

/**
 * Copy constructor
 */
MessageUnit::MessageUnit(const MessageUnit& rhs) 
   : key_(rhs.getKeyRef()),
     len_(rhs.getContentLen()),
     content_(0),
     qos_(rhs.getQosRef()),
     immutableSizeInBytes_(rhs.getSizeInBytes())
{
   if (len_ > 0) {
      content_ = new unsigned char[len_];
      memcpy(content_, rhs.getContent(), len_);
   }
}

/**
 * Assignment constructor
 */
MessageUnit& MessageUnit::operator=(const MessageUnit& rhs) 
{
   if (this != &rhs) {
      key_ = rhs.getKeyRef();
      len_ = rhs.getContentLen();
      if (len_ > 0) {
         content_ = new unsigned char[len_];
         memcpy(content_, rhs.getContent(), len_);
      }
      qos_ = rhs.getQosRef();
      immutableSizeInBytes_ = rhs.getSizeInBytes();
   }
   return *this;
}

MessageUnit::~MessageUnit() 
{
  if (content_ != 0)
     delete [] content_;
}

const KeyData& MessageUnit::getKey() const
{
   return *key_;
}

const KeyDataRef MessageUnit::getKeyRef() const
{
   return key_;
}

vector<unsigned char> MessageUnit::getContentVec() const 
{
  vector<unsigned char> vec;
  if (len_ > 0) {
     vec.reserve(len_);
     for (unsigned int ii=0; ii<len_; ii++) {
       vec.push_back(content_[ii]);
     }
  }
  return vec;
}

size_t MessageUnit::getSizeInBytes() const
{
   if (immutableSizeInBytes_ > 0) return immutableSizeInBytes_;
   // See org.xmlBlaster.engine.MsgUnitWrapper.java
   immutableSizeInBytes_ = 306 + len_ + key_->toXml().size() + qos_->toXml().size();
   return immutableSizeInBytes_;
}

std::string MessageUnit::getContentStr() const {
   if (len_ < 1) {
      return "";
   }
   return std::string(reinterpret_cast<const char *>(content_), static_cast<std::string::size_type>(len_));
}

const QosData& MessageUnit::getQos() const
{
   return *qos_;
}

const QosDataRef MessageUnit::getQosRef() const
{
   return qos_;
}

string MessageUnit::toXml(const string &extraOffset) const
{
   string ret;
   string offset = Constants::OFFSET + extraOffset;

   ret += offset + "<MessageUnit>";
   ret += getKey().toXml(Constants::INDENT+extraOffset);
   if (len_ > 0) {
      ret += offset + " <content>" + getContentStr() + "</content>";
   }
   ret += getQos().toXml(Constants::INDENT+extraOffset);
   ret += offset + "</MessageUnit>";
   return ret;
}

}}} // namespace
#endif
