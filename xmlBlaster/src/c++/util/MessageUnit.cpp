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

using namespace org::xmlBlaster::util::key;
   
/**
 * Holding a message. 
 * <p />
 * This class corresponds to the CORBA serverIdl::MessageUnit struct
 * but uses standard STL only.
 * @since 0.79e
 * @author xmlBlaster@marcelruff.info
 */

/**
 * Constructs with a 'char *' and its length 'len'. 
 */
MessageUnit::MessageUnit(const MsgKeyData &key,
                         unsigned long len,
                         const unsigned char * content, 
                         const MsgQosData &qos)
:key_(key), len_(len), qos_(qos) 
{
   
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
MessageUnit::MessageUnit(const MsgKeyData &key,
                         const string &content, 
                         const MsgQosData &qos)
:key_(key), len_(content.size()), qos_(qos) 
{
   
  content_ = new unsigned char[len_];
  memcpy(content_, content.c_str(), len_);

  //contentVec_.reserve(len_);
  //for (unsigned int ii=0; ii<len_; ii++) {
  //   contentVec_.push_back(content[ii]);
  //}
}


/**
 * Constructs a MessageUnit with a string and a PublishQos object
 */
MessageUnit::MessageUnit(const PublishKey& xmlKey,
                         const string &content, 
                         PublishQos& publishQos)
   : key_(xmlKey.getData()), len_(content.size()), qos_(publishQos.getData())
{
  content_ = new unsigned char[len_];
  memcpy(content_, content.c_str(), len_);
}


/**
 * Constructs the message unit. 
 */
MessageUnit::MessageUnit(const MsgKeyData &xmlKey,
                         const vector<unsigned char> &contentVec, 
                         const MsgQosData &qos)
:key_(xmlKey), /*contentVec_(contentVec),*/ len_(contentVec.size()), qos_(qos) 
{
  content_ = new unsigned char[len_];
  for (unsigned int ii=0; ii<len_; ii++) {
    content_[ii] = contentVec[ii];
  }
}


/**
 * Constructs the message unit by taking a PublishQos object.
 */
MessageUnit::MessageUnit(const PublishKey &xmlKey,
                         const vector<unsigned char> &contentVec, 
                         PublishQos& publishQos)
   : key_(xmlKey.getData()), len_(contentVec.size()), qos_(publishQos.getData())
{
  content_ = new unsigned char[len_];
  for (unsigned int ii=0; ii<len_; ii++) {
    content_[ii] = contentVec[ii];
  }
}

/**
 * Copy constructor
 */
MessageUnit::MessageUnit(const MessageUnit& rhs) 
   : key_(rhs.getKey()), qos_(rhs.getQos())
{
  //contentVec = rhs.getContentVec();
  len_ = rhs.getContentLen();
  content_ = new unsigned char[len_];
  memcpy(content_, rhs.getContent(), len_);
}

/**
 * Assignment constructor
 */
MessageUnit& 
MessageUnit::operator=(const MessageUnit& rhs) 
{
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
MessageUnit::~MessageUnit() 
{
  delete [] content_;
}


/**
 * @return The user data carried with this message
 *         This is created for each invocation so 
 *         use it sparingly
 */
vector<unsigned char> 
MessageUnit::getContentVec() const 
{
  //return contentVec_;
  vector<unsigned char> vec;
  vec.reserve(len_);
  for (unsigned int ii=0; ii<len_; ii++) {
    vec.push_back(content_[ii]);
  }
  return vec;
}

/**
 * Dump state of this object into a XML ASCII string.
 * <br>
 * @param extraOffset indenting of tags for nice output
 * @return The MessageUnit as a XML ASCII string
 */
string MessageUnit::toXml(const string &extraOffset) const
{
   string ret    = "\n   ";
   string offset = extraOffset;

   ret += offset + "<MessageUnit>";
//   ret += offset + "  <key>" + getKey() + "</key>";
   ret += offset + getKey().toXml(extraOffset);
   ret += offset + "  <content>" + getContentStr() + "</content>";
//   ret += offset + "  <qos>" + getQos() + "</qos>";
   ret += offset + getQos().toXml(extraOffset);
   ret += offset + "</MessageUnit>";
   return ret;
}

#endif
