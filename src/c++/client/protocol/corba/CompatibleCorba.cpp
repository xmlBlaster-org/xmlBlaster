/*----------------------------------------------------------------------------
Name:      CompatibleCorba.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   functions to encapsulate corba-implementor specific stuff
Version:   $Id$
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


# ifdef XBL_IDL_WITH_WSTRING
void string2wstring(std::wstring &dest,const std::string &src)
{
    dest.resize(src.size());
    for (uint32_t i=0; i<src.size(); i++)
        dest[i] = static_cast<unsigned char>(src[i]);
/*
   int xlen = src.size()+1;
   wchar_t *x = new wchar_t[xlen]; // Temp buffer
   mbstowcs(x, src.c_str(), xlen);
   dest = x;
   delete [] x; // Clean up temp buffer

   mbstate_t ps;
   int xlen = src.size()+1;
   wchar_t *cdest = new wchar_t[xlen]; // Temp buffer
   mbsrtowcs(cdest, &src.c_str(), xlen, &ps);
   dest = cdest;
   delete [] cdest; // Clean up temp buffer
*/
}

void wstring2string(std::string &dest,const std::wstring &src)
{
    dest.resize(src.size());
    for (uint32_t i=0; i<src.size(); i++)
        dest[i] = src[i] < 256 ? src[i] : ' ';
}

std::wstring toWstring(const std::string &src)
{
   std::wstring dest;
   string2wstring(dest,src);
   return dest;
}


std::string toString(const std::wstring &src)
{
   string dest;
   wstring2string(dest,src);
   return dest;
}

std::string corbaWStringToString(const CORBA::WString_var &src)
{
   std::string dest;
   wstring2string(dest,wstring(src));
   return dest;
}

CORBA::WString_var toCorbaWString(const std::string &src)
{
   setlocale(LC_ALL, ""); // set the locale
   std::wstring dest = toWstring(src);
   //wcout << L"CompatibleCorba: Sending now: " << dest << endl;
   CORBA::WString_var var = CORBA::wstring_dup(dest.c_str());
   //cout << "CompatibleCorba: Sending now CORBA: " << corbaWStringToString(var) << endl;
   return var;
   // Who does CORBA::wstring_free() ??
}
# else // XBL_IDL_WITH_WSTRING
std::string corbaWStringToString(const char *src)
{
   std::string dest(src);
   return dest;
}

const char *toCorbaWString(const std::string &src)
{
   return src.c_str();
}
# endif // XBL_IDL_WITH_WSTRING


/******************************************************************
 *                      OMNIORB (4.1.1)
 ******************************************************************/
#ifdef  XMLBLASTER_OMNIORB

string to_string(const CORBA::Exception &ex) {
   string name(ex._name());
   string repId(ex._rep_id());

   string ret = "CORBA::Exception name=" + name
          + " repId=" + repId;
   return ret;
}

#endif  // XMLBLASTER_OMNIORB

/******************************************************************
 *                      ORBACUS (OB-4.03)
 ******************************************************************/
#ifdef  XMLBLASTER_ORBACUS

string to_string(const CORBA::Exception &ex) {
   string name(ex._name());
   string repId(ex._rep_id());

   string ret = "CORBA::Exception name=" + name
          + " repId=" + repId;
   return ret;
}

#endif  // XMLBLASTER_ORBACUS

/*****************************************************************
 *                     MICO (ver. 2.3.1)
 *****************************************************************/
#ifdef  XMLBLASTER_MICO

string to_string(const CORBA::Exception &ex) {
   return ex._repoid();
}

#endif  // XMLBLASTER_MICO

/*****************************************************************
 *                    ORBIX 2000 (ver 2.0) 
 *****************************************************************/
#ifdef XMLBLASTER_ORBIX

string to_string(const CORBA::Exception &ex ) {
  return ex._rep_id();
}

#endif //XMLBLASTER_ORBIX

#ifdef  XMLBLASTER_TAO

string to_string(const CORBA::Exception &ex) {
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

#endif  // XMLBLASTER_TAO

