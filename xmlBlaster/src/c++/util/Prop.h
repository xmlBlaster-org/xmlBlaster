/*------------------------------------------------------------------------------
Name:      Prop.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding data for a property
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Helper class to hold properties.
 * In some cases it is needed to know how a certain property has been defined. For example if it has been
 * expressively set by the user at the command line, in the configuration file, or if it is the hardcoded 
 * default. 
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_PROP_H
#define _UTIL_PROP_H

#include <util/Property.h>



namespace org { namespace xmlBlaster { namespace util {

enum prop_enum {
   CREATED_BY_DEFAULT,
   CREATED_BY_JVMENV,
   CREATED_BY_PROPFILE,
   CREATED_BY_CMDLINE,
   CREATED_BY_SETTER
};

typedef enum prop_enum PropEnum;

template <class T> class Dll_Export Prop
{
private:
   T        value_;
   PropEnum origin_;

public:
   
   Prop(const T& value=T(), PropEnum origin=CREATED_BY_DEFAULT)
   {
      value_  = value;
      origin_ = origin;
   }

   Prop(const Prop& prop) 
   {
      value_ = prop.value_;
      origin_ = prop.origin_;
   }

   Prop& operator =(const Prop& prop) 
   {
      value_ = prop.value_;
      origin_ = prop.origin_;
      return *this;
   }

   bool setValue(Property& prop, const std::string& propName)
   {
      if (origin_ >= CREATED_BY_SETTER) return false;
      if (prop.getTypedProperty(propName, value_, false)) {
         origin_ = CREATED_BY_CMDLINE;
         return true;
      }
      if (origin_ > CREATED_BY_JVMENV) return false;
      if (prop.getTypedProperty(propName, value_, true)) return true;
      return false;
   }

   bool setValue(const T& value, PropEnum origin=CREATED_BY_DEFAULT)
   {
      if (origin < origin_) return false;
      origin_ = origin;
      value_ = value;
      return true;
   }

   PropEnum getOrigin() const
   {
      return origin_;
   }
  
   T getValue() const 
   {
      return value_;
   }

   /**
    * Is unmanipulated default value?
    */
   const bool isModified() const
   {
      return origin_ != CREATED_BY_DEFAULT;
   }

   /**
    * writes out the prop as an xml. If forceWrite is set to 'true', then it is always written out, otherwise
    * it is only written out when different from the default. If it is the default, an empty std::string is
    * returned.
    */ 
/*
   toXml(const std::string&  name, const std::string& offset="", bool forceWrite=false)
   {
      if (origin_ > CREATED_BY_DEFAULT || forceWrite) {
         return "<" + name + ">" +  .... (not finished yet);
      }
   }
*/   
};

}}} // namespaces

#endif
