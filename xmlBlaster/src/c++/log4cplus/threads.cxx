// Module:  Log4CPLUS
// File:    threads.cxx
// Created: 6/2001
// Author:  Tad E. Smith
//
//
// Copyright (C) Tad E. Smith  All rights reserved.
//
// This software is published under the terms of the Apache Software
// License version 1.1, a copy of which has been included with this
// distribution in the LICENSE.APL file.
//
// $Log: threads.cxx,v $
// Revision 1.2  2004/02/11 08:45:05  ruff
// Updated to version 1.0.2
//
// Revision 1.18  2003/10/22 06:41:11  tcsmith
// Modified getCurrentThreadName() so that it always uses an ostream to
// convert the Thread ID to a string.
//
// Revision 1.17  2003/09/10 07:01:36  tcsmith
// Added support for NetBSD.
//
// Revision 1.16  2003/08/27 15:22:38  tcsmith
// Corrected the getCurrentThreadName() method for MacOS X and DEC cxx.
//
// Revision 1.15  2003/08/09 06:58:21  tcsmith
// Fixed the getCurrentThreadName() method for MacOS X.
//
// Revision 1.14  2003/08/04 01:10:12  tcsmith
// Modified the getCurrentThreadName() method to use convertIntegerToString().
//
// Revision 1.13  2003/07/19 15:41:04  tcsmith
// Cleaned up the threadStartFunc() method.
//
// Revision 1.12  2003/06/29 16:48:25  tcsmith
// Modified to support that move of the getLogLog() method into the LogLog
// class.
//
// Revision 1.11  2003/06/13 15:25:07  tcsmith
// Added the getCurrentThreadName() function.
//
// Revision 1.10  2003/06/04 18:56:41  tcsmith
// Modified to use the new timehelper.h header.
//
// Revision 1.9  2003/05/22 21:17:32  tcsmith
// Moved the sleep() method into sleep.cxx
//
// Revision 1.8  2003/04/29 17:38:45  tcsmith
// Added "return NULL" to the threadStartFunc() method to make the Sun Forte
// compiler happy.
//
// Revision 1.7  2003/04/18 21:58:47  tcsmith
// Converted from std::string to log4cplus::tstring.
//
// Revision 1.6  2003/04/03 01:31:43  tcsmith
// Standardized the formatting.
//

#ifndef LOG4CPLUS_SINGLE_THREADED

#include <log4cplus/helpers/threads.h>
#include <log4cplus/streams.h>
#include <log4cplus/ndc.h>
#include <log4cplus/helpers/loglog.h>
#include <log4cplus/helpers/stringhelper.h>
#include <log4cplus/helpers/timehelper.h>

#include <exception>
#include <stdexcept>
#include <errno.h>

#if defined(LOG4CPLUS_USE_PTHREADS)
#    include <sched.h>
#endif

using namespace std;
using namespace log4cplus;
using namespace log4cplus::helpers;


///////////////////////////////////////////////////////////////////////////////
// public methods
///////////////////////////////////////////////////////////////////////////////

LOG4CPLUS_MUTEX_PTR_DECLARE 
log4cplus::thread::createNewMutex()
{
#if defined(LOG4CPLUS_USE_PTHREADS)
    pthread_mutex_t* m = new pthread_mutex_t();
    pthread_mutex_init(m, NULL);
#elif defined(LOG4CPLUS_USE_WIN32_THREADS)
    CRITICAL_SECTION* m = new CRITICAL_SECTION();
    InitializeCriticalSection(m);
#endif
    return m;
}


void 
log4cplus::thread::deleteMutex(LOG4CPLUS_MUTEX_PTR_DECLARE m)
{
#if defined(LOG4CPLUS_USE_PTHREADS)
    pthread_mutex_destroy(m);
#elif defined(LOG4CPLUS_USE_WIN32_THREADS)
    DeleteCriticalSection(m);
#endif
    delete m;
}



#if defined(LOG4CPLUS_USE_PTHREADS)
pthread_key_t*
log4cplus::thread::createPthreadKey()
{
    pthread_key_t* key = new pthread_key_t();
    pthread_key_create(key, NULL);
    return key;
}
#endif



void
log4cplus::thread::yield()
{
#if defined(LOG4CPLUS_USE_PTHREADS)
    ::sched_yield();
#elif defined(LOG4CPLUS_USE_WIN32_THREADS)
    ::Sleep(0);
#endif
}


log4cplus::tstring 
log4cplus::thread::getCurrentThreadName()
{
#if 1
    log4cplus::tostringstream tmp;
    tmp << LOG4CPLUS_GET_CURRENT_THREAD;

    return tmp.str();
#else
    return convertIntegerToString(LOG4CPLUS_GET_CURRENT_THREAD);
#endif
}



#if defined(LOG4CPLUS_USE_PTHREADS)
    void* 
    log4cplus::thread::threadStartFunc(void* arg)
#elif defined(LOG4CPLUS_USE_WIN32_THREADS)
    DWORD WINAPI
    log4cplus::thread::threadStartFunc(LPVOID arg)
#endif
{
    SharedObjectPtr<LogLog> loglog = LogLog::getLogLog();
    if(arg == NULL) {
        loglog->error(LOG4CPLUS_TEXT("log4cplus::thread::threadStartFunc()- arg is NULL"));
    }
    else {
        AbstractThread* ptr = static_cast<AbstractThread*>(arg);
        log4cplus::helpers::SharedObjectPtr<AbstractThread> thread(ptr);
        try {
            thread->run();
        }
        catch(std::exception& e) {
            tstring err = LOG4CPLUS_TEXT("log4cplus::thread::threadStartFunc()- run() terminated with an exception: ");
            err += LOG4CPLUS_C_STR_TO_TSTRING(e.what());
            loglog->warn(err);
        }
        catch(...) {
            loglog->warn(LOG4CPLUS_TEXT("log4cplus::thread::threadStartFunc()- run() terminated with an exception."));
        }
        thread->running = false;
        getNDC().remove();
    }

#if defined(LOG4CPLUS_USE_PTHREADS)
    pthread_exit(NULL);
#endif
    return NULL;
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::thread::AbstractThread ctor and dtor
///////////////////////////////////////////////////////////////////////////////

log4cplus::thread::AbstractThread::AbstractThread()
: running(false)
{
}



log4cplus::thread::AbstractThread::~AbstractThread()
{
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::thread::AbstractThread public methods
///////////////////////////////////////////////////////////////////////////////

void
log4cplus::thread::AbstractThread::start()
{
    running = true;
#if defined(LOG4CPLUS_USE_PTHREADS)
    if( pthread_create(&threadId, NULL, threadStartFunc, this) ) {
        throw std::runtime_error(LOG4CPLUS_TEXT("Thread creation was not successful"));
    }
#elif defined(LOG4CPLUS_USE_WIN32_THREADS)
    HANDLE h = CreateThread(NULL, 0, threadStartFunc, (LPVOID)this, 0, &threadId);
    CloseHandle(h);
#endif
}

#endif // LOG4CPLUS_SINGLE_THREADED

