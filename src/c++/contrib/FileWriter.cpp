/*------------------------------------------------------------------------------
Name:      FileWriter.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <contrib/FileWriter.h>
#include <util/Global.h>
#include <util/qos/ConnectQosFactory.h>
#include <util/lexical_cast.h>
#include <util/Timestamp.h>
#include <util/dispatch/DispatchManager.h>
#include <util/parser/ParserFactory.h>
#include <stdio.h>
#include <fstream>

namespace org { namespace xmlBlaster { namespace contrib {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::qos::address;
using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;

FileWriter::FileWriter(org::xmlBlaster::util::Global &global, std::string &name)
   : ME("FileWriter"),
     global_(global),
     log_(global.getLog("org.xmlBlaster.contrib")),
	  subscribeKey_(""),
	  subscribeQos_(""),
	  name_(name),
	  momAdministered_(true),
	  connectQos_((org::xmlBlaster::util::qos::ConnectQos*)0)
{
   access_ = NULL;
   callback_ = NULL;
	
}

void FileWriter::init() 
{
	try {
		org::xmlBlaster::util::Property props = global_.getProperty();
		std::string key("mom.connectQos");
		if (props.propertyExists(key)) {
			std::string connectQosLiteral = props.get(key, "");
			org::xmlBlaster::util::qos::ConnectQosFactory factory(global_);
			connectQos_ = factory.readObject(connectQosLiteral);
		}
		else {
			std::string userId = props.get("mom.loginName", "_" + name_);
			std::string password = props.get("mom.password", "");
			connectQos_ = ConnectQosRef(new ConnectQos(global_, userId, password));
		}
      
		// momAdministered = this.global.get("mom.administered", false, null, pluginConfig);
      
		// tmp = this.global.get("mom.subscribeKey", (String)null, null, pluginConfig);
		// String topicName =  this.global.get("mom.topicName", (String)null, null, pluginConfig);
		if (momAdministered_) {
			std::string replDispatchPlugin("ReplManager,1.0");
			org::xmlBlaster::util::qos::address::CallbackAddress *cbAddr = new org::xmlBlaster::util::qos::address::CallbackAddress(global_);
	      cbAddr->setRetries(-1);
			cbAddr->setDispatchPlugin(replDispatchPlugin);
			org::xmlBlaster::util::qos::address::AddressBaseRef ref(cbAddr);
			connectQos_->addCbAddress(ref);
		}
		key = std::string ("filewriter.directoryName");
   	if (!props.propertyExists(key)) {
			std::string location(ME + "::init");
			std::string txt("prop '" + key + "' must be set");
			throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
   	}
		std::string directoryName = props.get(key, ".");
		key = std::string("filewriter.tmpDirectoryName");
		std::string tmpDirectoryName = props.get(key, directoryName + FILE_SEP + "tmp");
		bool overwrite = props.get("filewriter.overwrite", true);
		std::string lockExtention = props.get("filewriter.lockExtention", ".lck");
		bool keepDumpFiles = false;
		callback_ = new FileWriterCallback(global_, directoryName, tmpDirectoryName, lockExtention, overwrite, keepDumpFiles);
		initConnection();
	}
	catch (XmlBlasterException &ex) {
		throw ex;
	}
	catch (exception &ex) {
		throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::init", ex.what());
	}
	catch (...) {
		throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::init", "unknown exception");
	}
	
}

void FileWriter::initConnection()
{
	log_.trace(ME, "init");
	access_ = new org::xmlBlaster::client::XmlBlasterAccess(global_);
	access_->connect(*connectQos_, this);
	if (!momAdministered_) {
		// access_->subscribe(subscribeKey_, subscribeQos_);
		std::string location(ME + "::putAllChunksTogether");
		std::string txt("momAdministered 'false' is not implemented");
		throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
	}
		
}


void FileWriter::shutdown()
{
	if (access_ != NULL) {
		DisconnectQos qos(global_);
		// access_->disconnect(qos);
		access_ = NULL;
	}
	callback_ = NULL;
}

std::string FileWriter::update(const std::string &sessionId,
                       org::xmlBlaster::client::key::UpdateKey &updateKey,
                       const unsigned char *content, long contentSize,
                       org::xmlBlaster::client::qos::UpdateQos &updateQos)
{
	try {
			// InputStream is = MomEventEngine.decompress(new ByteArrayInputStream(content), updateQos.getClientProperties());
         std::string timestamp = "" + updateQos.getRcvTime();
         std::map<std::string, org::xmlBlaster::util::qos::ClientProperty> props = updateQos.getClientProperties();
         std::map<std::string, org::xmlBlaster::util::qos::ClientProperty>::const_iterator iter = props.end();
        	org::xmlBlaster::util::qos::ClientProperty property(Constants::TIMESTAMP_ATTR, timestamp, Constants::TYPE_STRING, "UTF-8");
         props.insert(pair<std::string, org::xmlBlaster::util::qos::ClientProperty>(Constants::TIMESTAMP_ATTR, property));
			return callback_->update(sessionId, updateKey, content, contentSize, updateQos);
	}
	catch (XmlBlasterException &ex) {
		throw ex;
	}
	catch (exception &ex) {
		throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::update", ex.what());
	}
	catch (...) {
		throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::update", "unknown exception");
	}
}

FileWriter::~FileWriter() 
{
}


}}} // namespaces

