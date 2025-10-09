# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

package(default_visibility = ["//visibility:__subpackages__"])

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@rules_rust//rust:defs.bzl", "rust_test")

rust_test(
    name = "test-assembly-native",
    srcs = [
        "test_assembly.rs",
    ],
    deps = [
        "//tool/runner:typedb_binary_runner"
    ],
    env = select({
        "@typedb_bazel_distribution//platform:is_linux_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-linux-x86_64-targz)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_linux-x86_64//file)",
         },
        "@typedb_bazel_distribution//platform:is_linux_arm64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-linux-arm64-targz)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_linux-arm64//file)",
        },
        "@typedb_bazel_distribution//platform:is_mac_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-mac-x86_64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_mac-x86_64//file)",
        },
        "@typedb_bazel_distribution//platform:is_mac_arm64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-mac-arm64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_mac-arm64//file)",
        },
        "@typedb_bazel_distribution//platform:is_windows_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-windows-x86_64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_windows-x86_64//file)",
        },
    }),
    data = select({
        "@typedb_bazel_distribution//platform:is_linux_x86_64": [
            "//:assemble-linux-x86_64-targz",
            "@typedb_artifact_linux-x86_64//file",
        ],
        "@typedb_bazel_distribution//platform:is_linux_arm64" :  [
            "//:assemble-linux-arm64-targz",
            "@typedb_artifact_linux-arm64//file",
        ],
        "@typedb_bazel_distribution//platform:is_mac_x86_64" : [
            "//:assemble-mac-x86_64-zip",
            "@typedb_artifact_mac-x86_64//file",
        ],
        "@typedb_bazel_distribution//platform:is_mac_arm64" : [
            "//:assemble-mac-arm64-zip",
            "@typedb_artifact_mac-arm64//file",
        ],
        "@typedb_bazel_distribution//platform:is_windows_x86_64" : [
            "//:assemble-windows-x86_64-zip",
            "@typedb_artifact_windows-x86_64//file",
        ],
    }),
    use_libtest_harness = True,
)


rust_test(
    name = "test-assembly-commands",
    srcs = [
        "test_commands.rs",
    ],
    deps = [
        "//tool/runner:typedb_binary_runner"
    ],
    env = select({
        "@typedb_bazel_distribution//platform:is_linux_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-linux-x86_64-targz)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_linux-x86_64//file)",
         },
        "@typedb_bazel_distribution//platform:is_linux_arm64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-linux-arm64-targz)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_linux-arm64//file)",
        },
        "@typedb_bazel_distribution//platform:is_mac_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-mac-x86_64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_mac-x86_64//file)",
        },
        "@typedb_bazel_distribution//platform:is_mac_arm64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-mac-arm64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_mac-arm64//file)",
        },
        "@typedb_bazel_distribution//platform:is_windows_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-windows-x86_64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_windows-x86_64//file)",
        },
    }),
    data = select({
        "@typedb_bazel_distribution//platform:is_linux_x86_64": [
            "//:assemble-linux-x86_64-targz",
            "@typedb_artifact_linux-x86_64//file",
        ],
        "@typedb_bazel_distribution//platform:is_linux_arm64" :  [
            "//:assemble-linux-arm64-targz",
            "@typedb_artifact_linux-arm64//file",
        ],
        "@typedb_bazel_distribution//platform:is_mac_x86_64" : [
            "//:assemble-mac-x86_64-zip",
            "@typedb_artifact_mac-x86_64//file",
        ],
        "@typedb_bazel_distribution//platform:is_mac_arm64" : [
            "//:assemble-mac-arm64-zip",
            "@typedb_artifact_mac-arm64//file",
        ],
        "@typedb_bazel_distribution//platform:is_windows_x86_64" : [
            "//:assemble-windows-x86_64-zip",
            "@typedb_artifact_windows-x86_64//file",
        ],
    }),
    use_libtest_harness = True,
)


rust_test(
    name = "test-assembly-script",
    srcs = [
        "test_script.rs",
    ],
    deps = [
        "//tool/runner:typedb_binary_runner"
    ],
    env = select({
        "@typedb_bazel_distribution//platform:is_linux_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-linux-x86_64-targz)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_linux-x86_64//file)",
            "SCRIPT_PATH": "$(rootpath script.tqls)",
         },
        "@typedb_bazel_distribution//platform:is_linux_arm64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-linux-arm64-targz)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_linux-arm64//file)",
            "SCRIPT_PATH": "$(rootpath script.tqls)",
        },
        "@typedb_bazel_distribution//platform:is_mac_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-mac-x86_64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_mac-x86_64//file)",
            "SCRIPT_PATH": "$(rootpath script.tqls)",
        },
        "@typedb_bazel_distribution//platform:is_mac_arm64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-mac-arm64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_mac-arm64//file)",
            "SCRIPT_PATH": "$(rootpath script.tqls)",
        },
        "@typedb_bazel_distribution//platform:is_windows_x86_64" : {
            "TYPEDB_CONSOLE_ARCHIVE": "$(rootpath //:assemble-windows-x86_64-zip)",
            "TYPEDB_SERVER_ARCHIVE": "$(rootpath @typedb_artifact_windows-x86_64//file)",
            "SCRIPT_PATH": "$(rootpath script.tqls)",
        },
    }),
    data = ["script.tqls", "attributes.tql", "entities.tql"] + select({
        "@typedb_bazel_distribution//platform:is_linux_x86_64": [
            "//:assemble-linux-x86_64-targz",
            "@typedb_artifact_linux-x86_64//file",
        ],
        "@typedb_bazel_distribution//platform:is_linux_arm64" :  [
            "//:assemble-linux-arm64-targz",
            "@typedb_artifact_linux-arm64//file",
        ],
        "@typedb_bazel_distribution//platform:is_mac_x86_64" : [
            "//:assemble-mac-x86_64-zip",
            "@typedb_artifact_mac-x86_64//file",
        ],
        "@typedb_bazel_distribution//platform:is_mac_arm64" : [
            "//:assemble-mac-arm64-zip",
            "@typedb_artifact_mac-arm64//file",
        ],
        "@typedb_bazel_distribution//platform:is_windows_x86_64" : [
            "//:assemble-windows-x86_64-zip",
            "@typedb_artifact_windows-x86_64//file",
        ],
    }),
    use_libtest_harness = True,
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*"]),
    license_type = "mpl-header",
)
