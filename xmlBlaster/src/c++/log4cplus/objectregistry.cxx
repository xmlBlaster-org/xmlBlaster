// Module:  Log4CPLUS
// File:    objectregistry.cxx
// Created: 3/2003
// Author:  Tad E. Smith
//
//
// Copyright (C) Tad E. Smith  All rights reserved.
//
// This software is published under the terms of the Apache Software
// License version 1.1, a copy of which has been included with this
// distribution in the LICENSE.APL file.
//
// $Log: objectregistry.cxx,v $
// Revision 1.2  2004/02/11 08:45:05  ruff
// Updated to version 1.0.2
//
// Revision 1.3  2003/04/18 21:57:40  tcsmith
// Converted from std::string to log4cplus::tstring.
//
// Revision 1.2  2003/04/03 01:10:38  tcsmith
// Standardized the formatting.
//

#include <log4cplus/spi/objectregistry.h>

using namespace std;
using namespace log4cplus::spi;


///////////////////////////////////////////////////////////////////////////////
// log4cplus::spi::ObjectRegistryBase ctor and dtor
///////////////////////////////////////////////////////////////////////////////

log4cplus::spi::ObjectRegistryBase::ObjectRegistryBase()
 : mutex(LOG4CPLUS_MUTEX_CREATE)
{
}


log4cplus::spi::ObjectRegistryBase::~ObjectRegistryBase()
{
    LOG4CPLUS_MUTEX_FREE( mutex );
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::spi::ObjectRegistryBase public methods
///////////////////////////////////////////////////////////////////////////////

bool
log4cplus::spi::ObjectRegistryBase::exists(const log4cplus::tstring& name) const
{
    LOG4CPLUS_BEGIN_SYNCHRONIZE_ON_MUTEX( mutex )
        return data.find(name) != data.end();
    LOG4CPLUS_END_SYNCHRONIZE_ON_MUTEX
}


std::vector<log4cplus::tstring>
log4cplus::spi::ObjectRegistryBase::getAllNames() const
{
    LOG4CPLUS_BEGIN_SYNCHRONIZE_ON_MUTEX( mutex )
        std::vector<log4cplus::tstring> tmp;
        for(ObjectMap::const_iterator it=data.begin(); it!=data.end(); ++it) {
            tmp.push_back( (*it).first );
        }

        return tmp;
    LOG4CPLUS_END_SYNCHRONIZE_ON_MUTEX
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::spi::ObjectRegistryBase protected methods
///////////////////////////////////////////////////////////////////////////////

bool
log4cplus::spi::ObjectRegistryBase::putVal(const log4cplus::tstring& name, void* object)
{
    LOG4CPLUS_BEGIN_SYNCHRONIZE_ON_MUTEX( mutex )
        ObjectMap::value_type value(name, object);
        std::pair<ObjectMap::iterator, bool> ret = data.insert(value);

        if(ret.second) {
            return true;
        }
        else {
            deleteObject( value.second );
            return false;
        }
    LOG4CPLUS_END_SYNCHRONIZE_ON_MUTEX
}


void*
log4cplus::spi::ObjectRegistryBase::getVal(const log4cplus::tstring& name) const
{
    bool found = exists(name);
    LOG4CPLUS_BEGIN_SYNCHRONIZE_ON_MUTEX( mutex )
        if(found) {
            return data.find(name)->second;
        }
        else {
            return 0;
        }
    LOG4CPLUS_END_SYNCHRONIZE_ON_MUTEX
}




void
log4cplus::spi::ObjectRegistryBase::clear()
{
    LOG4CPLUS_BEGIN_SYNCHRONIZE_ON_MUTEX( mutex )
    for(ObjectMap::iterator it=data.begin(); it!=data.end(); ++it) {
        deleteObject( (*it).second );
    }
    LOG4CPLUS_END_SYNCHRONIZE_ON_MUTEX
}


