/*----------------------------------------------------------------------------
Name:      CompatibleCorba.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   functions to encapsulate corba-implementor specific stuff
Version:   $Id: CompatibleCorba.cpp,v 1.5 2002/08/27 13:14:05 johnson Exp $
Author:    <Michele Laghi> michele.laghi@attglobal.net
----------------------------------------------------------------------------*/
#pragma warning(disable:4786)

#include <util/CompatibleCorba.h>

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
