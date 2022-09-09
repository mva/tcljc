JAVA=$(HOME)/local/jdk-classfile/bin/java
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
MAIN_NS=tcljc.core
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


JAVAC=$(HOME)/local/jdk-classfile/bin/javac
JAVAP=$(HOME)/local/jdk-classfile/bin/javap
print-javap:
	rm -f *.class
	$(JAVAC) $(JAVA_OPTS) -source 20 Hello.java
	$(JAVAP) -v -p *.class

threadlog:
	$(TCLJ) -d :none -s test/tinyclj.compiler tcljc.threadlog/-main

########################################################################

BUILD_MAIN=tcljc.main
BOOT_JAVA=time -p $(JAVA)
BOOT_TCLJ_MDIR=$(TCLJ_MDIR)
BOOT_TCLJ_SRC=../jvm-stuff/tclj-in-tclj/src

BOOT_MOD_RT=$(BOOT_TCLJ_MDIR)/tinyclj.rt
BOOT_MOD_CORE=$(BOOT_TCLJ_MDIR)/tinyclj.core

BOOT_SRC_CORE=$(BOOT_TCLJ_SRC)/tinyclj.core

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
	$(BOOT_JAVA) --module-path $(BOOT_TCLJ_MDIR) $(JAVA_OPTS) -m tinyclj.compiler -d "$(dir $@)" -s src/tinyclj.compiler $(BUILD_MAIN)
	touch "$@"

# DI1: Build "tcljc" using the initial compiler from PREV_DEST_DIR
# (aka the first prerequisite's $< directory).
$(DEST_DIR).stageDI1/DONE: $(DEST_DIR).stageZero/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOT_JAVA) -cp $(BOOT_MOD_RT):$(BOOT_MOD_CORE):$(dir $<) $(JAVA_OPTS) $(BUILD_MAIN).__ns --deterministic --parent-loader :platform -d "$(dir $@)" -s $(BOOT_MOD_RT) -s $(BOOT_SRC_CORE) -s src/tinyclj.compiler $(BUILD_MAIN)
	touch "$@"

# DI2: Build "tcljc" using the DI1 compiler from PREV_DEST_DIR (aka
# the first prerequisite's $< directory).
$(DEST_DIR).stageDI2/DONE: $(DEST_DIR).stageDI1/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOT_JAVA) -cp $(BOOT_MOD_RT):$(dir $<) $(JAVA_OPTS) $(BUILD_MAIN).__ns --deterministic --parent-loader :platform -d "$(dir $@)" -s $(BOOT_MOD_RT) -s $(BOOT_SRC_CORE) -s src/tinyclj.compiler $(BUILD_MAIN)
	touch "$@"
	diff -Nrq "$(dir $<)" "$(dir $@)"

#----

$(DEST_DIR).stageFI1/DONE: $(DEST_DIR).stageDI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOT_JAVA) -cp $(BOOT_MOD_RT):$(dir $<) $(JAVA_OPTS) $(BUILD_MAIN).__ns -d "$(dir $@)" --parent-loader :platform -s $(BOOT_MOD_RT) -s $(BOOT_SRC_CORE) -s src/tinyclj.compiler $(BUILD_MAIN)
	touch "$@"

$(DEST_DIR).stageFI2/DONE: $(DEST_DIR).stageFI1/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOT_JAVA) -cp $(BOOT_MOD_RT):$(dir $<) $(JAVA_OPTS) $(BUILD_MAIN).__ns -d "$(dir $@)" --parent-loader :platform -s $(BOOT_MOD_RT) -s $(BOOT_SRC_CORE) -s src/tinyclj.compiler $(BUILD_MAIN)
	touch "$@"

$(DEST_DIR).rtiowFS/DONE: $(DEST_DIR).stageFI2/DONE
	@echo; echo "### $(dir $@)"
	@rm -rf "$(dir $@)"
	$(BOOT_JAVA) -cp $(BOOT_MOD_RT):$(dir $<) $(JAVA_OPTS) $(BUILD_MAIN).__ns -d "$(dir $@)" -s $(BOOT_SRC_CORE) -s test/tinyclj.compiler tcljc.rtiow-ref
	$(JAVA) -cp $(BOOT_MOD_RT):$(dir $<):$(dir $@) $(JAVA_OPTS) tcljc.rtiow-ref.__ns >"$(dir $@)"ray.ppm
	@echo "3cf6c9b9f93edb0de2bc24015c610d78  $(dir $@)ray.ppm" | md5sum -c -
	touch "$@"
