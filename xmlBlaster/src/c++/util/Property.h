/*----------------------------------------------------------------------------
Name:      Property.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Class used to read, store & write (java) properties.
-----------------------------------------------------------------------------*/
#ifndef _UTIL_PROPERTY_H
#define _UTIL_PROPERTY_H

#include <util/xmlBlasterDef.h>
#include <iostream>
#include <fstream>
#include <map>
#include <string>

#include <stdlib.h>
#include <util/PropertyDef.h>



namespace org { namespace xmlBlaster {
namespace util {

   /**
    * The class Property does handle properties in the java-way. It does not
    * throw any exception. Errors in file-reading, writing etc. are handled
    * by returning a special value (which for integers is zero and for bool
    * is false and empty std::strings for std::strings.
    *
    * When reading or writing, comments and empty lines are ignored.
    * When writing, properties are written in alphabetical order (of the 
    * property name).
    */
   class Dll_Export Property {
      
      typedef std::map<std::string, std::string, std::less<std::string> > MapType;
  
   private:
      MapType properties_;
      
   protected:
      /**
       * returns true if the line is a comment, or if it is empty. Returns
       * false if the line is a possible property line. "Possible" in the sense
       * that its validity is not checked yet.
       */
      bool isComment(const std::string &line) const {
         if (line.length() == 0) return false;
         return (line.c_str()[0] == '#');
      }
      
      /**
       * Filters (throws away) all whitespaces from the specified std::string.
       */
      std::string filter(const std::string &line) const {
         std::string ret;
         for (std::string::size_type i=0; i<line.length(); i++) {
            if (line.c_str()[i] != ' ') ret += line.c_str()[i];
         }
         return ret;
      }

      
      /**
       * gets the property in the line specified in the argument list. It 
       * returns this property as a pair name,value. If the line does not
       * contain a valid property(for example if the = sign is not present)
       * then a pair of empty std::strings is returned.
       */
      std::pair<const std::string, std::string> getPair(const std::string &line) const {
         std::string::size_type pos = line.find("=");
         if ((pos < 2) || (pos >= line.length()) || (isComment(line)) ) {
            return std::pair<const std::string, std::string>("","");
         }
         std::string name, value;
         name.assign(line, 0, pos);
         value = line.substr(pos+1);
         return std::pair<const std::string, std::string>(filter(name), filter(value));
      }
      
      
   public:
      
      /**
       * The default constructor allocate the storage
       * std::map for the properties and parses the command line properties.<p />
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
      void loadPropertyFile();
      

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
      int readPropertyFile(const std::string &filename, bool overwrite=true);
      

      /**
       * writes the properties to a file specified in the argument list. If it
       * could not write to the file, a zero is returned.
       * Returns the number of properties written to the file.
       */
      int writePropertyFile(const char *filename) const ;


      /**
       * Gets the propety with the specified name. If no such property exists,
       * an empty std::string is returned. If the std::string is not found, it searches
       * in among the environment variables (only if env is set to true which
       * is the default). In the property is not found there either, it returns
       * an empty std::string.
       */
      std::string getProperty(const std::string &name, bool env=true);


      bool propertyExists(const std::string &name, bool env=true);


      int getIntProperty(const std::string &name, int def, bool env=true);

      long getLongProperty(const std::string &name, long def, bool env=true);

      org::xmlBlaster::util::Timestamp getTimestampProperty(const std::string &name, org::xmlBlaster::util::Timestamp def, bool env=true);

      bool getBoolProperty(const std::string &name, bool def, bool env=true);


      std::string getStringProperty(const std::string &name, const std::string &def, 
                            bool env=true);

      /**
       * To allow templatized getting of properties. It returns true if the property has been found. In that
       * case, the return value is put into the 'value' argument.
       */
      bool getTypedProperty(const std::string& name, std::string& value, bool env=true);
      bool getTypedProperty(const std::string& name, int& value, bool env=true);
      bool getTypedProperty(const std::string& name, long& value, bool env=true);
      bool getTypedProperty(const std::string& name, bool& value, bool env=true);
      bool getTypedProperty(const std::string& name, org::xmlBlaster::util::Timestamp& value, bool env=true);
        
       bool setProperty(const std::string &name, const std::string &value,
                       bool overwrite=true);
 
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
      int loadCommandLineProps(int args, const char * const argc[], const std::string &sep="-D", 
                    bool javaStyle=true);
      

      /**
       * It searches in the argument list specified by argc the argument 
       * specified by name. If nothing is found it returns -1, otherwise it
       * returns the index of argc corresponding to what specified in name.
       */
      int findArgument(int args, const char * const argc[], const std::string &name) {
         for (int i=1; i < args; i++) {
            if (std::string(argc[i]) == name) return i;
         }
         return -1;
      }
      

   }; // class Property

}}} // namespace

#endif // _UTIL_PROPERTY_H


      
      
