# Do not edit. bazel-deps autogenerates this file from dependencies/maven/dependencies.yaml.
def _jar_artifact_impl(ctx):
    jar_name = "%s.jar" % ctx.name
    ctx.download(
        output=ctx.path("jar/%s" % jar_name),
        url=ctx.attr.urls,
        sha256=ctx.attr.sha256,
        executable=False
    )
    src_name="%s-sources.jar" % ctx.name
    srcjar_attr=""
    has_sources = len(ctx.attr.src_urls) != 0
    if has_sources:
        ctx.download(
            output=ctx.path("jar/%s" % src_name),
            url=ctx.attr.src_urls,
            sha256=ctx.attr.src_sha256,
            executable=False
        )
        srcjar_attr ='\n    srcjar = ":%s",' % src_name

    build_file_contents = """
package(default_visibility = ['//visibility:public'])
java_import(
    name = 'jar',
    tags = ['maven_coordinates={artifact}'],
    jars = ['{jar_name}'],{srcjar_attr}
)
filegroup(
    name = 'file',
    srcs = [
        '{jar_name}',
        '{src_name}'
    ],
    visibility = ['//visibility:public']
)\n""".format(artifact = ctx.attr.artifact, jar_name = jar_name, src_name = src_name, srcjar_attr = srcjar_attr)
    ctx.file(ctx.path("jar/BUILD"), build_file_contents, False)
    return None

jar_artifact = repository_rule(
    attrs = {
        "artifact": attr.string(mandatory = True),
        "sha256": attr.string(mandatory = True),
        "urls": attr.string_list(mandatory = True),
        "src_sha256": attr.string(mandatory = False, default=""),
        "src_urls": attr.string_list(mandatory = False, default=[]),
    },
    implementation = _jar_artifact_impl
)

def jar_artifact_callback(hash):
    src_urls = []
    src_sha256 = ""
    source=hash.get("source", None)
    if source != None:
        src_urls = [source["url"]]
        src_sha256 = source["sha256"]
    jar_artifact(
        artifact = hash["artifact"],
        name = hash["name"],
        urls = [hash["url"]],
        sha256 = hash["sha256"],
        src_urls = src_urls,
        src_sha256 = src_sha256
    )
    native.bind(name = hash["bind"], actual = hash["actual"])


def list_dependencies():
    return [
    {"artifact": "com.google.android:annotations:4.1.1.4", "lang": "java", "sha1": "a1678ba907bf92691d879fef34e1a187038f9259", "sha256": "ba734e1e84c09d615af6a09d33034b4f0442f8772dec120efb376d86a565ae15", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/android/annotations/4.1.1.4/annotations-4.1.1.4.jar", "source": {"sha1": "deb22daeb37bdcbc14230aeaeddce38320d6d0f9", "sha256": "e9b667aa958df78ea1ad115f7bbac18a5869c3128b1d5043feb360b0cfce9d40", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/android/annotations/4.1.1.4/annotations-4.1.1.4-sources.jar"} , "name": "com-google-android-annotations", "actual": "@com-google-android-annotations//jar", "bind": "jar/com/google/android/annotations"},
    {"artifact": "com.google.auto.value:auto-value:1.5.3", "lang": "java", "sha1": "514df6a7c7938de35c7f68dc8b8f22df86037f38", "sha256": "238d3b7535096d782d08576d1e42f79480713ff0794f511ff2cc147363ec072d", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/auto/value/auto-value/1.5.3/auto-value-1.5.3.jar", "source": {"sha1": "1bb4def82e18be0b6a58ab089fba288d712db6cb", "sha256": "7c9adb9f49a4f07e226778951e087da85759a9ab53ac375f9d076de6dc84ca2b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/auto/value/auto-value/1.5.3/auto-value-1.5.3-sources.jar"} , "name": "com-google-auto-value-auto-value", "actual": "@com-google-auto-value-auto-value//jar", "bind": "jar/com/google/auto/value/auto-value"},
# duplicates in com.google.code.findbugs:jsr305 fixed to 2.0.2
# - com.google.guava:guava:23.0 wanted version 1.3.9
# - io.grpc:grpc-api:1.29.0 wanted version 3.0.2
# - io.perfmark:perfmark-api:0.19.0 wanted version 3.0.2
    {"artifact": "com.google.code.findbugs:jsr305:2.0.2", "lang": "java", "sha1": "516c03b21d50a644d538de0f0369c620989cd8f0", "sha256": "1e7f53fa5b8b5c807e986ba335665da03f18d660802d8bf061823089d1bee468", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/2.0.2/jsr305-2.0.2.jar", "name": "com-google-code-findbugs-jsr305", "actual": "@com-google-code-findbugs-jsr305//jar", "bind": "jar/com/google/code/findbugs/jsr305"},
    {"artifact": "com.google.code.gson:gson:2.8.6", "lang": "java", "sha1": "9180733b7df8542621dc12e21e87557e8c99b8cb", "sha256": "c8fb4839054d280b3033f800d1f5a97de2f028eb8ba2eb458ad287e536f3f25f", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.8.6/gson-2.8.6.jar", "source": {"sha1": "1b9adea7bbe0b251818f42fde0bd2988d7e5f20a", "sha256": "da4d787939dc8de214724a20d88614b70ef8c3a4931d9c694300b5d9098ed9bc", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.8.6/gson-2.8.6-sources.jar"} , "name": "com-google-code-gson-gson", "actual": "@com-google-code-gson-gson//jar", "bind": "jar/com/google/code/gson/gson"},
# duplicates in com.google.errorprone:error_prone_annotations promoted to 2.3.4
# - com.google.guava:guava:23.0 wanted version 2.0.18
# - io.grpc:grpc-api:1.29.0 wanted version 2.3.4
# - io.grpc:grpc-core:1.29.0 wanted version 2.3.4
    {"artifact": "com.google.errorprone:error_prone_annotations:2.3.4", "lang": "java", "sha1": "dac170e4594de319655ffb62f41cbd6dbb5e601e", "sha256": "baf7d6ea97ce606c53e11b6854ba5f2ce7ef5c24dddf0afa18d1260bd25b002c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.3.4/error_prone_annotations-2.3.4.jar", "source": {"sha1": "950adf6dcd7361e3d1e544a6e13b818587f95d14", "sha256": "0b1011d1e2ea2eab35a545cffd1cff3877f131134c8020885e8eaf60a7d72f91", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.3.4/error_prone_annotations-2.3.4-sources.jar"} , "name": "com-google-errorprone-error_prone_annotations", "actual": "@com-google-errorprone-error_prone_annotations//jar", "bind": "jar/com/google/errorprone/error-prone-annotations"},
    {"artifact": "com.google.guava:guava:23.0", "lang": "java", "sha1": "c947004bb13d18182be60077ade044099e4f26f1", "sha256": "7baa80df284117e5b945b19b98d367a85ea7b7801bd358ff657946c3bd1b6596", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/guava/guava/23.0/guava-23.0.jar", "source": {"sha1": "ed233607c5c11e1a13a3fd760033ed5d9fe525c2", "sha256": "37fe8ba804fb3898c3c8f0cbac319cc9daa58400e5f0226a380ac94fb2c3ca14", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/guava/guava/23.0/guava-23.0-sources.jar"} , "name": "com-google-guava-guava", "actual": "@com-google-guava-guava//jar", "bind": "jar/com/google/guava/guava"},
    {"artifact": "com.google.j2objc:j2objc-annotations:1.3", "lang": "java", "sha1": "ba035118bc8bac37d7eff77700720999acd9986d", "sha256": "21af30c92267bd6122c0e0b4d20cccb6641a37eaf956c6540ec471d584e64a7b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3.jar", "source": {"sha1": "d26c56180205cbb50447c3eca98ecb617cf9f58b", "sha256": "ba4df669fec153fa4cd0ef8d02c6d3ef0702b7ac4cabe080facf3b6e490bb972", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3-sources.jar"} , "name": "com-google-j2objc-j2objc-annotations", "actual": "@com-google-j2objc-j2objc-annotations//jar", "bind": "jar/com/google/j2objc/j2objc-annotations"},
    {"artifact": "commons-cli:commons-cli:1.3", "lang": "java", "sha1": "a48653b6bcd06b5e61ed63739ca601701fcb6a6c", "sha256": "f8046bdc72b7ff88afb1dff5ff45451df95290c78a639ec7fa40c953ca89cb26", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-cli/commons-cli/1.3/commons-cli-1.3.jar", "source": {"sha1": "3ed41a7767cda9dc3f33bf64b08ccf201cf23fb2", "sha256": "d1414bc4a7076b94ec790821cec8b8480857ebe932e57b5b6e48e0c012531ab5", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-cli/commons-cli/1.3/commons-cli-1.3-sources.jar"} , "name": "commons-cli-commons-cli", "actual": "@commons-cli-commons-cli//jar", "bind": "jar/commons-cli/commons-cli"},
    {"artifact": "commons-io:commons-io:2.3", "lang": "java", "sha1": "cd8d6ffc833cc63c30d712a180f4663d8f55799b", "sha256": "f9c7cbd53f85951f5d3ef7c08a1bf2097029fcc1d0bfb01bd07d3d79ff0286bb", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-io/commons-io/2.3/commons-io-2.3.jar", "source": {"sha1": "ba0ce5e28373e2a21f71395e711b961563ff619d", "sha256": "04062ce8d3c06e8b22284a4ae10a3a7cc6d26cc8e31ddd51ff521189d74bc9a1", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-io/commons-io/2.3/commons-io-2.3-sources.jar"} , "name": "commons-io-commons-io", "actual": "@commons-io-commons-io//jar", "bind": "jar/commons-io/commons-io"},
    {"artifact": "commons-lang:commons-lang:2.6", "lang": "java", "sha1": "0ce1edb914c94ebc388f086c6827e8bdeec71ac2", "sha256": "50f11b09f877c294d56f24463f47d28f929cf5044f648661c0f0cfbae9a2f49c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6.jar", "source": {"sha1": "67313d715fbf0ea4fd0bdb69217fb77f807a8ce5", "sha256": "66c2760945cec226f26286ddf3f6ffe38544c4a69aade89700a9a689c9b92380", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6-sources.jar"} , "name": "commons-lang-commons-lang", "actual": "@commons-lang-commons-lang//jar", "bind": "jar/commons-lang/commons-lang"},
    {"artifact": "io.grpc:grpc-api:1.29.0", "lang": "java", "sha1": "04f067a7b1657ad95c00fe958e8a66c8f8446c9f", "sha256": "4837824acdd8d576d7d31a862e7391c38a1824cd2224daa68999377fdff9ae3f", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-api/1.29.0/grpc-api-1.29.0.jar", "source": {"sha1": "fd872828400620c57afe4685753cd75db763ea39", "sha256": "f9265d8b37d4b35fa6184be21a5b5975246198ab730beaf4efec459cade14f5b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-api/1.29.0/grpc-api-1.29.0-sources.jar"} , "name": "io-grpc-grpc-api", "actual": "@io-grpc-grpc-api//jar", "bind": "jar/io/grpc/grpc-api"},
    {"artifact": "io.grpc:grpc-context:1.29.0", "lang": "java", "sha1": "1d8a441110f86f8927543dc3007639080441ea3c", "sha256": "41426f8fa5b5ff6e8cf5d6a7a6e7b1175350bc8c8e11f352e0622e00f99c4a02", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.29.0/grpc-context-1.29.0.jar", "source": {"sha1": "9be4b89f1140e9dbb084f793627f76ee8ad47b46", "sha256": "56552f0eab62fd8fa770908b0e88e1313474f1a0372cd32ec210f45e9fa2079e", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.29.0/grpc-context-1.29.0-sources.jar"} , "name": "io-grpc-grpc-context", "actual": "@io-grpc-grpc-context//jar", "bind": "jar/io/grpc/grpc-context"},
    {"artifact": "io.grpc:grpc-core:1.29.0", "lang": "java", "sha1": "b051a14a67c97bb9bbe0b9a03b5d7e7080e7b960", "sha256": "d45e3ba310cf6a5d8170bcc500507977505614583c341d03c7d91658e49cf028", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.29.0/grpc-core-1.29.0.jar", "source": {"sha1": "236040aa86f3bf7b44785d48670007ea2b28eb56", "sha256": "c96e39670a2bc73e572f4af090bd9ff55528b07220363d75761beb999efbd7f8", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.29.0/grpc-core-1.29.0-sources.jar"} , "name": "io-grpc-grpc-core", "actual": "@io-grpc-grpc-core//jar", "bind": "jar/io/grpc/grpc-core"},
    {"artifact": "io.perfmark:perfmark-api:0.19.0", "lang": "java", "sha1": "2bfc352777fa6e27ad1e11d11ea55651ba93236b", "sha256": "b734ba2149712409a44eabdb799f64768578fee0defe1418bb108fe32ea43e1a", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/perfmark/perfmark-api/0.19.0/perfmark-api-0.19.0.jar", "source": {"sha1": "ca61d9fa052fd1caef7ccadd9b35fca7cc9184a1", "sha256": "05cfbdd34e6fc1f10181c755cec67cf1ee517dfee615e25d1007a8aabd569dba", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/perfmark/perfmark-api/0.19.0/perfmark-api-0.19.0-sources.jar"} , "name": "io-perfmark-perfmark-api", "actual": "@io-perfmark-perfmark-api//jar", "bind": "jar/io/perfmark/perfmark-api"},
    {"artifact": "jline:jline:2.12", "lang": "java", "sha1": "ce9062c6a125e0f9ad766032573c041ae8ecc986", "sha256": "d34b45c8ca4359c65ae61e406339022e4731c739bc3448ce3999a60440baaa72", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/jline/jline/2.12/jline-2.12.jar", "source": {"sha1": "acb005a73638f26f85504bbeba42e9861e8f9d9f", "sha256": "273c96d90527a53e203990a563bfcd4fb0c39ea82b86c3307a357c7801d237d8", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/jline/jline/2.12/jline-2.12-sources.jar"} , "name": "jline-jline", "actual": "@jline-jline//jar", "bind": "jar/jline/jline"},
# duplicates in org.codehaus.mojo:animal-sniffer-annotations promoted to 1.18
# - com.google.guava:guava:23.0 wanted version 1.14
# - io.grpc:grpc-api:1.29.0 wanted version 1.18
    {"artifact": "org.codehaus.mojo:animal-sniffer-annotations:1.18", "lang": "java", "sha1": "f7aa683ea79dc6681ee9fb95756c999acbb62f5d", "sha256": "47f05852b48ee9baefef80fa3d8cea60efa4753c0013121dd7fe5eef2e5c729d", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.18/animal-sniffer-annotations-1.18.jar", "source": {"sha1": "0dff084acf1ff3ff9145b1708c566787d2c82dd0", "sha256": "ee078a91bf7136ee1961abd612b54d1cd9877352b960a7e1e7e3e4c17ceafcf1", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.18/animal-sniffer-annotations-1.18-sources.jar"} , "name": "org-codehaus-mojo-animal-sniffer-annotations", "actual": "@org-codehaus-mojo-animal-sniffer-annotations//jar", "bind": "jar/org/codehaus/mojo/animal-sniffer-annotations"},
    {"artifact": "org.hamcrest:hamcrest-core:1.3", "lang": "java", "sha1": "42a25dc3219429f0e5d060061f71acb49bf010a0", "sha256": "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar", "source": {"sha1": "1dc37250fbc78e23a65a67fbbaf71d2e9cbc3c0b", "sha256": "e223d2d8fbafd66057a8848cc94222d63c3cedd652cc48eddc0ab5c39c0f84df", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3-sources.jar"} , "name": "org-hamcrest-hamcrest-core", "actual": "@org-hamcrest-hamcrest-core//jar", "bind": "jar/org/hamcrest/hamcrest-core"},
    {"artifact": "org.hamcrest:hamcrest-library:1.3", "lang": "java", "sha1": "4785a3c21320980282f9f33d0d1264a69040538f", "sha256": "711d64522f9ec410983bd310934296da134be4254a125080a0416ec178dfad1c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar", "source": {"sha1": "047a7ee46628ab7133129cd7cef1e92657bc275e", "sha256": "1c0ff84455f539eb3c29a8c430de1f6f6f1ba4b9ab39ca19b195f33203cd539c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3-sources.jar"} , "name": "org-hamcrest-hamcrest-library", "actual": "@org-hamcrest-hamcrest-library//jar", "bind": "jar/org/hamcrest/hamcrest-library"},
    {"artifact": "org.slf4j:slf4j-api:1.7.20", "lang": "java", "sha1": "867d63093eff0a0cb527bf13d397d850af3dcae3", "sha256": "2967c337180f6dca88a8a6140495b9f0b8a85b8527d02b0089bdbf9cdb34d40b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.7.20/slf4j-api-1.7.20.jar", "source": {"sha1": "a12636375205fa54af1ec30d1ca2e6dbb96bf9bd", "sha256": "3bb14e45d8431c2bb35ffff82324763d1bed6e9b8782d48943b163e8fee2134c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.7.20/slf4j-api-1.7.20-sources.jar"} , "name": "org-slf4j-slf4j-api", "actual": "@org-slf4j-slf4j-api//jar", "bind": "jar/org/slf4j/slf4j-api"},
    ]

def maven_dependencies(callback = jar_artifact_callback):
    for hash in list_dependencies():
        callback(hash)
