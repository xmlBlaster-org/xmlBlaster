/*----------------------------------------------------------------------------
Name:      Property.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Class used to read, store & write (java) properties.
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PROPERTY_H
#define _UTIL_PROPERTY_H

# include <iostream>
#include <fstream>
#include <map>
# include <string>

#include <stdlib.h>
#include <util/PropertyDef.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {

   /**
    * The class Property does handle properties in the java-way. It does not
    * throw any exception. Errors in file-reading, writing etc. are handled
    * by returning a special value (which for integers is zero and for bool
    * is false and empty strings for strings.
    *
    * When reading or writing, comments and empty lines are ignored.
    * When writing, properties are written in alphabetical order (of the 
    * property name).
    */
   class Property {
      
      typedef map<string, string, less<string> > MapType;
  
   private:
      MapType properties_;
      
   protected:
      /**
       * returns true if the line is a comment, or if it is empty. Returns
       * false if the line is a possible property line. "Possible" in the sense
       * that its validity is not checked yet.
       */
      bool isComment(const string &line) const {
         if (line.length() == 0) return false;
         return (line.c_str()[0] == '#');
      }
      
      /**
       * Filters (throws away) all whitespaces from the specified string.
       */
      string filter(const string &line) const {
         string ret;
         for (string::size_type i=0; i<line.length(); i++) {
            if (line.c_str()[i] != ' ') ret += line.c_str()[i];
         }
         return ret;
      }

      
      /**
       * gets the property in the line specified in the argument list. It 
       * returns this property as a pair name,value. If the line does not
       * contain a valid property(for example if the = sign is not present)
       * then a pair of empty strings is returned.
       */
      pair<const string, string> getPair(const string &line) const {
         string::size_type pos = line.find("=");
         if ((pos < 2) || (pos >= line.length()) || (isComment(line)) ) {
            return pair<const string, string>("","");
         }
         string name, value;
         name.assign(line, 0, pos);
         value = line.substr(pos+1);
         return pair<const string, string>(filter(name), filter(value));
      }
      
      
   public:
      
      /**
       * The default constructor allocate the storage
       * map for the properties and parses the command line properties.<p />
       * NOTE: You have to call loadPropertyFile() separatly
       */
      Property(int args=0, const char * const argc[]=0) : properties_() {

         if (args && argc) {
            loadCommandLineProps(args, argc, "-", false); // xmlBlaster-style properties
         }

         //loadPropertyFile();
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
      void loadPropertyFile()
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
            path = getenv("HOME");
            if (!path.empty())
               num = readPropertyFile(path + FILE_SEP + filename, false);
         }
         if (num < 1) {
            path = getenv("XMLBLASTER_HOME");
            if (!path.empty())
               num = readPropertyFile(path + FILE_SEP + filename, false);
         }
      }

      ~Property() {
         properties_.erase(properties_.begin(), properties_.end());
      }

      /**
       * Reads the file specified in filename. If the name is not valid, or if
       * the system can not write to the specified file, then -1 is returned.
       * If you specify overwrite=true (the default) then the properties read
       * from the file are inserted into the properties even if a property 
       * with the same name has been defined earlier. 
       */
      int readPropertyFile(const string &filename, bool overwrite=true) {
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
         if (count > 0)
            std::cout << "Successfully read " << count << " entries from " << filename << std::endl;
         return count;
      }
      

      /**
       * writes the properties to a file specified in the argument list. If it
       * could not write to the file, a zero is returned.
       * Returns the number of properties written to the file.
       */
      int writePropertyFile(const char *filename) const {
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
       * Gets the propety with the specified name. If no such property exists,
       * an empty string is returned. If the string is not found, it searches
       * in among the environment variables (only if env is set to true which
       * is the default). In the property is not found there either, it returns
       * an empty string.
       */
      string getProperty(const string &name, bool env=true) {
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


      bool propertyExists(const string &name, bool env=true) {
         MapType::const_iterator iter = properties_.find(name);
         if (iter == properties_.end()) {
            if (!env) return false;
            char* envStr = getenv(name.c_str());
            if (envStr == 0) return false;
            setProperty(name, envStr);
         }
         return true;
      }


      int getIntProperty(const string &name, int def, bool env=true) {
         string value = getProperty(name, env);
         if (value.length() == 0) return def;
         char *test = (char*)0;
         int ret = strtol(value.c_str(), &test, 10);
         if (test == value.c_str()) return def;
         return ret;
      }


      bool getBoolProperty(const string &name, bool def, bool env=true) {
         string value = getProperty(name, env);
         if (value.length() == 0) return def;
         if ((value=="1")||(value=="true")||(value=="TRUE")) return true;
         else {
            if ((value=="0")||(value=="false")||(value=="FALSE")) return false;
         }
         return def;
      }


      string getStringProperty(const string &name, const string &def, 
                            bool env=true) {
         string value = getProperty(name, env);
         if (value.length() == 0) return def;
         return value;
      }


      bool setProperty(const string &name, const string &value,
                       bool overwrite=true) {
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
       * Loads the properties read from the command line (or another array).
       * The syntax for passing properties is the same as in java if the 
       * switch javaStyle is true (default). That is "-Dprop1=val1" is 
       * then equivalent as prop1=val1 in a property file. If the switch 
       * javaStyle is false, then the Corba style is choosen, i.e. the 
       * following is correct syntax: "-ORBNameService whatever" (so no
       * equality sign between name and value).
       * Errors in syntax are silenty ignored (the property just isn't load).
       */
      int loadCommandLineProps(int args, const char * const argc[], const string &sep="-D", 
                    bool javaStyle=true) {

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
         if (count > 0)
            std::cout << "Successfully read " << (count-1)/2 << " command line arguments" << std::endl;
         return ret;
      }
      
      /**
       * It searches in the argument list specified by argc the argument 
       * specified by name. If nothing is found it returns -1, otherwise it
       * returns the index of argc corresponding to what specified in name.
       */
      int findArgument(int args, const char * const argc[], const string &name) {
         for (int i=1; i < args; i++) {
            if (string(argc[i]) == name) return i;
         }
         return -1;
      }
      

   }; // class Property

}}} // namespace

#endif // _UTIL_PROPERTY_H


      
      
