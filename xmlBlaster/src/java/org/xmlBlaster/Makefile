#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/make/make.h
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = \
			$(CLASS_DIR)/Main.class \
			$(CLASS_DIR)/Server.class \
			$(CLASS_DIR)/ServerImpl.class \
			$(CLASS_DIR)/BlasterCallback.class \
			$(CLASS_DIR)/BlasterCallbackImpl.class \
			$(CLASS_DIR)/Client.class \
			$(CLASS_DIR)/ClientInfo.class \
			$(CLASS_DIR)/RequestBroker.class \
			$(CLASS_DIR)/XmlKey.class \
			$(CLASS_DIR)/XmlQoS.class
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
	$(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all: 	precond java

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean:
	@echo "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*

doc:
	$(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
