/**
 * Wrap the XML key for a subscribe() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html" target="others">the interface.subscribe requirement</a>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _CLIENT_KEY_SUBSCRIBEKEY_H
#define _CLIENT_KEY_SUBSCRIBEKEY_H

#include <client/key/UnSubscribeKey.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export SubscribeKey : public UnSubscribeKey
{
public:

   /**
    * Minimal constructor.
    */
   SubscribeKey(Global& global);
   
   SubscribeKey(Global& global, const QueryKeyData& data);

   SubscribeKey(const SubscribeKey& key);

   SubscribeKey& operator =(const SubscribeKey& key);

   /**
    * Set the domain for this message, can be used for a simple grouping of
    * messages to their master node with xmlBlaster clusters. 
    * @param The domain, any chosen string in your problem domain, e.g. "RUGBY" or "RADAR_TRACK"
    *         defaults to "" where the local xmlBlaster instance is the master of the message.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html">The cluster requirement</a>
    */
   void setDomain(const string& domain);

   /**
    * Access the domain setting
    * @return A domain string or null
    */
   string getDomain() const;

};

}}}} // namespace

#endif



