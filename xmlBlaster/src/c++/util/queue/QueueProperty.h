/*------------------------------------------------------------------------------
Name:      QueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#ifndef _UTIL_QUEUE_QUEUEPROPERTY_H
#define _UTIL_QUEUE_QUEUEPROPERTY_H


#include <util/xmlBlasterDef.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/Constants.h>
#include <util/queue/QueuePropertyBase.h>
#include <util/cfg/Address.h>

#include <string>
#include <vector>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

class QueueProperty : public QueuePropertyBase
{
private:
    const string ME;

protected:

   /**
    * Configure property settings
    */
   void initialize()
   {
      QueuePropertyBase::initialize();

      // Set the queue properties
      setMaxMsg(global_.getProperty().getLongProperty("queue.maxMsg", DEFAULT_maxMsgDefault));
      setMaxSize(global_.getProperty().getLongProperty("queue.maxSize", DEFAULT_sizeDefault));
      setExpires(global_.getProperty().getTimestampProperty("queue.expires", DEFAULT_maxExpires));
      setOnOverflow(global_.getProperty().getStringProperty("queue.onOverflow", DEFAULT_onOverflow));
      setOnFailure(global_.getProperty().getStringProperty("queue.onFailure", DEFAULT_onFailure));
      setType(global_.getProperty().getStringProperty("queue.type", DEFAULT_type));
      setVersion(global_.getProperty().getStringProperty("queue.version", DEFAULT_version));
      if (nodeId_ != "") {
         setMaxMsg(global_.getProperty().getLongProperty(string("queue.maxMsg[")+nodeId_+string("]"), getMaxMsg()));
         setMaxSize(global_.getProperty().getLongProperty(string("queue.maxSize[")+nodeId_+string("]"), getMaxSize()));
         setExpires(global_.getProperty().getTimestampProperty(string("queue.expires[")+nodeId_+string("]"), getExpires()));
         setOnOverflow(global_.getProperty().getStringProperty(string("queue.onOverflow[")+nodeId_+string("]"), getOnOverflow()));
         setOnFailure(global_.getProperty().getStringProperty(string("queue.onFailure[")+nodeId_+string("]"), getOnFailure()));
         setType(global_.getProperty().getStringProperty(string("queue.type[")+nodeId_+string("]"), getType()));
         setVersion(global_.getProperty().getStringProperty(string("queue.version[")+nodeId_+string("]"), getVersion()));
      }
   }

public:
   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   QueueProperty(Global& global, const string& nodeId);

   /**
    * Show some important settings for logging
    */
   string getSettings();

   /**
    */
   void setAddress(const Address& address);

   void cleanUpAddresses();

   /**
    * clears up all addresses and allocates new ones.
    */
   void setAddresses(const vector<Address>& addresses);

   /**
    * @return null if none available
    */
   Address* getCurrentAddress();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();

};

}}}} // namespace

#endif
