/*------------------------------------------------------------------------------
Name:      MsgKeyData.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the Message meta data and unique identifier (key)
 * of a publish()/update() or get()-return message.
 * <p />
 * A typical key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have
 * to supply to the setClientTags() method.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * <p>
 * If you haven't specified a key oid, there will be generated one automatically.
 * </p>
 * <p>
 * NOTE: Message oid starting with "__" is reserved for internal usage.
 * </p>
 * <p>
 * NOTE: Message oid starting with "_" is reserved for xmlBlaster plugins.
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 */

#ifndef _UTIL_KEY_MSGKEYDATA_H
#define _UTIL_KEY_MSGKEYDATA_H

#include <util/key/KeyData.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace key {

class Dll_Export MsgKeyData : public KeyData
{
private:
   std::string clientTags_;

public:

   /**
    * Minimal constructor.
    */
   MsgKeyData(org::xmlBlaster::util::Global& global);
   
   MsgKeyData(const MsgKeyData& key);

   MsgKeyData& operator =(const MsgKeyData& key);

   /**
    * @return never '' (empty), an oid is generated if it was empty.
    */
   std::string getOid() const;

   /**
    * Set client specific meta informations. 
    * <p />
    * May be used to integrate your application tags, for example:
    * <p />
    * <pre>
    *&lt;key oid='4711' contentMime='text/xml'>
    *   &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
    *      &lt;DRIVER id='FileProof' pollingFreq='10'>
    *      &lt;/DRIVER>
    *   &lt;/AGENT>
    *&lt;/key>
    * </pre>
    * @param str Your tags in ASCII XML syntax
    */
   void setClientTags(const std::string& tags);

   std::string getClientTags() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset) const;
   std::string toXml() const;

   /**
    * Allocate a clone. 
    * @return The caller needs to free it with 'delete'. 
    */
   MsgKeyData* getClone() const;
};

}}}} // namespace

#endif

