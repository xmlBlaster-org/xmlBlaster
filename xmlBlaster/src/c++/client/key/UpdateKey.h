/*------------------------------------------------------------------------------
Name:      UpdateKey.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Parses the key of returned MsgUnit of update(). 
 * <p>
 * See MsgKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">the interface.update requirement</a>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
#ifndef _CLIENT_KEY_UPDATEKEY_H
#define _CLIENT_KEY_UPDATEKEY_H

#include <client/key/MsgKeyBase.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export UpdateKey : public MsgKeyBase
{
public:

   /**
    * Minimal constructor.
    */
   UpdateKey(org::xmlBlaster::util::Global& global);
   
   UpdateKey(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::key::MsgKeyData& data);

   UpdateKey(const UpdateKey& key);

   UpdateKey& operator =(const UpdateKey& key);

   /**
    * Test if oid is '__sys__deadMessage'. 
    * <p />
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    * <p>
    * This is an internal message (isInternal() returns true)
    * </p>
    */
   bool isDeadMessage() const;

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   bool isPluginInternal() const;

   std::string getClientTags() const;

};

}}}} // namespace

#endif



