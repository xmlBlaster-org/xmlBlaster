/*-----------------------------------------------------------------------------
Name:      XmlQoSBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it 
           with SAX
-----------------------------------------------------------------------------*/

#ifndef _UTIL_XMLQOSBASE_H
#define _UTIL_XMLQOSBASE_H

#include <util/xmlBlasterDef.h>
#include <util/SaxHandlerBase.h>
#include <string>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {
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
    class Dll_Export XmlQoSBase : public SaxHandlerBase
    {

    private:

       string me()
       {
          return "XmlQoSBase";
       }
       
    protected:

       bool inQos_; // parsing inside <qos> ? </qos>
       
    public:

       /**
        * Constructs an un initialized QoS (quality of service) object.
        * You need to call the init() method to parse the XML string.
        */
       XmlQoSBase(Global& global);

    protected:

       /**
        * To avoid SAX parsing (which costs many CPU cycles)
        * check the QoS string here if it contains anything useful.
        * @param qos The literal ASCII xml string
        */
       bool isEmpty(const string &qos);

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
       bool startElementBase(const XMLCh* const name, AttributeList& /*attrs*/);

    public:
       /**
        * Start element.<p />
        * Default implementation, knows how to parse &lt;qos> but knows 
        * nothing about the tags inside of qos
        */
       void startElement(const XMLCh* const name, AttributeList &attrs);
       
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
       bool endElementBase(const XMLCh* const name);

    public:
       /** End element.
        * <p />
        * Default implementation, knows how to parse &lt;qos> but knows 
        * nothing about the tags inside of qos
        */
       void endElement(const XMLCh* const name);
    };
}}} // namespace

#endif

