/*-----------------------------------------------------------------------------
Name:      XmlQoSBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it 
           with SAX
-----------------------------------------------------------------------------*/

#include <util/xmlBlasterDef.h>
#include <util/XmlQoSBase.h>
#include <string>
#include <util/Global.h>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {

XmlQoSBase::XmlQoSBase(Global& global) : SaxHandlerBase(global)
{
   inQos_ = false;
   if (log_.call()) log_.trace(me(), "Creating new XmlQoSBase");
}

bool XmlQoSBase::isEmpty(const string &qos) 
{
   if (qos == "") return true;
   char *trimHelper  = charTrimmer_.trim(qos.c_str());
   if (XMLString::stringLen(trimHelper) < 11) return true;
   
   string middle;
   middle.assign(qos, 5, qos.length()-6); // or minus 11 ???
   if (middle.size() < 1) return true;
   return false;
}

bool XmlQoSBase::startElementBase(const XMLCh* const name, AttributeList& /*attrs*/) 
{
   if (SaxHandlerBase::caseCompare(name, "qos")) {
      inQos_ = true;
      return true;
   }
   return false;
}

void XmlQoSBase::startElement(const XMLCh* const name, AttributeList &attrs) 
{
   startElementBase(name, attrs);
}

bool XmlQoSBase::endElementBase(const XMLCh* const name) 
{
   if( SaxHandlerBase::caseCompare(name, "qos") ) {
      inQos_     = false;
      character_ = "";
      return true;
   }
   return false;
}

void XmlQoSBase::endElement(const XMLCh* const name) 
{
   endElementBase(name);
}

}}} // namespace
