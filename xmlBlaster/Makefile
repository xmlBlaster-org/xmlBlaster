#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for sub directories
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

SUB_DIRS = src testsuite

all: all_SUB_DIRS

clean: clean_SUB_DIRS

javadoc:
	$(MKDIR_RECURSIVE) $(JAVADOC_DIR)
	$(JAVADOC) -d $(JAVADOC_DIR) `find $(XMLBLASTER_HOME)/src/java -name "[A-Za-z]*.java" -print`

