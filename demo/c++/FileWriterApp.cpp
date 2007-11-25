/*------------------------------------------------------------------------------
Name:      FileWriterApp.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <contrib/FileWriter.h>
#include <util/Global.h>
#include <util/Timestamp.h>
#include <util/thread/ThreadImpl.h>

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;

int main(int args, char* argv[])
{
	// Init the XML platform
	try
	{
		Global& glob = Global::getInstance();
		glob.initialize(args, argv);
		std::string name("writer");
		org::xmlBlaster::contrib::FileWriter writer(glob, name);
		writer.init();
		
		// wait indefinitely		
		org::xmlBlaster::util::thread::Condition condition;
		org::xmlBlaster::util::thread::Mutex mutex;
		org::xmlBlaster::util::thread::Lock lock(mutex);
		condition.wait(lock, -1);

		writer.shutdown(); // is probably never executed
   }
   catch (XmlBlasterException &ex) {
      std::cout << ex.toXml() << std::endl;
      // std::cout << ex.getRawMessage() << std::endl;
   }
   return 0;
}

