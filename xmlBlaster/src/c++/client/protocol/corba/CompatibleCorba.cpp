/*----------------------------------------------------------------------------
Name:      CompatibleCorba.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   functions to encapsulate corba-implementor specific stuff
Version:   $Id: CompatibleCorba.cpp,v 1.5 2003/02/18 21:24:24 laghi Exp $
Author:    <Michele Laghi> laghi@swissinfo.org
----------------------------------------------------------------------------*/
#ifdef _WINDOWS
#pragma warning(disable:4786)
#pragma warning(disable:4251)
#endif

#include <client/protocol/corba/CompatibleCorba.h>
#include <util/lexical_cast.h>

using namespace std;

/*
 * Further implementor specific macros which must be called after having
 * included the CORBA header files 
 */

/******************************************************************
 *                      ORBACUS (OB-4.03)
 ******************************************************************/
#ifdef  ORBACUS

const char* to_string(const CORBA::Exception &ex) {
   return ex._rep_id();
}

#endif  // ORBACUS

/*****************************************************************
 *                     MICO (ver. 2.3.1)
 *****************************************************************/
#ifdef  MICO

const char* to_string(const CORBA::Exception &ex) {
   return ex._repoid();
}

#endif  // MICO

/*****************************************************************
 *                    ORBIX 2000 (ver 2.0) 
 *****************************************************************/
#ifdef ORBIX

const char* to_string(const CORBA::Exception &ex ) {
  return ex._rep_id();
}

#endif //ORBIX

#ifdef  TAO

const char* to_string(const CORBA::Exception &ex) {
   string name(ex._name());
   string repId(ex._rep_id());
   //string typeCode(ex._type());
   //string info(ex._info()); // info is not portable

   string ret = "CORBA::Exception name=" + name
          + " repId=" + repId
          //+ " typeCode=" + typeCode
          //+ " info=" + info
          ;
   return ret.c_str();
}

#endif  // TAO

