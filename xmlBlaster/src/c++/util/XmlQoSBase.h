/*-----------------------------------------------------------------------------
Name:      XmlQoSBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it 
           with SAX
-----------------------------------------------------------------------------*/

#include <string>
#include <util/SaxHandlerBase.h>

#ifndef _UTIL_XMLQOSBASE_H
#define _UTIL_XMLQOSBASE_H

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
    /**
     * In good old C days this would have been named a 'flag' (with bit wise 
     * setting)<br />
     * But this allows to specify QoS (quality of service) in XML syntax.<p />
     * With XML there are no problems to extend the services of the xmlBlaster
     * in unlimited ways.<br />
     * The xml string is parsed with a SAX parser, since no persistent DOM 
     * tree is needed and SAX is much faster. <p />
     * You may use this as a base class for your specialized QoS.<br />
     * The &lt;qos> tag is parsed here, and you provide the parsing of the 
     * inner tags.
     */
    class XmlQoSBase : public SaxHandlerBase {

    private:

       string me() {
          return "XmlQoSBase";
       }
       
    protected:

       bool inQos_; // parsing inside <qos> ? </qos>
       
    public:

       /**
        * Constructs an un initialized QoS (quality of service) object.
        * You need to call the init() method to parse the XML string.
        */
       XmlQoSBase(int args=0, char *argc[]=0) : SaxHandlerBase(args,argc) {
          inQos_ = false;
          if (log_.CALL) log_.trace(me(), "Creating new XmlQoSBase");
       }


    protected:

       /**
        * To avoid SAX parsing (which costs many CPU cycles)
        * check the QoS string here if it contains anything useful.
        * @param qos The literal ASCII xml string
        */
       bool isEmpty(const string &qos) {
          if (qos == "") return true;
          char *trimHelper  = charTrimmer_.trim(qos.c_str());
          if (XMLString::stringLen(trimHelper) < 11) return true;
          
          string middle;
          middle.assign(qos, 5, qos.length()-6); // or minus 11 ???
          if (middle.size() < 1) return true;
          return false;
       }


       /**
        * Start element callback, does handling of tag &lt;qos>. <p />
        * You may include this into your derived startElement() method like 
        * this:<br />
        * <pre>
        *  if (util::XmlQoSBase::startElementBase(name, attrs)) return;
        * </pre>
        * @return true if the tag is parsed here, the derived class doesn't 
        *         need to look at this tag anymore
        *         false this tag is not handled by this Base class
        */
       bool startElementBase(const XMLCh* const name, AttributeList& /*attrs*/) {
          if (SaxHandlerBase::caseCompare(name, "qos")) {
             inQos_ = true;
             return true;
          }
          return false;
       }

    public:
       /**
        * Start element.<p />
        * Default implementation, knows how to parse &lt;qos> but knows 
        * nothing about the tags inside of qos
        */
       void startElement(const XMLCh* const name, AttributeList &attrs) {
          startElementBase(name, attrs);
       }
       
    protected:
       /**
        * End element callback, does handling of tag &lt;qos>. <p />
        * You may include this into your derived endElement() method like 
        * this:<br />
        * <pre>
        *  if (SaxHandlerBase::endElementBase(name) == true)
        *     return;
        * </pre>
        * @return true if the tag is parsed here, the derived class doesn't 
        *         need to look at this tag anymore
        *         false this tag is not handled by this Base class
        */
       bool endElementBase(const XMLCh* const name) {
          if( SaxHandlerBase::caseCompare(name, "qos") ) {
             inQos_     = false;
             character_ = "";
             return true;
          }
          return false;
       }

    public:
       /** End element.
        * <p />
        * Default implementation, knows how to parse &lt;qos> but knows 
        * nothing about the tags inside of qos
        */
       void endElement(const XMLCh* const name) {
          endElementBase(name);
       }
    };
}}} // namespace

#endif

