/**
 * Access to an erase key. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html" target="others">the interface.erase requirement</a>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _UTIL_KEY_ERASEKEY_H
#define _UTIL_KEY_ERASEKEY_H

#include <client/key/SubscribeKey.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

typedef SubscribeKey EraseKey;

}}}} // namespace

#endif



