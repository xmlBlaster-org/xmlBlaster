/*----------------------------------------------------------------------------
Name:      CompatibleCorba.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   functions to encapsulate corba-implementor specific stuff
Version:   $Id: CompatibleCorba.cpp,v 1.1 2002/12/05 00:01:32 laghi Exp $
Author:    <Michele Laghi> laghi@swissinfo.org
----------------------------------------------------------------------------*/
#pragma warning(disable:4786)

#include <client/protocol/corba/CompatibleCorba.h>

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
