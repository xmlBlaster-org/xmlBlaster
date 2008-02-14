/*------------------------------------------------------------------------------
Name:      FileWriterCallback.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <contrib/FileWriterCallback.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <util/Timestamp.h>
#include <util/dispatch/DispatchManager.h>
#include <util/parser/ParserFactory.h>
#include <stdio.h>
#if defined(_WINDOWS)
#   ifdef WIN32_LEAN_AND_MEAN
#      undef WIN32_LEAN_AND_MEAN
#   endif
#   define WIN32_LEAN_AND_MEAN 1
#   include <windows.h>
#else
#   include <dirent.h>
#   include <sys/stat.h>  //mkdir()
#endif
#include <fstream>

static void create_directorys(const std::string &fnfull);
static void add_fn_part(std::string &fnpath, const char *part);

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

FileWriterCallback::FileWriterCallback(org::xmlBlaster::util::Global &global, std::string &dirName, std::string &tmpDirName, std::string &lockExtention, bool overwrite, bool keepDumpFiles)
   : ME("FileWriterCallback"),
     global_(global),
          BUF_SIZE(300000),
     dirName_(dirName),
     lockExtention_(lockExtention),
     overwrite_(overwrite),
     tmpDirName_(tmpDirName),
     keepDumpFiles_(keepDumpFiles),
     directory_(dirName),
     tmpDirectory_(tmpDirName),
     log_(global.getLog("org.xmlBlaster.contrib"))
{
        bufSize_ = 120000;
        buf_ = new char[bufSize_];

        try {
                // test creation of files in the directory and temporary directory and throw an exception if it is not possible to
                // create a file
                std::string completeFilename;
                completeFilename.append(dirName).append(FILE_SEP).append("__checkCreation.dat");
                std::ofstream out(completeFilename.c_str());
                if (!out.is_open()) {
                        std::string txt("can not open files in directory '" + dirName + "'");
                        std::string location(ME + "::" + ME);
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
        out.close();
                if (::remove(completeFilename.c_str()) != 0) {
                        std::string location(ME + "::" + ME);
                        std::string txt("can not remove open files in directory '" + dirName + "'");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
                
                std::string completeTmpFilename;
                completeTmpFilename.append(tmpDirName).append(FILE_SEP).append("__checkTmpCreation.dat");
                std::ofstream outTmp(completeTmpFilename.c_str());
                if (!outTmp.is_open()) {
                        std::string txt("can not open files in temporary directory '" + tmpDirName + "'");
                        std::string location(ME + "::" + ME);
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
        outTmp.close();
                if (::remove(completeTmpFilename.c_str()) != 0) {
                        std::string location(ME + "::" + ME);
                        std::string txt("can not remove open files in temporary directory '" + tmpDirName + "'");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
        }
        catch (XmlBlasterException &ex) {
                throw ex;
        }
        catch (exception &ex) {
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::" + ME, ex.what());
        }
        catch (...) {
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::" + ME, "unknown exception");
        }
                
}

FileWriterCallback::~FileWriterCallback()
{
        delete[] buf_;
}


void FileWriterCallback::storeChunk(std::string &tmpDir, std::string &fileName, long chunkNumber, std::string &sep, bool overwrite, const char *content, long length) 
{

//   private static void storeChunk(File tmpDir, String fileName, long chunkNumber, char sep, boolean overwrite, InputStream is) throws Exception {

        std::string completeFileName;
        completeFileName.append(tmpDir).append(FILE_SEP).append(fileName).append(sep).append(lexical_cast<std::string>(chunkNumber));

        if (!overwrite) {
                std::ofstream file(completeFileName.c_str(), ios::in | ios::binary);
                if (file.is_open()) {
                        std::string txt("file '" + completeFileName + "' exists already and 'overwrite' is set to 'true', can not continue until you manually remove this file");
                        std::string location(ME + "::storeChunk");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
                file.close();
        }
        
        std::ofstream file(completeFileName.c_str(), ios::out | ios::binary);
        if (!file.is_open()) {
                std::string txt("chunk '" + lexical_cast<std::string>(chunkNumber) + "' for file '" + completeFileName + "' could not be written: check the write permissions on the directory");
                std::string location(ME + "::storeChunk");
                throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
        }
        
        try {
                // log.info(ME, "storing file '" + completeFileName + "'");
                file.write(content, length);
      file.close();
   }
        catch (exception &ex) {
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::storeChunk", ex.what());
        }
        catch (...) {
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::storeChunk", "unknown exception");
        }
                
}

long FileWriterCallback::extractNumberPostfixFromFile(std::string &filename, std::string &prefix) 
{
        if (filename.length() < 1)
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::extractNumberPostfixFromFile", "The filename is empty");

        int prefixLength = prefix.length();
        if (filename.find(prefix) == 0) {
        std::string postfix = filename.substr(prefixLength);
      if (postfix.length() < 1)
        return -1L;
                try {
                        return lexical_cast<long>(postfix);
                }
                catch (...) {
                        return -1L;
                }
        }
        return -1L;
}

using namespace std;
void FileWriterCallback::getdir(std::string &dir, std::string &prefix, vector<string> &files)
{
#ifdef _WIN32
        // return all files in dir, prefix?
        HANDLE          hFile = NULL;   /*  Find file handle */
        WIN32_FIND_DATA FileData;       /*  Find file info structure */

        char buf[256];
        int  buf_len = 255;
        if (!GetCurrentDirectory(buf_len,buf)) { return; } // error !

        SetCurrentDirectory(dir.c_str()); // ev. \ /  handling?

        hFile = FindFirstFile( "*", &FileData );
        if ( hFile == INVALID_HANDLE_VALUE) return;
        while (1) {
                if (!(FileData.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) &&  std::string(FileData.cFileName).find_first_of(prefix) == 0)
                        files.push_back(FileData.cFileName);
                if ( !FindNextFile( hFile, &FileData) ) break;
        }
        FindClose(hFile);

        SetCurrentDirectory(buf);
#else
        DIR *dp;
        struct dirent *dirp;
        if((dp  = opendir(dir.c_str())) == NULL) {
                std::string txt("could not retrieve files from directory '" + dir + "'");
                std::string location(ME + "::getDir");
                throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
   }
        while ((dirp = readdir(dp)) != NULL &&  std::string(dirp->d_name).find_first_of(prefix) == 0) {
                files.push_back(string(dirp->d_name));
        }
        closedir(dp);
#endif
}

void FileWriterCallback::getChunkFilenames(std::string &fileName, std::string &sep, std::vector<std::string> &filenames) 
{
        // scan for all chunks:
        std::string prefix("");
        prefix.append(fileName).append(sep);

        std::vector<std::string> files;
        getdir(tmpDirectory_, prefix, files);
        
        std::vector<std::string>::iterator iter = files.begin();
        std::map<int, std::string> fileMap;

        while (iter != files.end()) {
                long postfix = extractNumberPostfixFromFile(*iter, prefix);
                if (postfix > -1L)
                        fileMap.insert(pair<const int, std::string>(postfix, *iter));
                // else
                //      log.warning("");
                iter++;
        }
        std::map<int, std::string>::const_iterator mapIter = fileMap.begin();
        while (mapIter != fileMap.end()) {
                std::string tmp((*mapIter).second);
                filenames.insert(filenames.end(), tmp);
                mapIter++;
        }
}

void FileWriterCallback::putAllChunksTogether(std::string &fileName, std::string &subDir, long expectedChunks, const char *buf, long bufSize, bool isCompleteMsg) 
{
        log_.info(ME + "::putAllChunksTogether", "file='" + fileName + "' expectedChunks='" + lexical_cast<std::string>(expectedChunks) + "'");
        std::string completeFileName;(directory_);

        if (subDir != "") {
                completeFileName = directory_;
                add_fn_part(completeFileName, subDir.c_str());
                add_fn_part(completeFileName, fileName.c_str());
                // check if directories exists, if not create, argument must include the filename
                create_directorys(completeFileName);
        }
        else {
                completeFileName = directory_;
                completeFileName.append(FILE_SEP).append(fileName);
        }
        
        if (!overwrite_) {
                std::ofstream file(completeFileName.c_str(), ios::in | ios::binary);
                if (file.is_open()) {
                        std::string txt("file '" + completeFileName + "' exists already and 'overwrite' is set to 'true', can not continue until you manually remove this file");
                        std::string location(ME + "::putAllChunksTogether");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
                file.close();
        }

        try {
                // first create the lock file
                std::string lockName;
                lockName.append(completeFileName).append(lockExtention_);
                fstream lock(lockName.c_str(), ios::out);
                lock << "lock" << std::endl;
                lock.close();

                std::ofstream file(completeFileName.c_str(), ios::out | ios::binary);
                if (!file.is_open()) {
                        std::string txt("file '" + completeFileName + "' could not be opended");
                        std::string location(ME + "::putAllChunksTogether");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }

                std::vector<std::string> filenames;
                if (expectedChunks > 0) { // the last one is already added at the end directly
                        std::string sep(".");
                        getChunkFilenames(fileName, sep, filenames); // retrieves the chunks in correct order
                        long length = filenames.size();
                        if (length > expectedChunks)
                                log_.warn(ME, "Too many chunks belonging to '" + fileName + "' are found. They are '" + lexical_cast<std::string>(filenames.size()) + "' but should be '" + lexical_cast<std::string>(expectedChunks) + "'");
                        else if (length < expectedChunks) {
                                std::string tmp;
                                fstream file(completeFileName.c_str());
                                if (file.is_open()) {
                                        log_.warn(ME, "The number of chunks is '" + lexical_cast<std::string>(filenames.size()) + "' which is less than the expected '" + lexical_cast<std::string>(expectedChunks) + "' but the file '" + completeFileName + "' exists. So we will use the exisiting file (the chunks where probably already deleted)");
                                        ::remove(lockName.c_str());
                                        return;
                                }
                                else {
                                        std::string txt("Too few chunks belonging to '" + fileName + "' are found. They are '" + lexical_cast<std::string>(filenames.size()) + "' but should be '" + lexical_cast<std::string>(expectedChunks) + "'");
                                        std::string location(ME + "::putAllChunksTogether");
                                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                                }
                        }
                }
                std::vector<std::string>::const_iterator iter = filenames.begin();
                // to first create the file (without adding anything since it is closed after each tmp file)
                fstream tmp(completeFileName.c_str(), ios::out | ios::binary);
                tmp.close();
                
                while (iter != filenames.end()) {
                        fstream dest(completeFileName.c_str(), ios::out | ios::app | ios::binary);
                        std::string inName("");
                        inName.append(tmpDirectory_).append(FILE_SEP).append(*iter);
                        fstream in(inName.c_str(), ios::in | ios::binary);
                        if (!in)
                                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::storeChunk", "could not read the content of the chunk (exception when opening the file): " + inName + "'");
                        if (!in.is_open())
                                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::storeChunk", "the file is not open for reading");
                        
                        int numRead = 1;
                        while ( !in.eof() ) {
                                in.read(buf_, bufSize_);
                                numRead = in.gcount();
                                if (numRead > 0)
                                        dest.write(buf_, numRead);
                        }
                        in.close();
                        dest.close();
                        iter++;
                }

                fstream dest(completeFileName.c_str(), ios::out | ios::app | ios::binary);
                dest.write(buf, bufSize);
                dest.close();

                // clean up all chunks since complete file created
      if (!isCompleteMsg && ! keepDumpFiles_) {
                        iter = filenames.begin();
                        while (iter != filenames.end()) {
                                std::string fileToDelete("");
                                fileToDelete.append(tmpDirectory_).append(FILE_SEP).append(*iter);
                                deleteFile(fileToDelete);
                                iter++;
                        }
      }
      
                if (::remove(lockName.c_str()) != 0) {
                        std::string location(ME + "::putAllChunksTogether");
                        std::string txt("can not remove lock file '" + lockName + "'");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
                }
   }
        catch (XmlBlasterException &ex) {
                throw ex;
        }
        catch (exception &ex) {
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::storeChunk", ex.what());
        }
        catch (...) {
                throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::storeChunk", "unknown exception");
        }
                
}

bool FileWriterCallback::deleteFile(std::string &file) 
{
        return ::remove(file.c_str()) != 0;
}



std::string FileWriterCallback::update(const std::string&,
                       org::xmlBlaster::client::key::UpdateKey &updateKey,
                       const unsigned char *content, long contentSize,
                       org::xmlBlaster::client::qos::UpdateQos &updateQos) 
{

        std::map<std::string, org::xmlBlaster::util::qos::ClientProperty> props =
                updateQos.getClientProperties();
        
        std::string filename("");
        std::string exMsg("");
        long chunkCount = 0;
        std::string subDir;
        bool isLastMsg = true;
        std::string topic = updateKey.getOid();

        std::map<std::string, org::xmlBlaster::util::qos::ClientProperty>::const_iterator iter;
        
        iter = props.find(Constants::FILENAME_ATTR);
        if (iter != props.end())
                filename = ((*iter).second).getStringValue();
        else {
                iter = props.find(Constants::TIMESTAMP_ATTR);           
                if (iter != props.end()) {
                        std::string timestamp = ((*iter).second).getStringValue();
                        filename.append("xbl").append(timestamp).append(".msg");
                }
                else {
                        std::string txt("update: the message '" + topic + "' should contain either the filename or the timestamp in the properties, but none was found. Can not create a filename to store the data on.");
                        std::string location(ME + "::putAllChunksTogether");
                        throw XmlBlasterException(USER_ILLEGALARGUMENT, location.c_str(), txt.c_str());
        }
        }
        
        iter = props.find("_subdir"); // or define Constants::SUBDIR_ATTR in Constants.h .cpp
        if (iter != props.end())
                subDir = ((*iter).second).getStringValue();
        
        iter = props.find(Constants::JMSX_GROUP_SEQ);           
        if (iter != props.end()) {
                isLastMsg = false;
                chunkCount = lexical_cast<int>(((*iter).second).getStringValue());
                iter = props.find(Constants::JMSX_GROUP_EOF);
                if (iter != props.end()) {
                        isLastMsg = lexical_cast<bool>(((*iter).second).getStringValue());
                        iter = props.find(Constants::JMSX_GROUP_EX);
                        if (iter != props.end())
                                exMsg = ((*iter).second).getStringValue();
                }
        }
        else
                isLastMsg = true;
        
        if (filename.length() < 1) {
                filename = topic;
      log_.warn(ME, "The message did not contain any filename nor timestamp. Will write to '" + filename + "'");
        }
   log_.trace(ME, "storing file '" + filename + "' on directory '" + directory_ + "'");

        bool isCompleteMsg = isLastMsg && chunkCount == 0L;
        if (exMsg.length() < 1) { // no exception
                if (isLastMsg)
                        putAllChunksTogether(filename, subDir, chunkCount, (const char*)content, contentSize, isCompleteMsg);
                else {
                        std::string sep(".");
                        storeChunk(tmpDirectory_, filename, chunkCount, sep, overwrite_, (const char*)content, contentSize);
                }
        }
   else if (!isCompleteMsg) { // clean up old chunks
        std::vector<std::string> filenames;
        std::string sep(".");
                getChunkFilenames(filename, sep, filenames); // retrieves the chunks in correct order
                std::vector<std::string>::const_iterator fileIter = filenames.begin();
                while (fileIter != filenames.end()) {
                        std::string tmp((*fileIter));
                        deleteFile(tmp);
                        fileIter++;
                }
   }
        return "OK";
}
                       

}}} // namespaces


// #include <string>
using namespace std;

#ifdef _WIN32
#       define DIR_SEP '\\'
#       define DIR_SEP_STR "\\"
#       include <io.h>
#else
#       define DIR_SEP '/'
#       define DIR_SEP_STR "/"
#       include <unistd.h>
#endif

static void add_fn_part(string &fnpath, const char *part)
{
        int len = fnpath.size();
        if (len) {
                if (fnpath[len-1] != DIR_SEP) fnpath += DIR_SEP_STR;
        }
        fnpath += part;
}

static void create_directorys(const std::string &fnfull)
{
        char    *p,*pn,*buf;
        int     part_len;
        int   len = strlen(fnfull.c_str())+1;
        char  *fn = new char[len];

        strcpy(fn, fnfull.c_str());
        buf = new char[len];
        p = fn;

#       ifdef _WIN32
                if ( *(p+1) == ':' ) p += 2;
#       endif

        while ((pn = strchr(p,DIR_SEP)) != (char *)0) {

                part_len = pn - fn;

                if (!part_len) {
                        p = pn + 1;
                        continue;
                }

                strncpy(buf,fn, part_len + 1);
                buf[part_len] = 0;

                if (access(buf,4)) {
#               ifdef _WIN32
                        CreateDirectory(buf,(LPSECURITY_ATTRIBUTES)0);
#               else
                        mkdir(buf, (mode_t)0); /* umask setzt mode */
#               endif
                }
                p = pn + 1;
        }

        delete [] fn;
        delete [] buf;
}


#ifdef _XMLBLASTER_CLASSTEST

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
       I_Log& log = glob.getLog("org.xmlBlaster.client");

       std::string dirName("/home/michele/testWriteFile");
       std::string tmpDirName("/home/michele/testWriteFile/tmp");
       std::string lockExtention(".lck");
       bool overwrite = true;
       bool keepDumpFiles = false;
       org::xmlBlaster::contrib::FileWriterCallback callback(glob, dirName, tmpDirName, lockExtention, overwrite, keepDumpFiles);
       
       std::string test1("dummy.dat.128");
       std::string test2("dummy.dat.");
       std::cout << "Value: " << callback.extractNumberPostfixFromFile(test1, test2) << std::endl;
       
       std::string filename("dummy.dat");
       std::string sep(".");
       std::vector<std::string> filenames;
                 callback.getChunkFilenames(filename, sep, filenames);
                 std::vector<std::string>::const_iterator iter = filenames.begin();
                 while (iter != filenames.end()) {
                        std::cout << (*iter) << std::endl;
                        iter++;
                 }
       
       log.info("PROG", "The program ends");
   }
   catch (XmlBlasterException &ex) {
      std::cout << ex.toXml() << std::endl;
      // std::cout << ex.getRawMessage() << std::endl;
   }
   return 0;
}

#endif
