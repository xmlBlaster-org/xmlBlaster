/*------------------------------------------------------------------------------
Name:      Base64.cpp
Project:   xmlBlaster.org
Copyright: 2001-2002 Randy Charles Morin randy@kbcafe.com
Comment:   http://www.kbcafe.com/articles/HowTo.Base64.pdf
           Allowed to distribute under xmlBlasters LGPL in email
           from Randy Charles Morin <randy@kbcafe.com> from 2004-01-18
Minor change by Marcel Ruff xmlBlaster@marcelruff.info
http://www.xmlBlaster.org
------------------------------------------------------------------------------*/
#include <string>
#include <vector>
#include <util/Base64.h>

using namespace std;
using namespace org::xmlBlaster::util;

std::string Base64::Encode(const std::vector<unsigned char> & vby)
{
   std::string retval;
   if (vby.size() == 0)
   {
      return retval;
   }
   retval.reserve(vby.size()/3*4+4);
   for (std::vector<unsigned char>::size_type i=0;i<vby.size();i+=3)
   {
      unsigned char by1=0,by2=0,by3=0;
      by1 = vby[i];
      if (i+1<vby.size())
      {
         by2 = vby[i+1];
      };
      if (i+2<vby.size())
      {
         by3 = vby[i+2];
      }
      unsigned char by4=0,by5=0,by6=0,by7=0;
      by4 = by1>>2;
      by5 = ((by1&0x3)<<4)|(by2>>4);
      by6 = ((by2&0xf)<<2)|(by3>>6);
      by7 = by3&0x3f;
      retval += Encode(by4);
      retval += Encode(by5);
      if (i+1<vby.size())
      {
         retval += Encode(by6);
      }
      else
      {
         retval += "=";
      };
      if (i+2<vby.size())
      {
         retval += Encode(by7);
      }
      else
      {
         retval += "=";
      }
   }
   return retval;
}

std::vector<unsigned char> Base64::Decode(const std::string & _str)
{
   std::string str;
   for (std::string::size_type j=0;j<_str.length();j++)
   {
      if (IsBase64(_str[j]))
      {
         str += _str[j];
      }
   }
   std::vector<unsigned char> retval;
   if (str.length() == 0)
   {
      return retval;
   }
   retval.reserve(str.size()/4*3+3);
   for (std::string::size_type i=0;i<str.length();i+=4)
   {
      char c1='A',c2='A',c3='A',c4='A';
      c1 = str[i];
      if (i+1<str.length())
      {
         c2 = str[i+1];
      };
      if (i+2<str.length())
      {
         c3 = str[i+2];
      };
      if (i+3<str.length())
      {
         c4 = str[i+3];
      };
      unsigned char by1=0,by2=0,by3=0,by4=0;
      by1 = Decode(c1);
      by2 = Decode(c2);
      by3 = Decode(c3);
      by4 = Decode(c4);
      retval.push_back( (by1<<2)|(by2>>4) );
      if (c3 != '=')
      {
         retval.push_back( ((by2&0xf)<<4)|(by3>>2) );
      }
      if (c4 != '=')
      {
         retval.push_back( ((by3&0x3)<<6)|by4 );
      }
   }
   return retval;
}


#ifdef BASE64_MAIN
// For testing only
// g++ -Wall -g -o Base64 Base64.cpp -DBASE64_MAIN=1 -I../
#include <iostream>
#include <fstream>
static void inout(char * szFilename);

int main(int argc, char* argv[])
{
   if (argc != 2)
      std::cerr << "Usage: base64 [file]" << std::endl;
   inout(argv[1]);
   return 0;
}

void inout(char * szFilename)
{
   std::vector<unsigned char> vby1;

   {  // Read the given file ...
      std::ifstream infile;
      infile.open(szFilename, std::ios::binary); //std::ios::in+std::ios::binary);
      if (!infile.is_open())
      {
         std::cerr << "File not open!";
         return;
      }
      infile >> std::noskipws;
      unsigned char uc;
      while (true)
      {
         infile >> uc;
         if (!infile.eof())
         {
            vby1.push_back(uc);
         }
         else
         {
            break;
         }
      }
      std::cout << "File length is " << vby1.size() << std::endl;
   }

   // Encode the file content ...
   std::vector<unsigned char>::iterator j;
   std::string str = Base64::Encode(vby1);
   std::cout << "Interim" << std::endl;
   std::cout << str << std::endl << std::endl;

   {  // Dump encoding to file ...
      std::ofstream outfile;
      std::string strOutfile = szFilename;
      strOutfile += ".b64";
      outfile.open(strOutfile.c_str(), std::ios::binary); // std::ios::out+std::ios::binary);
      if (!outfile.is_open())
      {
        std::cerr << "File not open!";
         return;
      }
      outfile << str;
      std::cout << "Dumped encoding to file " << strOutfile << std::endl;
   }

   // decode back again and test the cycle ...
   std::vector<unsigned char> vby2;
   vby2 = Base64::Decode(str);
   std::vector<unsigned char>::iterator k;
   int i=1;
   j = vby2.begin();
   k = vby1.begin();
   if (vby1.size() != vby2.size())
   {
      std::cerr << "Error in size " << vby1.size() << " " << vby2.size() << std::endl;
      return;
   }
   for (;j!=vby2.end();j++,k++,i++)
   {
      if (*j != *k)
      {
         std::cerr << "Error in translation " << i << std::endl;
         return;
      }
   }
   /*
   {
      std::ofstream outfile;
      std::string strOutfile = szFilename;
      strOutfile += ".bak";
      outfile.open(strOutfile.c_str(), std::ios::binary); // std::ios::out+std::ios::binary);
      if (!outfile.is_open())
      {
        std::cerr << "File not open!";
         return;
      }
      unsigned char uc;
      j = vby2.begin();
      for (;j!=vby2.end();j++)
      {
         uc = *j;
         outfile << uc;
      }
   }
   */
   std::cout << "Success, test OK" << std::endl;
   return;
}
#endif // BASE64_MAIN

