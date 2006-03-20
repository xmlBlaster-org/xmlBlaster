/*-----------------------------------------------------------------------------
Name:      XmlQoSBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it 
           with the implemented xml parser.
-----------------------------------------------------------------------------*/

#include <util/xmlBlasterDef.h>
#include <util/XmlQoSBase.h>
#include <string>
#include <util/Global.h>
#include <util/StringTrim.h>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {

XmlQoSBase::XmlQoSBase(Global& global) : XmlHandlerBase(global), ReferenceCounterBase()
{
   inQos_ = false;
   if (log_.call()) log_.trace(me(), "Creating new XmlQoSBase");
}

bool XmlQoSBase::isEmpty(const string &qos) 
{
   if (qos.empty()) return true;
   string trimHelper  = StringTrim::trim(qos);
   if (trimHelper.size() < 11) return true;
   
   string middle;
   middle.assign(qos, 5, qos.length()-6); // or minus 11 ???
   if (middle.size() < 1) return true;
   return false;
}

bool XmlQoSBase::startElementBase(const string &name, const parser::AttributeMap& /*attrs*/) 
{
   if (name.compare("qos") == 0) {
      inQos_ = true;
      return true;
   }
   return false;
}

void XmlQoSBase::startElement(const string &name, const parser::AttributeMap &attrs) 
{
   startElementBase(name, attrs);
}

bool XmlQoSBase::endElementBase(const string &name) 
{
   if (name.compare("qos") == 0) {
      inQos_     = false;
      character_ = "";
      return true;
   }
   return false;
}

void XmlQoSBase::endElement(const string &name) 
{
   endElementBase(name);
}

}}} // namespace
