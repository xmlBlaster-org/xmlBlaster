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
// #include <util/SaxHandlerBase.h>
#include <util/parser/XmlHandlerBase.h>
#include <util/key/MsgKeyData.h>




namespace org { namespace xmlBlaster { namespace util { namespace key {

class Dll_Export MsgKeyFactory : public parser::XmlHandlerBase
{
private:
    std::string ME;
    org::xmlBlaster::util::key::MsgKeyData msgKeyData_;

    std::string OID; //                   = "oid";
    std::string CONTENT_MIME; //          = "contentMime";
    std::string CONTENT_MIME_EXTENDED; // = "contentMimeExtended";
    std::string D_O_M_A_I_N; //           = "domain";

   /** helper flag for SAX parsing: parsing inside <state> ? */
   int inKey_; // = 0;
   std::string clientTags_;
   std::string clientTagsOffset_;
   int clientTagsDepth_;
   
public:
   /**
    * Can be used as singleton. 
    */
   MsgKeyFactory(org::xmlBlaster::util::Global& global);

   ~MsgKeyFactory();

   /**
    * Parses the given xml Key and returns a org::xmlBlaster::util::key::MsgKeyData holding the data. 
    * Parsing of update() and publish() key is supported here.
    * @param the XML based ASCII std::string
    */
   org::xmlBlaster::util::key::MsgKeyData readObject(const std::string& xmlKey);

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   void startElement(const std::string &name, const parser::AttributeMap &attrs);

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   void endElement(const std::string &name);
};

}}}} // namespace

#endif

