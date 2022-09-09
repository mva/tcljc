JAVA_BIN=$(HOME)/local/jdk-classfile/bin/
JAVA=$(JAVA_BIN)java
JAVAC=$(JAVA_BIN)javac
JAVAP=$(JAVA_BIN)javap

# Note: currently need the impl package for BlockCodeBuilderImpl.isEmpty()
JAVA_OPTS=--enable-preview \
  --add-exports java.base/jdk.classfile=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.constantpool=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.instruction=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.attribute=ALL-UNNAMED
#JAVA_OPTS += -XX:+UseZGC -Xlog:gc
#JAVA_OPTS += -Djdk.tracePinnedThreads
TCLJ_MDIR=../jvm-stuff/bootstrap-tclj
#TCLJ_MDIR=../jvm-stuff/tclj-in-tclj/target/distrib/mods
#DET=--deterministic
TCLJ=$(JAVA) --module-path $(TCLJ_MDIR) $(JAVA_OPTS) -m tinyclj.compiler $(DET)

BOOTSTRAP_TCLJ=$(JAVA) --class-path $(TCLJ_MDIR)/tinyclj.rt:$(TCLJ_MDIR)/tinyclj.core:$(TCLJ_MDIR)/tinyclj.compiler $(JAVA_OPTS) tinyclj.build.main.__ns $(DET) --parent-loader :platform
BOOTSTRAP_RUN=$(JAVA) --class-path $(TCLJ_MDIR)/tinyclj.rt:$(DEST_DIR):resources

SOURCE_OPTS=-s src/tinyclj.compiler -s test/tinyclj.compiler
RUN_TESTS_NS=tcljc.run-tests

# $(DEST_DIR) matches the compiler's default destination directory
PROJECT_DIR=$(notdir $(PWD))
DEST_DIR=/tmp/$(USER)/tinyclj/$(PROJECT_DIR)

compile:
	$(TCLJ) $(SOURCE_OPTS) $(RUN_TESTS_NS)
watch-and-compile:
	$(TCLJ) --watch $(SOURCE_OPTS) $(RUN_TESTS_NS)

# Call with "make test TEST=<scope>" (with <scope> being "ns-name" or
# "ns-name/var-name") to only run tests from the given namespace or
# var.  Only call this after compile, possibly while one of the
# watch-and-xxx targets is running.
test:
	$(JAVA) --module-path $(TCLJ_MDIR) $(JAVA_OPTS) --add-modules tinyclj.core -cp $(DEST_DIR):resources $(RUN_TESTS_NS).__ns
watch-and-test:
	$(TCLJ) --watch $(SOURCE_OPTS) $(RUN_TESTS_NS)/run

# Compilation of tinyclj.core needs the bootstrap setup: place modules
# in classpath so that they do not interfere with the compilation of
# this special namespace.
test-tclj-in-tclj:
	$(BOOTSTRAP_TCLJ) -s $(TCLJ_MDIR)/tinyclj.rt -d $(DEST_DIR).compile-tclj-in-tclj -s $(TCLJ_MDIR)/tinyclj.core $(SOURCE_OPTS) tcljc.compile-tclj-in-tclj/run
watch-and-test-tclj-in-tclj:
	$(BOOTSTRAP_TCLJ) --watch -s $(TCLJ_MDIR)/tinyclj.rt -d $(DEST_DIR).compile-tclj-in-tclj -s $(TCLJ_MDIR)/tinyclj.core $(SOURCE_OPTS) tcljc.compile-tclj-in-tclj/run

clean:
	rm -rf "$(DEST_DIR)"/* "$(DEST_DIR)".* *.class hs_err_pid*.log replay_pid*.log

print-line-count:
	find src/tinyclj.compiler/tcljc -name "*.cljt" | xargs wc -l | sort -n

.PHONY: compile watch-and-compile test watch-and-test clean


print-javap:
	rm -f *.class
	$(JAVAC) $(JAVA_OPTS) -source 20 Hello.java
	$(JAVAP) -v -p *.class

threadlog:
	$(TCLJ) -d :none -s test/tinyclj.compiler tcljc.threadlog/-main

########################################################################

TIME_JAVA=time -p $(JAVA)

TCLJC_MAIN_NS=tcljc.main

BOOT_TCLJ_MDIR=$(TCLJ_MDIR)
BOOT_MOD_RT=$(BOOT_TCLJ_MDIR)/tinyclj.rt
BOOT_MOD_CORE=$(BOOT_TCLJ_MDIR)/tinyclj.core

bootstrap-fixpoint: $(DEST_DIR).stageDI2/DONE
bootstrap-check: $(DEST_DIR).rtiowFS/DONE

# Naming:
# Dx -- deterministic classfile output but slow compilation
# Fx -- fast compilation but non-deterministic classfile output
# xI -- isolated runtime, i.e. with "--parent-loader :platform"
# xS -- shared runtime, i.e. with default "--parent-loader :system"

# Zero: Build "tcljc" using the bootstrap compiler "tclj-in-tclj".
# Because there is no name collision between the compilers' namespaces
# currently, "tcljc" can be compiled like a regular application using
# the shared runtime setup.  Once tcljc becomes its own bootstrap
# compiler, this must be changed to the isolated runtime setup.
$(DEST_DIR).stageZero/DONE: $(wildcard $(BOOT_TCLJ_MDIR)/commit-id.txt src/tinyclj.compiler/tcljc/*.cljt src/tinyclj.compiler/tcljc/*/*.cljt)
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) --module-path $(BOOT_TCLJ_MDIR) $(JAVA_OPTS) -m tinyclj.compiler -d "$(dir $@)" -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"

# DI1: Build "tcljc" using the initial compiler from PREV_DEST_DIR
# (aka the first prerequisite's $< directory).
$(DEST_DIR).stageDI1/DONE: $(DEST_DIR).stageZero/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) -cp $(BOOT_MOD_RT):$(BOOT_MOD_CORE):$(dir $<) $(JAVA_OPTS) $(TCLJC_MAIN_NS).__ns --deterministic --parent-loader :platform -d "$(dir $@)" -s $(BOOT_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"

# DI2: Build "tcljc" using the DI1 compiler from PREV_DEST_DIR (aka
# the first prerequisite's $< directory).
$(DEST_DIR).stageDI2/DONE: $(DEST_DIR).stageDI1/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) -cp $(BOOT_MOD_RT):$(dir $<) $(JAVA_OPTS) $(TCLJC_MAIN_NS).__ns --deterministic --parent-loader :platform -d "$(dir $@)" -s $(BOOT_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"
	diff -Nrq "$(dir $<)" "$(dir $@)"

# ------------------------------------------------------------------------
# Use bootstrapped compiler to build modules for runtime, core
# library, and compiler.  Collect the jar files into $(DEST_DIR).mdir.

BUILD_JAVAC=$(JAVAC) --release 17
BUILD_JAVA=$(JAVA)
BUILD_JAR=$(JAVA_BIN)jar

TINYCLJ_RT_SOURCE := $(sort $(wildcard src/tinyclj.rt/*/lang/*.java)) src/tinyclj.rt/module-info.java
$(DEST_DIR).mod-tinyclj-rt/module-info.class: $(TINYCLJ_RT_SOURCE) | $(DEST_DIR).stageDI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	mkdir -p --mode 700 "$(dir $@)"
	$(BUILD_JAVAC) -d "$(dir $@)" $^

TINYCLJ_CORE_SOURCE := $(sort $(wildcard src/tinyclj.core/*/*.cljt src/tinyclj.core/*/*/*.cljt))
$(DEST_DIR).mod-tinyclj-core/module-info.class: $(DEST_DIR).mod-tinyclj-rt/module-info.class $(TINYCLJ_RT_SOURCE)
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BUILD_JAVA) -cp $(BOOT_MOD_RT):$(DEST_DIR).stageDI2 $(JAVA_OPTS) $(TCLJC_MAIN_NS).__ns -d "$(dir $@)" --parent-loader :platform -s $(dir $<) -s src/tinyclj.core tinyclj.core.all
	$(BUILD_JAVAC) -p $(dir $<) -d "$(dir $@)" src/tinyclj.core/module-info.java

TINYCLJ_COMPILER_SOURCE := $(sort $(wildcard src/tinyclj.compiler/*/*.cljt src/tinyclj.compiler/*/*/*.cljt))
$(DEST_DIR).mod-tinyclj-compiler/module-info.class: $(DEST_DIR).mod-tinyclj-core/module-info.class $(TINYCLJ_COMPILER_SOURCE)
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BUILD_JAVA) -cp $(BOOT_MOD_RT):$(DEST_DIR).stageDI2 $(JAVA_OPTS) $(TCLJC_MAIN_NS).__ns -d "$(dir $@)" --parent-loader :platform -s $(DEST_DIR).mod-tinyclj-rt -s $(dir $<) -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	$(BUILD_JAVAC) -p $(DEST_DIR).mod-tinyclj-rt:$(dir $<) -d "$(dir $@)" src/tinyclj.compiler/module-info.java

MOD_DIRS := 
$(DEST_DIR).mdir/DONE: $(DEST_DIR).mod-tinyclj-rt/module-info.class $(DEST_DIR).mod-tinyclj-core/module-info.class  $(DEST_DIR).mod-tinyclj-compiler/module-info.class
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	mkdir -p --mode 700 "$(dir $@)"
	$(BUILD_JAR) --create --file="$(dir $@)"/tinyclj-rt.jar -C $(DEST_DIR).mod-tinyclj-rt .
	$(BUILD_JAR) --create --file="$(dir $@)"/tinyclj-core.jar -C $(DEST_DIR).mod-tinyclj-core .
	$(BUILD_JAR) --create --file="$(dir $@)"/tinyclj-compiler.jar --main-class=$(TCLJC_MAIN_NS).__ns -C $(DEST_DIR).mod-tinyclj-compiler/ .
	touch "$@"

# ------------------------------------------------------------------------
# Use bootstrap compiler again, with module jars on classpath.
# Finally, compile and run an application using the jars as modules,
# i.e. from the module path.

DISTRIB_MOD_RT=$(DEST_DIR).mdir/tinyclj-rt.jar
BOOTSTRAP_DISTRIB_TCLJ=$(TIME_JAVA) $(JAVA_OPTS) -cp $(DEST_DIR).mdir/\* $(TCLJC_MAIN_NS).__ns

$(DEST_DIR).stageFI1/DONE: $(DEST_DIR).mdir/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOTSTRAP_DISTRIB_TCLJ) -d "$(dir $@)" --parent-loader :platform -s $(DISTRIB_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"

$(DEST_DIR).stageFI2/DONE: $(DEST_DIR).stageFI1/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) -cp $(DISTRIB_MOD_RT):$(dir $<) $(JAVA_OPTS) $(TCLJC_MAIN_NS).__ns -d "$(dir $@)" --parent-loader :platform -s $(DISTRIB_MOD_RT) -s src/tinyclj.core -s src/tinyclj.compiler $(TCLJC_MAIN_NS)
	touch "$@"

$(DEST_DIR).rtiowFS/DONE: $(DEST_DIR).stageFI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(TIME_JAVA) --enable-preview --add-exports java.base/jdk.classfile=tcljc --add-exports java.base/jdk.classfile.constantpool=tcljc --add-exports java.base/jdk.classfile.instruction=tcljc --add-exports java.base/jdk.classfile.attribute=tcljc -p $(DEST_DIR).mdir -m tcljc -d "$(dir $@)" -s test/tinyclj.compiler tcljc.rtiow-ref
	@echo "\nRun from class path:"
	$(JAVA) -cp $(DEST_DIR).mdir/\*:$(dir $@) tcljc.rtiow-ref.__ns >"$(dir $@)"ray.ppm
	@echo "3cf6c9b9f93edb0de2bc24015c610d78  $(dir $@)ray.ppm" | md5sum -c -
	@echo "\nRun from module path:"
	$(JAVA) -p $(DEST_DIR).mdir --add-modules tinyclj.core -cp $(dir $@) tcljc.rtiow-ref.__ns >"$(dir $@)"ray.ppm
	@echo "3cf6c9b9f93edb0de2bc24015c610d78  $(dir $@)ray.ppm" | md5sum -c -
	touch "$@"
