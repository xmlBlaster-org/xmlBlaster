/*------------------------------------------------------------------------------
Name:      ClientProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
#include <typeinfo>
#include <vector>
//#include <algorithm>
#include <sstream>
#include <iostream>
#include <util/qos/ClientProperty.h>
#include <util/Base64.h>
#include <util/Constants.h>
#include <util/lexical_cast.h>

using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

ClientProperty::ClientProperty(bool /*dummy*/,
                  const std::string& name,
                  const std::string& type,
                  const std::string& encoding
                  )
   : name_(name),
     value_(""),
     encoding_(encoding),
     type_(type)
{
}

ClientProperty::ClientProperty(const std::string& name,
                     const char *value,
                     const std::string& type)
   : name_(name),
     value_(value),
     encoding_(""),
     type_(type)
{
   if (needsEncoding()) {
//#     if defined(__sun)
#     if defined(__SUNPRO_CC)
      std::vector<unsigned char> vec; // TODO: Better workaround for SunOS CC Sun C++ 5.5 2003/03/12
      int len = strlen(value);
      for (int i=0; i<len; i++) {
         vec.push_back(value[i]);
      }
#     else
      std::vector<unsigned char> vec(value, value+strlen(value));
#     endif
      encoding_ = Constants::ENCODING_BASE64;
      value_ = Base64::Encode(vec);
   }
   else
      value_ = value;
}

ClientProperty::ClientProperty(const std::string& name,
                     const std::vector<unsigned char>& value,
                     const std::string& type)
   : name_(name),
     value_(""),
     encoding_(""),
     type_(type)
{
   if (type_ == "") {
      type_ = Constants::TYPE_BLOB;
   }
   encoding_ = Constants::ENCODING_BASE64;
   value_ = Base64::Encode(value);
}
/*
ClientProperty::ClientProperty(const std::string& name,
                  const std::string& value,
                  const std::string& type,
                  const std::string& encoding)
   : name_(name),
     value_(value),
     encoding_(encoding),
     type_(type)
{
}
*/
bool ClientProperty::needsEncoding() const {
   if (type_ == Constants::TYPE_BLOB || encoding_ == Constants::ENCODING_BASE64)
      return true;
   else if (
        value_.find("<") != std::string::npos ||
        value_.find("&") != std::string::npos ||
        value_.find("]]>") != std::string::npos
      ) {
      return true;
   }
   return false;
}

const std::string& ClientProperty::getName() const {
   return name_;
}

std::string ClientProperty::getType() const {
   return type_;
}

std::string ClientProperty::getEncoding() const {
   return encoding_;
}

bool ClientProperty::isBase64() const {
   return encoding_ == Constants::ENCODING_BASE64;
}

std::string ClientProperty::getValueRaw() const {
   return value_;
}

std::string ClientProperty::getStringValue() const {
   if (value_ == "") return "";
   if (Constants::ENCODING_BASE64 == encoding_) {
      std::vector<unsigned char> vec = Base64::Decode(value_);
      std::string str;
      str.reserve(vec.size());
#     if defined(__sun)
         std::vector<unsigned char>::const_iterator it;
         for(it = vec.begin(); it != vec.end(); ++it) {
            unsigned char c = (*it);
            str += c;
         }
#     else
         str.assign(vec.begin(),vec.end());
#     endif
      return str;
   }
   return value_;
}

std::vector<unsigned char> ClientProperty::getValue() const {
   if (value_ == "") std::vector<unsigned char>();
   if (Constants::ENCODING_BASE64 == encoding_) {
      return Base64::Decode(value_);
   }
   std::vector<unsigned char>vec;
   vec.reserve(value_.size());
   copy(value_.begin(), value_.end(), vec.begin());
   return vec;
}

void ClientProperty::setValue(const string& value) {
   value_ = value;
   if (needsEncoding()) {
      std::vector<unsigned char>vec;
      vec.reserve(value.size());
      copy(value.begin(), value.end(), vec.begin());
      value_ = Base64::Encode(vec);
   }
}

void ClientProperty::setValueRaw(const string& value) {
   value_ = value;
}

std::string ClientProperty::toXml(std::string extraOffset, bool clearText, std::string tagName) const {
   std::string sb = std::string();
   sb.reserve(256);
   std::string offset = Constants::OFFSET + extraOffset;

   sb += offset + "<" + tagName;
   if (getName() != "") {
      sb += " name='" + getName() + "'";
   }
   if (getType() != "") {
      sb += " type='" + getType() + "'";
   }
   if (getEncoding() != "") {
      sb += " encoding='" + getEncoding() + "'";
   }

   std::string val = getValueRaw();
   if (val == "")
      sb += "/>";
   else {
      //if (encoding_ == Constants.ENCODING_NONE &&
      //    (
      //     val.find("%") != std::string::npos ||
      //     val.find(">") != std::string::npos ||
      //     val.find("&") != std::string::npos
      //    )
      //   sb += "><![CDATA [" + val + "]]></clientProperty>";
      //else
      sb += ">" + (clearText?getStringValue():val) + "</" + tagName + ">";
   }

   return sb;
}

}}}}

//g++ -o ClientProperty -Wall -g -DCLIENTPROPERTY_MAIN ClientProperty.cpp ../Base64.cpp ../Constants.cpp -I ~/xmlBlaster/src/c++
#ifdef CLIENTPROPERTY_MAIN
# include <iostream>
int main(int argc, char **argv) {
   try {
      {
         ClientProperty cp("key", string("string"));
         cout << "name=" << cp.getName() 
              << ", valueB64=" << cp.getValueRaw()
              << ", value=" << cp.getStringValue()
              << ", type=" << cp.getType()
              << ", isBase64=" << cp.isBase64()
              << cp.toXml("")
              << endl << endl;
      }
      {
         vector<unsigned char> v;
         v.push_back('H');
         v.push_back('a');
         v.push_back('l');
         v.push_back('l');
         v.push_back('o');
         ClientProperty cp("key", v);
         cout << "name=" << cp.getName() 
              << ", valueB64=" << cp.getValueRaw()
              << ", value=" << cp.getStringValue()
              << ", type=" << cp.getType()
              << ", encoding=" << cp.getEncoding()
              << ", isBase64=" << cp.isBase64()
              << cp.toXml("")
              << endl;
         {
            std::vector<unsigned char> ret = cp.getValue();
            std::string str;
            str.assign(v.begin(),v.end());
            cout << "NEW=" << str << endl;
         }
      }
   }
   catch(bad_cast b) {
      cout << "EXCEPTION: " << b.what() << endl;
   }
   return 0;
}
#endif
