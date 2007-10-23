// Module:  Log4CPLUS
// File:    loglevel.cxx
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
// $Log: loglevel.cxx,v $
// Revision 1.2  2004/02/11 08:45:05  ruff
// Updated to version 1.0.2
//
// Revision 1.9  2003/09/28 04:29:58  tcsmith
// Added OFF and ALL LogLevels.
//
// Revision 1.8  2003/07/30 05:03:25  tcsmith
// Changed LogLevelManager so that the "toString" and "fromString" methods are
// list based.
//
// Revision 1.7  2003/06/12 23:51:45  tcsmith
// Modified to support the rename of the toupper and tolower methods.
//
// Revision 1.6  2003/05/02 22:43:04  tcsmith
// Moved the LogLevel string defintions out of the defaultLogLevelToStringMethod()
// method.
//
// Revision 1.5  2003/04/19 23:04:31  tcsmith
// Fixed UNICODE support.
//
// Revision 1.4  2003/04/19 07:22:18  tcsmith
// Removed all LogLevelToSysLogMethod methods.
//
// Revision 1.3  2003/04/18 21:07:05  tcsmith
// Converted from std::string to log4cplus::tstring.
//
// Revision 1.2  2003/04/03 02:03:09  tcsmith
// Changed defaultLogLevelToSysLogMethod() so that it returns -1 for unknow
// LogLevels.
//
// Revision 1.1  2003/04/03 01:42:19  tcsmith
// Renamed from priority.cxx
//

#include <log4cplus/loglevel.h>
#include <log4cplus/helpers/loglog.h>
#include <log4cplus/helpers/stringhelper.h>
#include <algorithm>

using namespace log4cplus;
using namespace log4cplus::helpers;

#define _ALL_STRING LOG4CPLUS_TEXT("ALL")
#define _TRACE_STRING LOG4CPLUS_TEXT("TRACE")
#define _DEBUG_STRING LOG4CPLUS_TEXT("DEBUG")
#define _INFO_STRING LOG4CPLUS_TEXT("INFO")
#define _WARN_STRING LOG4CPLUS_TEXT("WARN")
// Add to build.properties
//CFLAGS=-DLOG4CPLUS_SEVERE_INSTEADOF_ERROR
// to use "SEVERE" similar to Java logging
#ifdef LOG4CPLUS_SEVERE_INSTEADOF_ERROR
#define _ERROR_STRING LOG4CPLUS_TEXT("SEVERE")
#else
#define _ERROR_STRING LOG4CPLUS_TEXT("ERROR")
#endif
#define _FATAL_STRING LOG4CPLUS_TEXT("FATAL")
#define _OFF_STRING LOG4CPLUS_TEXT("OFF")
#define _NOTSET_STRING LOG4CPLUS_TEXT("NOTSET")
#define _UNKNOWN_STRING LOG4CPLUS_TEXT("UNKNOWN")

#define GET_TO_STRING_NODE static_cast<ToStringNode*>(this->toStringMethods)
#define GET_FROM_STRING_NODE static_cast<FromStringNode*>(this->fromStringMethods)



//////////////////////////////////////////////////////////////////////////////
// file LOCAL definitions
//////////////////////////////////////////////////////////////////////////////

namespace {
    class ToStringNode {
    public:
        ToStringNode(LogLevelToStringMethod m) : method(m), next(0) {}

        LogLevelToStringMethod method;
        ToStringNode* next;
    };
    
    
    class FromStringNode {
    public:
        FromStringNode(StringToLogLevelMethod m) : method(m), next(0) {}

        StringToLogLevelMethod method;
        FromStringNode* next;
    };
    
    
    
    log4cplus::tstring
    defaultLogLevelToStringMethod(LogLevel ll) {
        switch(ll) {
            case OFF_LOG_LEVEL:     return _OFF_STRING;
            case FATAL_LOG_LEVEL:   return _FATAL_STRING;
            case ERROR_LOG_LEVEL:   return _ERROR_STRING;
            case WARN_LOG_LEVEL:    return _WARN_STRING;
            case INFO_LOG_LEVEL:    return _INFO_STRING;
            case DEBUG_LOG_LEVEL:   return _DEBUG_STRING;
            case TRACE_LOG_LEVEL:   return _TRACE_STRING;
            //case ALL_LOG_LEVEL:     return _ALL_STRING;
            case NOT_SET_LOG_LEVEL: return _NOTSET_STRING;
        };
        
        return tstring();
    }
    
    
    LogLevel
    defaultStringToLogLevelMethod(const log4cplus::tstring& arg) {
        log4cplus::tstring s = log4cplus::helpers::toUpper(arg);
        
        if(s == _ALL_STRING)   return ALL_LOG_LEVEL;
        if(s == _TRACE_STRING) return TRACE_LOG_LEVEL;
        if(s == _DEBUG_STRING) return DEBUG_LOG_LEVEL;
        if(s == _INFO_STRING)  return INFO_LOG_LEVEL;
        if(s == _WARN_STRING)  return WARN_LOG_LEVEL;
        if(s == _ERROR_STRING) return ERROR_LOG_LEVEL;
        if(s == _FATAL_STRING) return FATAL_LOG_LEVEL;
        if(s == _OFF_STRING)   return OFF_LOG_LEVEL;
        
        return NOT_SET_LOG_LEVEL;
    }
    
}



//////////////////////////////////////////////////////////////////////////////
// public static methods
//////////////////////////////////////////////////////////////////////////////

LogLevelManager&
log4cplus::getLogLevelManager() 
{
    static LogLevelManager singleton;
    return singleton;
}



//////////////////////////////////////////////////////////////////////////////
// log4cplus::LogLevelManager ctors and dtor
//////////////////////////////////////////////////////////////////////////////

LogLevelManager::LogLevelManager() 
: toStringMethods(new ToStringNode(defaultLogLevelToStringMethod)),
  fromStringMethods(new FromStringNode(defaultStringToLogLevelMethod))
{
}



LogLevelManager::~LogLevelManager() 
{
    ToStringNode* toStringTmp = GET_TO_STRING_NODE;
    while(toStringTmp) {
        ToStringNode* tmp = toStringTmp;
        toStringTmp = toStringTmp->next;
        delete tmp;
    }
    
    FromStringNode* fromStringTmp = GET_FROM_STRING_NODE;
    while(fromStringTmp) {
        FromStringNode* tmp = fromStringTmp;
        fromStringTmp = fromStringTmp->next;
        delete tmp;
    }
}



//////////////////////////////////////////////////////////////////////////////
// log4cplus::LogLevelManager public methods
//////////////////////////////////////////////////////////////////////////////

log4cplus::tstring 
LogLevelManager::toString(LogLevel ll) const
{
    ToStringNode* toStringTmp = GET_TO_STRING_NODE;
    while(toStringTmp) {
        tstring ret = toStringTmp->method(ll);
        if(ret.length() > 0) {
            return ret;
        }
        toStringTmp = toStringTmp->next;
    }
    
    return _UNKNOWN_STRING;
}



LogLevel 
LogLevelManager::fromString(const log4cplus::tstring& s) const
{
    FromStringNode* fromStringTmp = GET_FROM_STRING_NODE;
    while(fromStringTmp) {
        LogLevel ret = fromStringTmp->method(s);
        if(ret != NOT_SET_LOG_LEVEL) {
            return ret;
        }
        fromStringTmp = fromStringTmp->next;
    }
    
    return NOT_SET_LOG_LEVEL;
}



void 
LogLevelManager::pushToStringMethod(LogLevelToStringMethod newToString)
{
    ToStringNode* toStringTmp = GET_TO_STRING_NODE;
    while(1) {
        if(toStringTmp->next) {
            toStringTmp = toStringTmp->next;
        }
        else {
            toStringTmp->next = new ToStringNode(newToString);
            break;
        }
    }
}



void 
LogLevelManager::pushFromStringMethod(StringToLogLevelMethod newFromString)
{
    FromStringNode* fromStringTmp = GET_FROM_STRING_NODE;
    while(1) {
        if(fromStringTmp->next) {
            fromStringTmp = fromStringTmp->next;
        }
        else {
            fromStringTmp->next = new FromStringNode(newFromString);
            break;
        }
    }
}
        
