#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for sub directories
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

SUB_DIRS = src testsuite

all: all_SUB_DIRS

clean: clean_SUB_DIRS

distclean: clean
	$(RM_RECURSIVE) $(JAVADOC_DIR)
	$(RM) $(JAR_DIR)/xmlBlaster.jar

javadoc: all
	$(MKDIR_RECURSIVE) $(JAVADOC_DIR)
	$(JAVADOC) -d $(JAVADOC_DIR) `find $(XMLBLASTER_HOME)/src/java -name "[A-Za-z]*.java" -print`

jar: all
	( cd $(CLASS_TOP_DIR); jar cvf $(JAR_DIR)/xmlBlaster.jar ./org )


