/*----------------------------------------------------------------------------
Name:      CompatibleCorba.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   functions to encapsulate corba-implementor specific stuff
Version:   $Id: CompatibleCorba.cpp,v 1.1 2001/12/12 17:30:22 ruff Exp $
Author:    <Michele Laghi> michele.laghi@attglobal.net
----------------------------------------------------------------------------*/

#include <util/CompatibleCorba.h>

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
