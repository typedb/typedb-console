#
# Copyright (C) 2020 Grakn Labs
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

workspace(name = "graknlabs_console")

################################
# Load @graknlabs_dependencies #
################################
load("//dependencies/graknlabs:dependencies.bzl", "graknlabs_dependencies")
graknlabs_dependencies()

load("@graknlabs_dependencies//builder/antlr:deps.bzl", antlr_deps = "deps")
antlr_deps()
load("@rules_antlr//antlr:deps.bzl", "antlr_dependencies")
antlr_dependencies()

load("@graknlabs_dependencies//builder/bazel:deps.bzl","bazel_common", "bazel_deps", "bazel_toolchain")
bazel_common()
bazel_deps()
bazel_toolchain()

load("@graknlabs_dependencies//builder/grpc:deps.bzl", grpc_deps = "deps")
grpc_deps()
load("@com_github_grpc_grpc//bazel:grpc_deps.bzl",
com_github_grpc_grpc_deps = "grpc_deps")
com_github_grpc_grpc_deps()
load("@stackb_rules_proto//java:deps.bzl", "java_grpc_compile")
java_grpc_compile()
load("@stackb_rules_proto//node:deps.bzl", "node_grpc_compile")
node_grpc_compile()

load("@graknlabs_dependencies//builder/java:deps.bzl", java_deps = "deps")
java_deps()
load("@graknlabs_dependencies//library/maven:rules.bzl", "maven")

load("@graknlabs_dependencies//builder/nodejs:deps.bzl", nodejs_deps = "deps")
nodejs_deps()
load("@build_bazel_rules_nodejs//:defs.bzl", "node_repositories")
node_repositories()

load("@graknlabs_dependencies//builder/python:deps.bzl", python_deps = "deps")
python_deps()
load("@rules_python//python:pip.bzl", "pip_repositories", "pip3_import")
pip_repositories()
pip3_import(
    name = "graknlabs_dependencies_ci_pip",
    requirements = "@graknlabs_dependencies//tool:requirements.txt",
)
load("@graknlabs_dependencies_ci_pip//:requirements.bzl",
graknlabs_dependencies_ci_pip_install = "pip_install")
graknlabs_dependencies_ci_pip_install()

load("@graknlabs_dependencies//distribution:deps.bzl", distribution_deps = "deps")
distribution_deps()

pip3_import(
    name = "graknlabs_bazel_distribution_pip",
    requirements = "@graknlabs_bazel_distribution//pip:requirements.txt",
)
load("@graknlabs_bazel_distribution_pip//:requirements.bzl",
graknlabs_bazel_distribution_pip_install = "pip_install")
graknlabs_bazel_distribution_pip_install()

load("@graknlabs_bazel_distribution//github:dependencies.bzl", "tcnksm_ghr")
tcnksm_ghr()

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
git_repository(
    name = "io_bazel_skydoc",
    remote = "https://github.com/graknlabs/skydoc.git",
    branch = "experimental-skydoc-allow-dep-on-bazel-tools",
)

load("@io_bazel_skydoc//:setup.bzl", "skydoc_repositories")
skydoc_repositories()

load("@io_bazel_rules_sass//:package.bzl", "rules_sass_dependencies")
rules_sass_dependencies()

load("@build_bazel_rules_nodejs//:defs.bzl", "node_repositories")
node_repositories()

load("@io_bazel_rules_sass//:defs.bzl", "sass_repositories")
sass_repositories()

load("@graknlabs_bazel_distribution//common:dependencies.bzl", "bazelbuild_rules_pkg")
bazelbuild_rules_pkg()

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
rules_pkg_dependencies()

load("@graknlabs_dependencies//distribution/docker:deps.bzl", docker_deps = "deps")
docker_deps()

load("@io_bazel_rules_docker//repositories:repositories.bzl",
bazel_rules_docker_repositories = "repositories")
bazel_rules_docker_repositories()

load("@io_bazel_rules_docker//repositories:deps.bzl", bazel_rules_docker_container_deps = "deps")
bazel_rules_docker_container_deps()

load("@io_bazel_rules_docker//container:container.bzl", "container_pull")
container_pull(
  name = "openjdk_image",
  registry = "index.docker.io",
  repository = "library/openjdk",
  tag = "8"
)

load("@graknlabs_dependencies//tool/checkstyle:deps.bzl", checkstyle_deps = "deps")
checkstyle_deps()

load("@graknlabs_dependencies//tool/sonarcloud:deps.bzl", "sonarcloud_dependencies")
sonarcloud_dependencies()

load("@graknlabs_dependencies//tool/unuseddeps:deps.bzl", unuseddeps_deps = "deps")
unuseddeps_deps()

##########################
# Load @graknlabs_common #
##########################
load("//dependencies/graknlabs:dependencies.bzl", "graknlabs_common")
graknlabs_common()

#########################
# Load @graknlabs_graql #
#########################
load("//dependencies/graknlabs:dependencies.bzl", "graknlabs_graql")
graknlabs_graql()

load("@graknlabs_graql//dependencies/maven:artifacts.bzl", graknlabs_graql_artifacts = "artifacts")

##############################
# Load @graknlabs_grakn_core #
##############################
load("//dependencies/graknlabs:dependencies.bzl", "graknlabs_grakn_core")
graknlabs_grakn_core()
# load("@graknlabs_grakn_core//dependencies/maven:artifacts.bzl", graknlabs_grakn_core_artifacts = "artifacts")
load("@graknlabs_grakn_core//dependencies/maven:dependencies.bzl", graknlabs_grakn_core_maven_dependencies = "maven_dependencies")
graknlabs_grakn_core_maven_dependencies()

#################################
# Load @graknlabs_grabl_tracing #
#################################
load("@graknlabs_grakn_core//dependencies/graknlabs:dependencies.bzl", "graknlabs_grabl_tracing")
graknlabs_grabl_tracing()

load("@graknlabs_grabl_tracing//dependencies/maven:artifacts.bzl", graknlabs_grabl_tracing_artifacts = "artifacts")

###############################
# Load @graknlabs_client_java #
###############################
load("//dependencies/graknlabs:dependencies.bzl", "graknlabs_client_java")
graknlabs_client_java()
load("@graknlabs_client_java//dependencies/maven:artifacts.bzl", graknlabs_client_java_artifacts = "artifacts")

#################################
# Load @graknlabs_protocol #
#################################
load("@graknlabs_grakn_core//dependencies/graknlabs:dependencies.bzl", "graknlabs_protocol")
graknlabs_protocol()


########################
# Load Maven Artifacts #
########################
load("//dependencies/maven:dependencies.bzl", "maven_dependencies")
maven_dependencies()
maven(
    graknlabs_graql_artifacts +
    graknlabs_grabl_tracing_artifacts +
    graknlabs_client_java_artifacts
)

#########################
# Create Workspace Refs #
#########################
load("@graknlabs_bazel_distribution//common:rules.bzl", "workspace_refs")
workspace_refs(
    name = "graknlabs_console_workspace_refs"
)


# ################################
# # Load Grakn Labs dependencies #
# ################################

# load(
#     "//dependencies/graknlabs:dependencies.bzl",
#     "graknlabs_build_tools",
#     "graknlabs_common",
#     "graknlabs_graql",
#     "graknlabs_grakn_core",
#     "graknlabs_client_java",
# )
# graknlabs_client_java()
# graknlabs_build_tools()
# graknlabs_common()
# graknlabs_graql()
# graknlabs_grakn_core()

# load("@graknlabs_dependencies//distribution:deps.bzl", "graknlabs_bazel_distribution")
# graknlabs_bazel_distribution()

# ###########################
# # Load Bazel dependencies #
# ###########################

# load("@graknlabs_dependencies//builder/bazel:deps.bzl", "bazel_common", "bazel_deps", "bazel_toolchain")
# bazel_common()
# bazel_deps()
# bazel_toolchain()


# #################################
# # Load Build Tools dependencies #
# #################################

# load("@graknlabs_dependencies//tool/checkstyle:deps.bzl", "checkstyle_dependencies")
# checkstyle_dependencies()

# load("@graknlabs_dependencies//tool/sonarcloud:deps.bzl", "sonarcloud_dependencies")
# sonarcloud_dependencies()

# load("@graknlabs_dependencies//builder/bazel:deps.bzl", "bazel_rules_python")
# bazel_rules_python()

# load("@rules_python//python:pip.bzl", "pip_repositories", "pip3_import")
# pip_repositories()

# pip3_import(
#     name = "graknlabs_build_tools_ci_pip",
#     requirements = "@graknlabs_dependencies//tool/requirements.txt",
# )

# load("@graknlabs_dependencies_pip//:requirements.bzl",
# graknlabs_build_tools_ci_pip_install = "pip_install")
# graknlabs_build_tools_ci_pip_install()


# #####################################
# # Load Java dependencies from Maven #
# #####################################

# load("//dependencies/maven:dependencies.bzl", "maven_dependencies")
# maven_dependencies()


# ###########################
# # Load Graql dependencies #
# ###########################

# # Load ANTLR dependencies for Bazel
# load("@graknlabs_graql//dependencies/compilers:dependencies.bzl", "antlr_dependencies")
# antlr_dependencies()

# # Load ANTLR dependencies for ANTLR programs
# load("@rules_antlr//antlr:deps.bzl", "antlr_dependencies")
# antlr_dependencies()

# load("@graknlabs_graql//dependencies/maven:dependencies.bzl",
# graknlabs_graql_maven_dependencies = "maven_dependencies")
# graknlabs_graql_maven_dependencies()


# ################################
# # Load Grakn Core dependencies #
# ################################

# load("@graknlabs_grakn_core//dependencies/maven:dependencies.bzl",
# graknlabs_grakn_core_maven_dependencies = "maven_dependencies")
# graknlabs_grakn_core_maven_dependencies()

# load("@graknlabs_client_java//dependencies/graknlabs:dependencies.bzl", "graknlabs_grabl_tracing")
# graknlabs_grabl_tracing()

# load("@graknlabs_grabl_tracing//dependencies/maven:dependencies.bzl",
# graknlabs_grabl_tracing_maven_dependencies = "maven_dependencies")
# graknlabs_grabl_tracing_maven_dependencies()

# load("@graknlabs_dependencies//builder/bazel:deps.bzl", "bazel_rules_docker")
# bazel_rules_docker()

# ################################
# # Load Client Java dependencies #
# ################################
# load(
#     "@graknlabs_client_java//dependencies/graknlabs:dependencies.bzl", "graknlabs_grabl_tracing", "graknlabs_protocol")
# graknlabs_grabl_tracing()
# graknlabs_protocol()

# load("@graknlabs_client_java//dependencies/maven:dependencies.bzl",
# graknlabs_client_java_maven_dependencies = "maven_dependencies")
# graknlabs_client_java_maven_dependencies()

# #######################################
# # Load compiler dependencies for GRPC #
# #######################################

# load("@graknlabs_dependencies//builder/grpc/deps.bzl", "grpc_dependencies")
# grpc_dependencies()

# load("@com_github_grpc_grpc//bazel:grpc_deps.bzl",
# com_github_grpc_grpc_deps = "grpc_deps")
# com_github_grpc_grpc_deps()

# load("@stackb_rules_proto//java:deps.bzl", "java_grpc_compile")
# java_grpc_compile()


# ##################################
# # Load Distribution dependencies #
# ##################################

# # TODO: rename the macro we load here to deploy_github_dependencies
# load("@graknlabs_bazel_distribution//github:dependencies.bzl", "tcnksm_ghr")
# tcnksm_ghr()

# load("@graknlabs_bazel_distribution//common:dependencies.bzl", "bazelbuild_rules_pkg")
# bazelbuild_rules_pkg()

# load("@graknlabs_dependencies//builder/bazel:deps.bzl", "bazel_rules_docker")
# bazel_rules_docker()

# load("@io_bazel_rules_docker//repositories:repositories.bzl",
# bazel_rules_docker_repositories = "repositories")
# bazel_rules_docker_repositories()

# load("@io_bazel_rules_docker//container:container.bzl", "container_pull")
# container_pull(
#   name = "openjdk_image",
#   registry = "index.docker.io",
#   repository = "library/openjdk",
#   tag = "8"
# )

# #####################################
# # Load Bazel common workspace rules #
# #####################################

# # TODO: Figure out why this cannot be loaded at earlier at the top of the file
# load("@com_github_google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")
# google_common_workspace_rules()


# # Generate a JSON document of commit hashes of all external workspace dependencies
# load("@graknlabs_bazel_distribution//common:rules.bzl", "workspace_refs")
# workspace_refs(
#     name = "graknlabs_console_workspace_refs"
# )