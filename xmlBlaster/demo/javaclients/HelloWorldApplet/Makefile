#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/javaclients/HelloWorldApplet
SRC_JAVA_DIR = $(XMLBLASTER_HOME)/demo/javaclients/HelloWorldApplet
#------------------------------------------------------------------------------


LOCAL_FILES   := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))

$(CLASS_DIR)/%.class: %.java
	$(JAVAC) -d $(CLASS_TOP_DIR) $<


all: $(LOCAL_FILES)

clean:
	rm $(CLASS_DIR)/*.class 
