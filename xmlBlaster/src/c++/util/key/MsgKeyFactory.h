/*------------------------------------------------------------------------------
Name:      MsgKeyFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Parsing xml Key (quality of service) of publish() and update(). 
 * <p />
 * All XmlKey's have the same XML minimal structure:<p>
 * <pre>
 *    &lt;key oid="12345"/>
 * </pre>
 * or
 * <pre>
 *    &lt;key oid="12345">
 *       &lt;!-- application specific tags -->
 *    &lt;/key>
 * </pre>
 *
 * where oid is a unique key.
 * <p />
 * A typical <b>publish</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * <p>
 * NOTE: &lt;![CDATA[ ... ]]> sections in the key are not supported
 * </p>
 * <p>
 * NOTE: Using tags like '&lt;<c/>' will be transformed to '&lt;c>&lt;/c>' on toXml()
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeyData
 * @see org.xmlBlaster.test.classtest.key.MsgKeyFactoryTest
 * @author xmlBlaster@marcelruff.info
 */

#ifndef _UTIL_KEY_MSGKEYFACTORY_H
#define _UTIL_KEY_MSGKEYFACTORY_H

#include <util/xmlBlasterDef.h>
#include <util/SaxHandlerBase.h>
#include <util/key/MsgKeyData.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace key {

class Dll_Export MsgKeyFactory : public SaxHandlerBase
{
private:
    string ME;
    MsgKeyData msgKeyData_;

    XMLCh* OID; //                   = "oid";
    XMLCh* CONTENT_MIME; //          = "contentMime";
    XMLCh* CONTENT_MIME_EXTENDED; // = "contentMimeExtended";
    XMLCh* D_O_M_A_I_N; //           = "domain";

   /** helper flag for SAX parsing: parsing inside <state> ? */
   int inKey_; // = 0;
public:
   /**
    * Can be used as singleton. 
    */
   MsgKeyFactory(Global& global);

   ~MsgKeyFactory();

   /**
    * Parses the given xml Key and returns a MsgKeyData holding the data. 
    * Parsing of update() and publish() key is supported here.
    * @param the XML based ASCII string
    */
   MsgKeyData readObject(const string& xmlKey);

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   void startElement(const XMLCh* const name, AttributeList& attrs);

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   void endElement(const XMLCh* const name);
};

}}}} // namespace

#endif

