
#ifndef _UTIL_PROPERTY_C
#define _UTIL_PROPERTY_C

#include "Property.h"
#include <boost/lexical_cast.hpp>

using namespace boost;
using namespace org::xmlBlaster::util;


/**
 * Loads the properties read from the command line (or another array).
 * The syntax for passing properties is the same as in java if the 
 * switch javaStyle is true (default). That is "-Dprop1=val1" is 
 * then equivalent as prop1=val1 in a property file. If the switch 
 * javaStyle is false, then the Corba style is choosen, i.e. the 
 * following is correct syntax: "-ORBNameService whatever" (so no
 * equality sign between name and value).
 * Errors in syntax are silenty ignored (the property just isn't load).
 */
int 
Property::loadCommandLineProps(int args,
                               const char * const argc[],
                               const string &sep, 
                               bool javaStyle) 
{

   int    count = 1, ret=0, nmax = args;
   string name, value;
   if (!javaStyle) nmax--; // they come in separated pairs
   while (count < nmax) {
      string name = argc[count];
      if (name.find(sep) == 0) { // it is a property
         name = name.substr(sep.length()); // remove separator e.g. "-trace" -> "trace"
         if (!javaStyle) { // Corba style (or other non-java styles)
            count++;
            value = argc[count];
            //std::cout << "readPropertyCommandLine: " << name << "=" << value << std::endl;
            if (setProperty(name, value)) ret++;
         }
         else { // java style
            pair<const string, string> propPair(getPair(name));
            if (setProperty(propPair.first, propPair.second)) ret++;
         }
      }
      count++;
   }
//   if (count > 0)
//      std::cout << "Successfully read " << (count-1)/2 << " command line arguments" << std::endl;
   return ret;
}


/*
 * xmlBlaster.properties is searched in this sequence:
 * <ul>
 *    <li>the command line parameter '-propertyFile', e.g. "-propertyFile /tmp/xy.properties"</li>
 *    <li>the environment variable 'propertyFile', e.g. "propertyFile=/tmp/xy.properties"</li>
 *    <li>the local directory: ./xmlBlaster.properties</li>
 *    <li>in your home directory, HOME/xmlBlaster.properties</li>
 *    <li>in the $XMLBLASTER_HOME directory</li>
 * </ul>
 * Command line properties have precedence<p />
 * Environment variables are weakest
 */

void 
Property::loadPropertyFile()
{
   const string filename = "xmlBlaster.properties";
   string path="";
   int num=0;

   if (num < 1) {
      path = getProperty("propertyFile", false); // command line property
      if (!path.empty())
         num = readPropertyFile(path, false);
   }
   if (num < 1) {
      path = getProperty("propertyFile", true); // looking in environment as well
      if (!path.empty())
         num = readPropertyFile(path, false);
   }
   if (num < 1) {
      num = readPropertyFile(filename, false);
   }
   if (num < 1) {      
     if(getenv("HOME")) {
       path = getenv("HOME");
     if (!path.empty())
       num = readPropertyFile(path + FILE_SEP + filename, false);
     }
   }
   if (num < 1) {
     if(getenv("XMLBLASTER_HOME")) {
       path = getenv("XMLBLASTER_HOME");
     if (!path.empty())
       num = readPropertyFile(path + FILE_SEP + filename, false);
     }
   }
}


/**
 * Reads the file specified in filename. If the name is not valid, or if
 * the system can not write to the specified file, then -1 is returned.
 * If you specify overwrite=true (the default) then the properties read
 * from the file are inserted into the properties even if a property 
 * with the same name has been defined earlier. 
 */
int 
Property::readPropertyFile(const string &filename, bool overwrite) 
{
   ifstream in(filename.c_str());
   string  line;
   int     count = 0;
   if (in == 0) return -1;
   while (!in.eof()) {
      getline(in, line);
      if (!in.eof()) {
         pair<const string, string> valuePair(getPair(line));
         if ((valuePair.first != "") && (valuePair.second != "")) {
            //std::cout << "readPropertyFile: " << valuePair.first << "=" << valuePair.second << std::endl;
            if (setProperty(valuePair.first, valuePair.second, overwrite))
               count++;
         }
      }
   }
   in.close();
//   if (count > 0)
//      std::cout << "Successfully read " << count << " entries from " << filename << std::endl;
   return count;
}


/**
 * writes the properties to a file specified in the argument list. If it
 * could not write to the file, a zero is returned.
 * Returns the number of properties written to the file.
 */
int 
Property::writePropertyFile(const char *filename) const 
{
   ofstream out(filename);
   int      count = 0;
   if (out == 0) return count;
   MapType::const_iterator iter = properties_.begin();
   while (iter != properties_.end()) {
     out << (*iter).first << "=" << (*iter).second << std::endl;
      iter++;
      count++;
   }
   out.close();
   return count;
}

/**
 * Gets the property with the specified name. If no such property exists,
 * an empty string is returned. If the string is not found, it searches
 * in among the environment variables (only if env is set to true which
 * is the default). In the property is not found there either, it returns
 * an empty string.
 */
string 
Property::getProperty(const string &name, bool env) 
{
   MapType::const_iterator iter = properties_.find(name);
   if (iter == properties_.end()) {
      if (!env) return "";
      char* envStr = getenv(name.c_str());
      if (envStr == 0) return "";
      setProperty(name, envStr);
      return string(envStr);
   }
   return (*iter).second;
}


bool 
Property::propertyExists(const string &name, bool env) 
{
   MapType::const_iterator iter = properties_.find(name);
   if (iter == properties_.end()) {
      if (!env) return false;
      char* envStr = getenv(name.c_str());
      if (envStr == 0) return false;
      setProperty(name, envStr);
   }
   return true;
}


int 
Property::getIntProperty(const string &name, int def, bool env) 
{
   string value = getProperty(name, env);
   if (value.length() == 0) return def;
   char *test = (char*)0;
   int ret = strtol(value.c_str(), &test, 10);
   if (test == value.c_str()) return def;
//   int ret = lexical_cast<int>(value);
   return ret;
}

long
Property::getLongProperty(const string &name, long def, bool env)
{
   string value = getProperty(name, env);
   if (value.empty()) return def;
   char *test = (char*)0;
   long ret = atol(value.c_str());
   if (test == value.c_str()) return def;
//   long ret = lexical_cast<long>(value);
   return ret;
}

Timestamp
Property::getTimestampProperty(const string &name, Timestamp def, bool env)
{
   string value = getProperty(name, env);
   if (value.length() == 0) return def;
   char *test = (char*)0;
//   Timestamp ret = STRING_TO_TIMESTAMP(value.c_str());
   if (test == value.c_str()) return def;
   Timestamp ret = 0;
   try {
      lexical_cast<Timestamp>(value);
   }
   catch (...) {
      ret = 0;
   }
   return ret;
}

bool
Property::getBoolProperty(const string &name, bool def, bool env) 
{
   string value = getProperty(name, env);
   if (value.length() == 0) return def;
   if ((value=="1")||(value=="true")||(value=="TRUE")) return true;
   else {
      if ((value=="0")||(value=="false")||(value=="FALSE")) return false;
   }
   return def;
}


string 
Property::getStringProperty(const string &name, const string &def, 
                      bool env) 
{
   string value = getProperty(name, env);
   if (value.length() == 0) return def;
   return value;
}


bool 
Property::setProperty(const string &name, const string &value,
                      bool overwrite) 
{
   pair<const string, string> valuePair(name, value);
   MapType::iterator iter = properties_.find(name);
   if (iter != properties_.end()) {
      if (overwrite) (*iter).second = name;
      else return false;
   }
   else properties_.insert(valuePair);
   return true;                                                                                                                                         
}


/**
 * To allow templatized getting of properties. It returns true if the property has been found. In that
 * case, the return value is put into the 'value' argument.
 */
bool Property::getTypedProperty(const string& name, string& value, bool env)
{
   if (!propertyExists(name, env)) return false;
   value = getStringProperty(name, "", env);
   return true;
}

bool Property::getTypedProperty(const string& name, int& value, bool env)
{
   if (!propertyExists(name, env)) return false;
   value = getIntProperty(name, 0, env);
   return true;
}

bool Property::getTypedProperty(const string& name, long& value, bool env)
{
   if (!propertyExists(name, env)) return false;
   value = getLongProperty(name, 0, env);
   return true;
}

bool Property::getTypedProperty(const string& name, bool& value, bool env)
{
   if (!propertyExists(name, env)) return false;
   value = getBoolProperty(name, false, env);
   return true;
}

bool Property::getTypedProperty(const string& name, Timestamp& value, bool env)
{
   if (!propertyExists(name, env)) return false;
   value = getTimestampProperty(name, 0, env);
   return true;
}
  
#endif 
