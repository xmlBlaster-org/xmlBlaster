/*----------------------------------------------------------------------------
Name:      Property.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Class used to read, store & write (java) properties.
-----------------------------------------------------------------------------*/
#ifndef _UTIL_PROPERTY_H
#define _UTIL_PROPERTY_H

#include <util/xmlBlasterDef.h>
#include <map>
#include <string>

namespace org { namespace xmlBlaster {
namespace util {

   /**
    * The class Property does handle properties in the java-way. It does not
    * throw any exception. Errors in file-reading, writing etc. are handled
    * by returning a special value (which for integers is zero and for bool
    * is false and empty std::strings for std::strings.
    * <br />
    * When reading or writing, comments and empty lines are ignored.
    * When writing, properties are written in alphabetical order (of the 
    * property name).
    * <br />
    * In properties like <code>SomeValue-${xy}-somePostValue</code> the <code>${}</code>
    * are replaced by the value of <code>xy</code>.
    * <br />
    * Fills during construction the properties <code>user.home</code> and
    * <code>file.separator</code> and <code>path.separator</code> and others
    * as described at method <code>initializeDefaultProperties()</code>.
    * This simplifies the reuse of the xmlBlaster.properties
    * which uses those settings from the Java environment.
    */
   class Dll_Export Property {
      
      public: typedef std::map<std::string, std::string, std::less<std::string> > MapType;
  
   private:
      MapType properties_;
      
      /**
       * Replace all ${...} variables in value. 
       * @param key The property key
       * @param value the corresponding value
       * @param env If true the environment is scanned as well
       */
      std::string replaceVariable(const std::string &key, const std::string &value, bool env);

      /**
       * Set a property without replacing ${...} variables in value. 
       * @return false if an old entry existed which was not overwritten
       */
      bool setProperty_(const std::string &name, const std::string &value,
                       bool overwrite=true);

      /**
       * Initialize some default properties, similar to the java virtual machine. 
       * Add some predefined variables to be useful in xmlBlaster.properties as ${user.home} etc:
       * <pre>
       * user.home       For example "/home/marcel"
       * user.name       Your login name on the OS.
       * java.io.tmpdir  The temporary directory of your OS.
       * file.separator  On UNIX "/", on Windows "\"
       * path.separator  On UNIX ":", on Windows ";"
       * </pre>
       */
      void initializeDefaultProperties();

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
                 * @param args Length of argv
                 * @param argv The command line arguments, for example "-protocol SOCKET"
       */
      Property(int args=0, const char * const argv[]=0);

      /**
       * Initialize with the given key/value std::map. 
       * NOTE: You have to call loadPropertyFile() separately
       * @param propertyMap A std::map which contains key and values pairs,
       *                    for example key="protocol" and value="SOCKET"
       */
      Property(MapType propMap);

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
       * Replace all ${...} variables in value. 
       * @param env If true the environment is scanned as well
       */
      void replaceVariables(bool env);

      const MapType& getPropertyMap() const {
         return properties_;
      }

      /**
       * Reads the file specified in filename. If the name is not valid, or if
       * the system can not write to the specified file, then -1 is returned.
       * If you specify overwrite=true (the default) then the properties read
       * from the file are inserted into the properties even if a property 
       * with the same name has been defined earlier. 
       * <p />
       * Note: The ${...} tokens are not replaced in this method
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


      int get(const std::string &name, int def) { return getIntProperty(name, def, false); }
      int getIntProperty(const std::string &name, int def, bool env=true);

      long get(const std::string &name, long def) { return getLongProperty(name, def, false); }
      long getLongProperty(const std::string &name, long def, bool env=true);

      org::xmlBlaster::util::Timestamp getTimestampProperty(const std::string &name, org::xmlBlaster::util::Timestamp def, bool env=true);

      bool get(const std::string &name, bool def) { return getBoolProperty(name, def, false); }
      bool getBoolProperty(const std::string &name, bool def, bool env=true);


      std::string get(const std::string &name, const char *def) { std::string defS=def; return getStringProperty(name, defS, false); }
      std::string get(const std::string &name, const std::string &def) { return getStringProperty(name, def, false); }
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
#     if __LP64__
      // long === long long === 64 bit
#     else
      bool getTypedProperty(const std::string& name, org::xmlBlaster::util::Timestamp& value, bool env=true);
#     endif
        
       bool setProperty(const std::string &name, const std::string &value,
                       bool overwrite=true);
 
      /**
       * Loads the properties read from the command line (or another array).
       * The syntax for passing properties is the same as in java if the 
       * switch javaStyle is true (default). That is "-Dprop1=val1" is 
       * then equivalent as prop1=val1 in a property file. If the switch 
       * javaStyle is false, then the Corba style is chosen, i.e. the 
       * following is correct syntax: "-ORBNameService whatever" (so no
       * equality sign between name and value).
       * Errors in syntax are silently ignored (the property just isn't load).
       *
       * @param args The length of argv[]
       * @param argv The command line arguments, argv[0] is the executable name,
       *             for example { "HelloWorld2" "-trace" "true" }
       * @param sep The property praefix, for example "-" for "-trace true"
       */
      int loadCommandLineProps(int args, const char * const argv[], const std::string &sep="-D", 
                    bool javaStyle=true);
      

      /**
       * It searches in the argument list specified by argv the argument 
       * specified by name. If nothing is found it returns -1, otherwise it
       * returns the index of argv corresponding to what specified in name.
       */
      int findArgument(int args, const char * const argv[], const std::string &name) {
         for (int i=1; i < args; i++) {
            if (std::string(argv[i]) == name) return i;
         }
         return -1;
      }
      
      std::string toXml(const std::string& extraOffset="");

   }; // class Property

}}} // namespace

#endif // _UTIL_PROPERTY_H


      
      
