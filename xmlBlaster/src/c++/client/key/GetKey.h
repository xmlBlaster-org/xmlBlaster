/**
 * Wraps an XML key for a get() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html" target="others">the interface.get requirement</a>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _UTIL_KEY_GETKEY_H
#define _UTIL_KEY_GETKEY_H

#include <client/key/SubscribeKey.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

typedef SubscribeKey GetKey;

}}}} // namespace

#endif



