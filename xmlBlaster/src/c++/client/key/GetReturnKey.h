/*------------------------------------------------------------------------------
Name:      GetReturnKey.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Parses the key of returned MsgUnit of get() invocations
 * <p>
 * See MsgKeySaxFactory for a syntax description of the xml structure
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html" target="others">the interface.get requirement</a>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _CLIENT_KEY_GETRETURNKEY_H
#define _CLIENT_KEY_GETRETURNKEY_H

#include <client/key/UpdateKey.h>

using namespace std;
//using namespace org::xmlBlaster::util;<-- VC CRASH
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export GetReturnKey : public UpdateKey
{
public:

   /**
    * Minimal constructor.
    */
   GetReturnKey(Global& global);
   
   GetReturnKey(Global& global, const MsgKeyData& data);

   GetReturnKey(const GetReturnKey& key);

   GetReturnKey& operator =(const GetReturnKey& key);

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   bool isInternal() const;

};

}}}} // namespace

#endif



