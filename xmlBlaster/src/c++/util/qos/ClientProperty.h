/*------------------------------------------------------------------------------
Name:      ClientProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
#ifndef _UTIL_QOS_CLIENTPROPERTY_H
#define _UTIL_QOS_CLIENTPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <util/Base64.h>
#include <util/lexical_cast.h>
#include <typeinfo>
#include <vector>
#include <string>
#include <iostream> // temporary for cerr

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export bad_clientProperty_cast : public std::bad_cast
{
 public:
   virtual const char * what() const throw() {
      return "bad lexical cast for ClientProperty: source type value could not be interpreted as string";
   }
};

/**
 * This class encapsulates one client property in a QoS. 
 * <p/>
 * Examples:
 * <pre>
 *&lt;clientProperty name='transactionId' type='int'>120001&lt;/clientProperty>
 *&lt;clientProperty name='myKey'>Hello World&lt;/clientProperty>
 *&lt;clientProperty name='myBlob' type='byte[]' encoding='base64'>OKFKAL==&lt;/clientProperty>
 * </pre>
 * If the attribute <code>type</code> is missing we assume a 'String' property
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 * @see TestClientProperty
 */
class Dll_Export ClientProperty
{
private:
   /** The unique key */
   std::string name_;  // Can't be const because of: operator=() error: non-static const member `const std::string name_', can't use default assignment operator
   /** The value encoded as specified with encoding_ */
   std::string value_;
   std::string encoding_;
   mutable std::string type_;

   template <typename T_VALUE> void guessType(const T_VALUE& value) const;
   bool needsEncoding() const;

public:

   /**
    * Standard constructor. 
    * @param name  The unique property key
    * @param value Your data . The type (like "float") is guessed from T_VALUE
    *              NOTE: "vector<unsigned char>" "unsigned char*" are
    *                    treated as BLOBs and will be transferred Base64 encoded.
    * @param type The data type of the value, optional, e.g. "float"
    */
   template <typename T_VALUE> ClientProperty(
                  const std::string& name,
                  const T_VALUE& value
                  );

   /**
    * Specialized ctor for literal data. 
    * @param name  The unique property key
    * @param value Your pointer
    * @param type  Optionally you can force another type than "String",
    *              for example Constant::TYPE_DOUBLE if the pointer contains
    *              such a number as a string representation. 
    */
   ClientProperty(const std::string& name,
                  const char *value,
                  const std::string& type="");

   /**
    * Specialized ctor for blob data. 
    * @param name  The unique property key
    * @param value Your BLOB data.
    * @param type  Optionally you can force another type than "byte[]",
    *              for example Constant::TYPE_DOUBLE if the vector contains
    *              such a number as a string representation. 
    */
   ClientProperty(const std::string& name,
                  const std::vector<unsigned char>& value,
                  const std::string& type="");

   /**
    * Internal constructor only, used by SAX parser in conjunction with setValueRaw()
    */
   ClientProperty(const std::string& name,
                  const std::string& encoding,
                  const std::string& type);

   //virtual ~ClientProperty();

   /**
    * The unique key of the property. 
    * @return The key string
    */
   const std::string& getName() const;

   /**
    * Get the data type of the property value. 
    * @return The data type, for example "short" or "byte[]" for "vector<unsigned char>"
    * @see Constants::TYPE_SHORT
    * @see Constants::TYPE_BLOB
    */
   std::string getType() const;

   /**
    * Get the internally used encoding to transfer data to/from xmlBlaster. 
    * @return The used encoding, for example "base64" or "" for none
    * @see Constants::ENCODING_BASE64
    */
   std::string getEncoding() const;

   /**
    * Check if getValueRaw() is Base64 encoded
    */
   bool isBase64() const;

   /**
    * The raw, possibly still Base64 encoded value
    */
   std::string getValueRaw() const;
   
   /**
    * Convenience method for getValue(T_VALUE&). 
    * @return The value. It is decoded (readable) in case it was base64 encoded,
    */
   std::string getStringValue() const;
   
   /**
    * Accessor for binary data (BLOB). 
    * @return The value. It is decoded (readable) in case it was base64 encoded,
    */
   std::vector<unsigned char> getValue() const;
   
   /**
    * Access with for supported data type. 
    * @param value OUT parameter: The value in the desired data type. 
    *        It is decoded (readable) in case it was base64 encoded,
    */
   template <typename T_VALUE> void getValue(T_VALUE& value) const;
   
   /**
    * Set the value, it will be encoded with the encoding specified in the constructor. 
    */
   void setValue(const std::string& value);

   /**
    * Set the already correctly encoded value, used internally by SAX parser. 
    */
   void setValueRaw(const std::string& value);

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the ClientProperty as a XML ASCII string
    */
   std::string toXml(std::string extraOffset) const;
};


// All template based function definitions follow here
// to be available outside the shared library
template <typename T_VALUE> ClientProperty::ClientProperty(
                     const std::string& name,
                     const T_VALUE& value)
   : name_(name),
     value_(""),
     encoding_(org::xmlBlaster::util::Constants::ENCODING_NONE),
     type_("")
{
   if (type_ == "") {
      guessType(value);  // guess type from T_VALUE
   }

   // Convert the given value type to a std::string value_
   
   if (type_ == "") { // is string type
      value_ = value;
   }
   else {
      std::stringstream interpreter;
      int origPrecision = interpreter.precision(); // 6 digits
      if (type_ == org::xmlBlaster::util::Constants::TYPE_DOUBLE)
         interpreter.precision(15); // Otherwise it will be rounded to 6 digits
                                    // Note that 'long double' has a typical size of 18
      if(!(interpreter << value) || !(interpreter >> value_) || !(interpreter >> std::ws).eof())
         throw bad_clientProperty_cast();
      interpreter.precision(origPrecision);
   }

   if (needsEncoding()) {
      std::vector<unsigned char> vec;
      vec.reserve(value_.size());
      encoding_ = org::xmlBlaster::util::Constants::ENCODING_BASE64;
      copy(value_.begin(), value_.end(), back_inserter(vec));
      value_ = org::xmlBlaster::util::Base64::Encode(vec);
   }
}

template <typename T_VALUE> void ClientProperty::guessType(const T_VALUE& value) const {
   const char *cPC=0;
   char *cP=0;
   unsigned char *cUP=0;
   const unsigned char *cUPC=0;
   int64_t ll=0L; // Assumed to be 'long long int' (C99) == _int64 (Windows), Windows does not like double LL like 0LL;
   std::vector<char> vc;
   std::vector<unsigned char> vuc;

   if (typeid(value) == typeid(std::string("")))
      type_ = std::string(""); // "String"
   else if (typeid(value) == typeid(true))
      type_ = org::xmlBlaster::util::Constants::TYPE_BOOLEAN;
   else if (typeid(value) == typeid(char(0)))
      type_ = org::xmlBlaster::util::Constants::TYPE_BYTE;
   else if (typeid(value) == typeid(cP) || typeid(value) == typeid(cPC) ||
            typeid(value) == typeid("A") || typeid(value).name() == std::string("A13_c"))
      type_ = std::string(""); // "String"
   else if (typeid(value) == typeid(cUP) || typeid(value) == typeid(cUPC))
      type_ = org::xmlBlaster::util::Constants::TYPE_BLOB;
   else if (typeid(value) == typeid(vc) || typeid(value) == typeid(vuc))
      type_ = org::xmlBlaster::util::Constants::TYPE_BLOB;
   else if (typeid(value) == typeid(short(0)))
      type_ = org::xmlBlaster::util::Constants::TYPE_SHORT;
   else if (typeid(value) == typeid(int(0)))
      type_ = org::xmlBlaster::util::Constants::TYPE_INT;
   else if (typeid(value) == typeid(long(0L)))
      type_ = org::xmlBlaster::util::Constants::TYPE_LONG;
   else if (typeid(value) == typeid(ll))
      type_ = org::xmlBlaster::util::Constants::TYPE_LONG;
   else if (typeid(value) == typeid(float(1.10)))
      type_ = org::xmlBlaster::util::Constants::TYPE_FLOAT;
   else if (typeid(value) == typeid(double(1.7L)))
      type_ = org::xmlBlaster::util::Constants::TYPE_DOUBLE;
   else {
      type_ = org::xmlBlaster::util::Constants::TYPE_BLOB;
      std::cerr << "Warning: ClientProperty typeid=" << typeid(value).name() << " is unknown, we handle it as a blob" << std::endl;
   }
}

template <typename T_VALUE> void ClientProperty::getValue(T_VALUE& value) const {
   if (isBase64()) {
      if (type_ == org::xmlBlaster::util::Constants::TYPE_BLOB) {
         // TODO: detect it on compile time
         std::cerr << "Sorry, binary data type '" << typeid(value).name()
                   << "' is not supported using getValue(value), please use 'std::vector<unsigned char> getValue()' instead"
                   << std::endl;
         value = lexical_cast<T_VALUE>(getStringValue());
      }
      else {
         value = lexical_cast<T_VALUE>(getStringValue());
      }
   }
   else {
      value = lexical_cast<T_VALUE>(value_);
   }
}

}}}}

#endif

