# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

workspace(name = "vaticle_typedb_console")

################################
# Load @typedb_dependencies #
################################

load("//dependencies/typedb:repositories.bzl", "typedb_dependencies")
typedb_dependencies()

# Load //builder/bazel for RBE
load("@typedb_dependencies//builder/bazel:deps.bzl", "bazel_toolchain")
bazel_toolchain()

# Load //builder/java
load("@typedb_dependencies//builder/java:deps.bzl", "rules_jvm_external")
rules_jvm_external()

# Load //builder/kotlin
load("@typedb_dependencies//builder/kotlin:deps.bzl", "io_bazel_rules_kotlin")
io_bazel_rules_kotlin()
load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")
kotlin_repositories()
load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")
kt_register_toolchains()

# Load //builder/python
load("@typedb_dependencies//builder/python:deps.bzl", "rules_python")
rules_python()

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
rules_jvm_external_deps()

# Load //builder/antlr
load("@typedb_dependencies//builder/antlr:deps.bzl", "rules_antlr", "antlr_version")
rules_antlr()

load("@rules_antlr//antlr:lang.bzl", "JAVA")
load("@rules_antlr//antlr:repositories.bzl", "rules_antlr_dependencies")
rules_antlr_dependencies(antlr_version, JAVA)

# Load //builder/proto_grpc
load("@typedb_dependencies//builder/proto_grpc:deps.bzl", proto_grpc_deps = "deps")
proto_grpc_deps()

load("@rules_proto_grpc//:repositories.bzl", "rules_proto_grpc_repos", "rules_proto_grpc_toolchains")
rules_proto_grpc_toolchains()
rules_proto_grpc_repos()

load("@rules_proto_grpc//java:repositories.bzl", rules_proto_grpc_java_repos = "java_repos")
rules_proto_grpc_java_repos()

load("@io_grpc_grpc_java//:repositories.bzl", "IO_GRPC_GRPC_JAVA_ARTIFACTS")
load("@typedb_dependencies//library/maven:rules.bzl", "parse_unversioned")
io_grpc_artifacts = [parse_unversioned(c) for c in IO_GRPC_GRPC_JAVA_ARTIFACTS]

# Load //distribution/docker
load("@typedb_dependencies//distribution/docker:deps.bzl", docker_deps = "deps")
docker_deps()

# Load //builder/rust
load("@typedb_dependencies//builder/rust:deps.bzl", rust_deps = "deps")
rust_deps()

load("@rules_rust//rust:repositories.bzl", "rules_rust_dependencies", "rust_register_toolchains", "rust_analyzer_toolchain_repository")
load("@rules_rust//tools/rust_analyzer:deps.bzl", "rust_analyzer_dependencies")
rules_rust_dependencies()
load("@rules_rust//rust:defs.bzl", "rust_common")
rust_register_toolchains(
    edition = "2021",
    extra_target_triples = [
        "aarch64-apple-darwin",
        "aarch64-unknown-linux-gnu",
        "x86_64-apple-darwin",
        "x86_64-pc-windows-msvc",
        "x86_64-unknown-linux-gnu",
    ],
    rust_analyzer_version = rust_common.default_version,
)

load("@typedb_dependencies//library/crates:crates.bzl", "fetch_crates")
fetch_crates()
load("@crates//:defs.bzl", "crate_repositories")
crate_repositories()

load("@typedb_dependencies//tool/swig:deps.bzl", "swig")
swig()

# Load //tool/common
load("@typedb_dependencies//tool/common:deps.bzl", "typedb_dependencies_ci_pip",
    typedb_dependencies_tool_maven_artifacts = "maven_artifacts")
typedb_dependencies_ci_pip()
load("@typedb_dependencies_ci_pip//:requirements.bzl", "install_deps")
install_deps()

# Load //tool/checkstyle
load("@typedb_dependencies//tool/checkstyle:deps.bzl", checkstyle_deps = "deps")
checkstyle_deps()

# Load //tool/unuseddeps
load("@typedb_dependencies//tool/unuseddeps:deps.bzl", unuseddeps_deps = "deps")
unuseddeps_deps()

# Load //tool/sonarcloud
load("@typedb_dependencies//tool/sonarcloud:deps.bzl", "sonarcloud_dependencies")
sonarcloud_dependencies()

######################################
# Load @typedb_bazel_distribution #
######################################

load("@typedb_dependencies//distribution:deps.bzl", "typedb_bazel_distribution")
typedb_bazel_distribution()

# Load //common
load("@typedb_bazel_distribution//common:deps.bzl", "rules_pkg")
rules_pkg()
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
rules_pkg_dependencies()

# Load //github
load("@typedb_bazel_distribution//github:deps.bzl", "ghr_linux_tar", "ghr_osx_zip")
ghr_linux_tar()
ghr_osx_zip()

# Load //pip
load("@typedb_bazel_distribution//pip:deps.bzl", "typedb_bazel_distribution_pip")
typedb_bazel_distribution_pip()

# Load @typedb_bazel_distribution_uploader
load("@typedb_bazel_distribution//common/uploader:deps.bzl", "typedb_bazel_distribution_uploader")
typedb_bazel_distribution_uploader()
load("@typedb_bazel_distribution_uploader//:requirements.bzl", uploader_install_deps = "install_deps")
uploader_install_deps()

# Load //docs
load("@typedb_bazel_distribution//docs:java/deps.bzl", "google_bazel_common")
google_bazel_common()
load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")
google_common_workspace_rules()

################################
# Load @vaticle dependencies #
################################

# Load repositories
load("//dependencies/typedb:repositories.bzl", "typedb_driver")
typedb_driver()

load("@typedb_driver//dependencies/typedb:repositories.bzl", "typedb_protocol")
typedb_protocol()

# Load artifacts
load("@typedb_driver//dependencies/typedb:artifacts.bzl", "typedb_artifact")
typedb_artifact()

# Load maven
load("@typedb_driver//dependencies/maven:artifacts.bzl", typedb_driver_artifacts = "artifacts")
#load("@typedb_driver//dependencies/typedb:artifacts.bzl", vaticle_typedb_vaticle_maven_artifacts = "maven_artifacts")
load("//dependencies/maven:artifacts.bzl", typedb_console_artifacts = "artifacts")

###############
# Load @maven #
###############

load("@typedb_dependencies//library/maven:rules.bzl", "maven")
maven(
    typedb_driver_artifacts +
    typedb_console_artifacts +
    typedb_dependencies_tool_maven_artifacts +
    io_grpc_artifacts,
    generate_compat_repositories = True,
#    internal_artifacts = vaticle_typedb_vaticle_maven_artifacts,
)

load("@maven//:compat.bzl", "compat_repositories")
compat_repositories()

############################################
# Create @vaticle_typedb_console_workspace_refs #
############################################
load("@typedb_bazel_distribution//common:rules.bzl", "workspace_refs")
workspace_refs(
    name = "vaticle_typedb_console_workspace_refs"
)
