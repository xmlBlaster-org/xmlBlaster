/*------------------------------------------------------------------------------
Name:      FileWriterCallback.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _CONTRIB_FILEWRITERCALLBACK_H
#define _CONTRIB_FILEWRITERCALLBACK_H

#include <util/xmlBlasterDef.h>
#include <util/Global.h>
#include <util/qos/ConnectQos.h>
#include <client/I_ConnectionProblems.h>
#include <client/I_Callback.h>
#include <util/thread/ThreadImpl.h>
#include <util/dispatch/I_PostSendListener.h>
#include <util/ReferenceCounterBase.h>
#include <util/ReferenceHolder.h>
#include <string>
#include <vector>
#include <map>

/* The following comment is used by doxygen for the main html page: */
/*! \mainpage Hints about the C++ client library usage.
 *
 * \section intro_sec The C++ client library
 *
 * The xmlBlaster C++ client library supports access to xmlBlaster with asynchronous callbacks,
 * client side queuing and fail safe reconnect using the CORBA or SOCKET protocol plugin.
 * Details about compilation and its usage can be found in the 
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.cpp.html requirement.
 *
 * As a C++ developer your entry point to use is the class org::xmlBlaster::client::FileWriterCallback and
 * a complete overview demo code is HelloWorld2.cpp
 *
 * \section c_sec The C client library
 * The C client library offers some basic functionality like the SOCKET protocol with
 * the struct #FileWriterCallbackUnparsed or persistent queues with struct #I_Queue.
 * These features are heavily used by the C++ library.
 * If you need a tiny xmlBlaster access you can choose to use the C client library directly
 * without any C++ code.
 *
 * For details read the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html requirement and the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html requirement.
 * and look at the API documentation at http://www.xmlblaster.org/xmlBlaster/doc/doxygen/c/html/index.html
 */

// Note: I_ConnectionProblems.h includes I_ConnectionsHandler.h includes I_XmlBlasterConnection.h
//       which includes all EraseQos, SubscribeKey etc.
//       -> We could try to avoid this by forward declaration, but all cpp files must
//          then include them thereselves.

// Declare classes without the need to include them in this header file
namespace org { namespace xmlBlaster { namespace util {
   class MessageUnit;
}}}
namespace org { namespace xmlBlaster { namespace util { namespace dispatch {
   class DispatchManager;
   class ConnectionsHandler;
}}}}
namespace org { namespace xmlBlaster { namespace client { namespace protocol {
   class I_CallbackServer;
}}}}

namespace org { namespace xmlBlaster { namespace contrib {

/*
 * The interface org::xmlBlaster::client::I_CallbackRaw/I_Callback/I_CallbackExtended are enforced by AbstractCallbackExtended
 * is for the protocol drivers.
 */
typedef std::map<std::string, org::xmlBlaster::client::I_Callback*> CallbackMapType;
typedef std::map<std::string, std::string> StringMap;

/**
 * This is the main entry point for programmers to the C++ client library. 
 * 
 * Exactly one Global instance and one instance of this are a pair which can't be
 * mixed with other instances. 
 */
class Dll_Export FileWriterCallback : public org::xmlBlaster::client::I_Callback
{
private:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   int BUF_SIZE;
   std::string dirName_;
   std::string lockExtention_;
   bool overwrite_;
   std::string tmpDirName_;
   bool keepDumpFiles_;
   std::string directory_;
   std::string tmpDirectory_;
	org::xmlBlaster::util::I_Log& log_;
	char *buf_;
	int bufSize_;
		
// TODO REMOVE NEXT LINE AFTER TESTING	
public:
   void storeChunk(std::string &tmpDir, std::string &fileName, long chunkNumber, std::string &sep, bool overwrite, const char *content, long length);

   /**
    * Returns the number used as the postfix of this file. If there is no such number, -1 is
    * returned. If the content is null or empty, an exception is thrown. If there is a postfix
    * but it is not a number, a -1 is returned.
    *
    * @param filename the filename to check.
    * @param prefix the prefix of the filename (the part before the number)
    * @return a long specifying the postfix number of the file.
    * @throws Exception if the content is null or empty.
    */
   long extractNumberPostfixFromFile(std::string &filename, std::string &prefix);

   /**
    * Fills the vector with a list of files found.
    *
    * @param dir
    * @param prefix if not empty, only files starting with the specified prefix will be returned.
    * @param files
    */
	void getdir(std::string &dir, std::string &prefix, std::vector<std::string> &files);
   
   /**
    * The filename prefix for which to retrieve all chunks.
    * @param fileName
    * @param sep
    * @return
    * @throws Exception
    */
   void getChunkFilenames(std::string &fileName, std::string &sep, std::vector<std::string> &filenames);

   /**
    * Puts all chunks stored in separate files together in one single one.
    *
    * @param fileName The name of the complete (destination) file.
    * @param expectedChunks The number of expected chunks. If the number of chunks found is
    * bigger, the exceeding ones are ignored and a warning is written to the logs. If the number
    * is too low it checks if the file already exists. If it exists a warning is written and
    * the method returns, otherwise an exception is thrown. Keeps also track of locking the file.
    * @param lastContent the content of the last chunk of the message (can be null).
    * @param isCompleteMsg is true if the content of the message is contained in one single
    * chunk (i.e. if the message is not chunked).
    * @throws Exception If an error occurs when writing / reading the files. This method tries
    * to clean up the destination file in case of an exception when writing.
    */
   void putAllChunksTogether(std::string &fileName, std::string &subDir, long expectedChunks, const char *buf, long bufSize, bool isCompleteMsg);

   /**
    * Deletes the specified file from the file system.
    * Never throws Exceptions
    * @param file the file object to be deleted.
    * @return if it can not delete the file it returns false, true otherwise.
    */
   bool deleteFile(std::string &file);

public:

   /**
    * Creates a callback
    * @param dirName The name of the directory where to store the files.
    * @param tmpDirName the name of the directory where to store the temporary files.
    * @param lockExtention The extention to use to lock the reading of the file. This is used if the
    * entries have to be retrieved by the filepoller. Until such a file exists, the entry is not
    * processed by the file poller.
    * @param overwrite if set to true it will overwrite existing files.
    * @throws Exception
    */
   FileWriterCallback(org::xmlBlaster::util::Global &global, std::string &dirName, std::string &tmpDirName, std::string &lockExtention, bool overwrite, bool keepDumpFiles);

   std::string update(const std::string &sessionId,
                       org::xmlBlaster::client::key::UpdateKey &updateKey,
                       const unsigned char *content, long contentSize,
                       org::xmlBlaster::client::qos::UpdateQos &updateQos);

   virtual ~FileWriterCallback();

};

typedef org::xmlBlaster::util::ReferenceHolder<org::xmlBlaster::contrib::FileWriterCallback> FileWriterCallbackRef;

}}} // namespaces

#endif
