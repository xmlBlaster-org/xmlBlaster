#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/util
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/util
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = \
			$(CLASS_DIR)/Log.class \
			$(CLASS_DIR)/FileUtil.class \
			$(CLASS_DIR)/XmlKeyBase.class \
			$(CLASS_DIR)/XmlQoSBase.class \
			$(CLASS_DIR)/StopWatch.class \
			$(CLASS_DIR)/XmlProcessor.class \
			$(CLASS_DIR)/XmlToDom.class \
			$(CLASS_DIR)/Args.class \
			$(CLASS_DIR)/TimeHelper.class
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
