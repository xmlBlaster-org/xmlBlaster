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

#------------------------------------------------------------------------------
SUB_DIRS = chat
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = \
			$(CLASS_DIR)/Util.class \
			$(CLASS_DIR)/BlasterCallbackImpl.class \
			$(CLASS_DIR)/ClientOid.class \
			$(CLASS_DIR)/ClientGet.class \
			$(CLASS_DIR)/ClientXml.class \
			$(CLASS_DIR)/ClientSub.class \
			$(CLASS_DIR)/ClientQuery.class \
			$(CLASS_DIR)/ClientRaw.class \
			$(CLASS_DIR)/ClientErase.class \
			$(CLASS_DIR)/ClientPubDestination.class 
#------------------------------------------------------------------------------


JFILES   := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))

$(CLASS_DIR)/%.class: %.java
	$(JAVAC) -d $(CLASS_TOP_DIR) $<


all: $(JFILES)

clean:
	rm $(CLASS_DIR)/*.class 
