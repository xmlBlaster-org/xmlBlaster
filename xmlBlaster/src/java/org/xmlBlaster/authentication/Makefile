#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/authentication
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/authentication
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = \
			$(CLASS_DIR)/Authenticate.class \
			$(CLASS_DIR)/AuthenticationInfo.class \
			$(CLASS_DIR)/HttpIORServer.class \
			$(CLASS_DIR)/ClientQoS.class
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

#doc:
#	$(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
