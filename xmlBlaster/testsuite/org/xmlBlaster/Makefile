#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/testsuite/org/xmlBlaster
SRC_JAVA_DIR = $(SRC_TESTSUITE_TOP_DIR)/org/xmlBlaster
#------------------------------------------------------------------------------


LOCAL_FILES   := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))


$(CLASS_DIR)/%.class: %.java
	$(JAVAC) -d $(CLASS_TOP_DIR) $<


all: $(LOCAL_FILES)

clean:
	rm $(CLASS_DIR)/*.class 
