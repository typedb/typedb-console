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
    {"artifact": "com.google.auto.value:auto-value:1.5.3", "lang": "java", "sha1": "514df6a7c7938de35c7f68dc8b8f22df86037f38", "sha256": "238d3b7535096d782d08576d1e42f79480713ff0794f511ff2cc147363ec072d", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/auto/value/auto-value/1.5.3/auto-value-1.5.3.jar", "source": {"sha1": "1bb4def82e18be0b6a58ab089fba288d712db6cb", "sha256": "7c9adb9f49a4f07e226778951e087da85759a9ab53ac375f9d076de6dc84ca2b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/auto/value/auto-value/1.5.3/auto-value-1.5.3-sources.jar"} , "name": "com-google-auto-value-auto-value", "actual": "@com-google-auto-value-auto-value//jar", "bind": "jar/com/google/auto/value/auto-value"},
# duplicates in com.google.code.findbugs:jsr305 fixed to 2.0.2
# - com.google.guava:guava:23.0 wanted version 1.3.9
# - io.grpc:grpc-core:1.16.0 wanted version 3.0.2
    {"artifact": "com.google.code.findbugs:jsr305:2.0.2", "lang": "java", "sha1": "516c03b21d50a644d538de0f0369c620989cd8f0", "sha256": "1e7f53fa5b8b5c807e986ba335665da03f18d660802d8bf061823089d1bee468", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/2.0.2/jsr305-2.0.2.jar", "name": "com-google-code-findbugs-jsr305", "actual": "@com-google-code-findbugs-jsr305//jar", "bind": "jar/com/google/code/findbugs/jsr305"},
    {"artifact": "com.google.code.gson:gson:2.7", "lang": "java", "sha1": "751f548c85fa49f330cecbb1875893f971b33c4e", "sha256": "2d43eb5ea9e133d2ee2405cc14f5ee08951b8361302fdd93494a3a997b508d32", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.7/gson-2.7.jar", "source": {"sha1": "bbb63ca253b483da8ee53a50374593923e3de2e2", "sha256": "2d3220d5d936f0a26258aa3b358160741a4557e046a001251e5799c2db0f0d74", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.7/gson-2.7-sources.jar"} , "name": "com-google-code-gson-gson", "actual": "@com-google-code-gson-gson//jar", "bind": "jar/com/google/code/gson/gson"},
# duplicates in com.google.errorprone:error_prone_annotations promoted to 2.2.0
# - com.google.guava:guava:23.0 wanted version 2.0.18
# - io.grpc:grpc-core:1.16.0 wanted version 2.2.0
# - io.opencensus:opencensus-api:0.12.3 wanted version 2.2.0
# - io.opencensus:opencensus-contrib-grpc-metrics:0.12.3 wanted version 2.2.0
    {"artifact": "com.google.errorprone:error_prone_annotations:2.2.0", "lang": "java", "sha1": "88e3c593e9b3586e1c6177f89267da6fc6986f0c", "sha256": "6ebd22ca1b9d8ec06d41de8d64e0596981d9607b42035f9ed374f9de271a481a", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.2.0/error_prone_annotations-2.2.0.jar", "source": {"sha1": "a8cd7823aa1dcd2fd6677c0c5988fdde9d1fb0a3", "sha256": "626adccd4894bee72c3f9a0384812240dcc1282fb37a87a3f6cb94924a089496", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.2.0/error_prone_annotations-2.2.0-sources.jar"} , "name": "com-google-errorprone-error_prone_annotations", "actual": "@com-google-errorprone-error_prone_annotations//jar", "bind": "jar/com/google/errorprone/error-prone-annotations"},
    {"artifact": "com.google.guava:guava:23.0", "lang": "java", "sha1": "c947004bb13d18182be60077ade044099e4f26f1", "sha256": "7baa80df284117e5b945b19b98d367a85ea7b7801bd358ff657946c3bd1b6596", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/guava/guava/23.0/guava-23.0.jar", "source": {"sha1": "ed233607c5c11e1a13a3fd760033ed5d9fe525c2", "sha256": "37fe8ba804fb3898c3c8f0cbac319cc9daa58400e5f0226a380ac94fb2c3ca14", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/guava/guava/23.0/guava-23.0-sources.jar"} , "name": "com-google-guava-guava", "actual": "@com-google-guava-guava//jar", "bind": "jar/com/google/guava/guava"},
    {"artifact": "com.google.j2objc:j2objc-annotations:1.1", "lang": "java", "sha1": "ed28ded51a8b1c6b112568def5f4b455e6809019", "sha256": "2994a7eb78f2710bd3d3bfb639b2c94e219cedac0d4d084d516e78c16dddecf6", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar", "source": {"sha1": "1efdf5b737b02f9b72ebdec4f72c37ec411302ff", "sha256": "2cd9022a77151d0b574887635cdfcdf3b78155b602abc89d7f8e62aba55cfb4f", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1-sources.jar"} , "name": "com-google-j2objc-j2objc-annotations", "actual": "@com-google-j2objc-j2objc-annotations//jar", "bind": "jar/com/google/j2objc/j2objc-annotations"},
    {"artifact": "commons-cli:commons-cli:1.3", "lang": "java", "sha1": "a48653b6bcd06b5e61ed63739ca601701fcb6a6c", "sha256": "f8046bdc72b7ff88afb1dff5ff45451df95290c78a639ec7fa40c953ca89cb26", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-cli/commons-cli/1.3/commons-cli-1.3.jar", "source": {"sha1": "3ed41a7767cda9dc3f33bf64b08ccf201cf23fb2", "sha256": "d1414bc4a7076b94ec790821cec8b8480857ebe932e57b5b6e48e0c012531ab5", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-cli/commons-cli/1.3/commons-cli-1.3-sources.jar"} , "name": "commons-cli-commons-cli", "actual": "@commons-cli-commons-cli//jar", "bind": "jar/commons-cli/commons-cli"},
    {"artifact": "commons-io:commons-io:2.3", "lang": "java", "sha1": "cd8d6ffc833cc63c30d712a180f4663d8f55799b", "sha256": "f9c7cbd53f85951f5d3ef7c08a1bf2097029fcc1d0bfb01bd07d3d79ff0286bb", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-io/commons-io/2.3/commons-io-2.3.jar", "source": {"sha1": "ba0ce5e28373e2a21f71395e711b961563ff619d", "sha256": "04062ce8d3c06e8b22284a4ae10a3a7cc6d26cc8e31ddd51ff521189d74bc9a1", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-io/commons-io/2.3/commons-io-2.3-sources.jar"} , "name": "commons-io-commons-io", "actual": "@commons-io-commons-io//jar", "bind": "jar/commons-io/commons-io"},
    {"artifact": "commons-lang:commons-lang:2.6", "lang": "java", "sha1": "0ce1edb914c94ebc388f086c6827e8bdeec71ac2", "sha256": "50f11b09f877c294d56f24463f47d28f929cf5044f648661c0f0cfbae9a2f49c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6.jar", "source": {"sha1": "67313d715fbf0ea4fd0bdb69217fb77f807a8ce5", "sha256": "66c2760945cec226f26286ddf3f6ffe38544c4a69aade89700a9a689c9b92380", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6-sources.jar"} , "name": "commons-lang-commons-lang", "actual": "@commons-lang-commons-lang//jar", "bind": "jar/commons-lang/commons-lang"},
    {"artifact": "io.grpc:grpc-context:1.16.0", "lang": "java", "sha1": "c979cb271666193f5ce7e704cbf9328d82eec14c", "sha256": "e6d2df6ccc8274093c8e42c23a9f600fb88f10581e7cd5a027936c64c01ba44b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.16.0/grpc-context-1.16.0.jar", "source": {"sha1": "d191e7a7eb9c2ec451d29c2d2a3a1774ab745b54", "sha256": "ca6d28dc2722aa1aac1b8e0cb9d415381104d5ff31f51fc48883252c68f3c291", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.16.0/grpc-context-1.16.0-sources.jar"} , "name": "io-grpc-grpc-context", "actual": "@io-grpc-grpc-context//jar", "bind": "jar/io/grpc/grpc-context"},
    {"artifact": "io.grpc:grpc-core:1.16.0", "lang": "java", "sha1": "eb2dbfedffb5f2ce9ada9a1577d3e01be0a6cd77", "sha256": "33d6ccd6bb6168e107cffa6bedac1f16939ac65d3f60839568ab96eeb70e0597", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.16.0/grpc-core-1.16.0.jar", "source": {"sha1": "48b139d706b0dc9e8ce48d9d0b8bd334cb884801", "sha256": "35e2bf94559382b2961f5edae69f1331bfabf37fdde450050ed1430047a8949a", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.16.0/grpc-core-1.16.0-sources.jar"} , "name": "io-grpc-grpc-core", "actual": "@io-grpc-grpc-core//jar", "bind": "jar/io/grpc/grpc-core"},
    {"artifact": "io.opencensus:opencensus-api:0.12.3", "lang": "java", "sha1": "743f074095f29aa985517299545e72cc99c87de0", "sha256": "8c1de62cbdaf74b01b969d1ed46c110bca1a5dd147c50a8ab8c5112f42ced802", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/opencensus/opencensus-api/0.12.3/opencensus-api-0.12.3.jar", "source": {"sha1": "09c2dad7aff8b6d139723b9181ba5da3f689213b", "sha256": "67e8b2120737c7dcfc61eef33f75319b1c4e5a2806d3c1a74cab810650ac7a19", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/opencensus/opencensus-api/0.12.3/opencensus-api-0.12.3-sources.jar"} , "name": "io-opencensus-opencensus-api", "actual": "@io-opencensus-opencensus-api//jar", "bind": "jar/io/opencensus/opencensus-api"},
    {"artifact": "io.opencensus:opencensus-contrib-grpc-metrics:0.12.3", "lang": "java", "sha1": "a4c7ff238a91b901c8b459889b6d0d7a9d889b4d", "sha256": "632c1e1463db471b580d35bc4be868facbfbf0a19aa6db4057215d4a68471746", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/opencensus/opencensus-contrib-grpc-metrics/0.12.3/opencensus-contrib-grpc-metrics-0.12.3.jar", "source": {"sha1": "9a7d004b774700837eeebff61230b8662d0e30d1", "sha256": "d54f6611f75432ca0ab13636a613392ae8b7136ba67eb1588fccdb8481f4d665", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/io/opencensus/opencensus-contrib-grpc-metrics/0.12.3/opencensus-contrib-grpc-metrics-0.12.3-sources.jar"} , "name": "io-opencensus-opencensus-contrib-grpc-metrics", "actual": "@io-opencensus-opencensus-contrib-grpc-metrics//jar", "bind": "jar/io/opencensus/opencensus-contrib-grpc-metrics"},
    {"artifact": "jline:jline:2.12", "lang": "java", "sha1": "ce9062c6a125e0f9ad766032573c041ae8ecc986", "sha256": "d34b45c8ca4359c65ae61e406339022e4731c739bc3448ce3999a60440baaa72", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/jline/jline/2.12/jline-2.12.jar", "source": {"sha1": "acb005a73638f26f85504bbeba42e9861e8f9d9f", "sha256": "273c96d90527a53e203990a563bfcd4fb0c39ea82b86c3307a357c7801d237d8", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/jline/jline/2.12/jline-2.12-sources.jar"} , "name": "jline-jline", "actual": "@jline-jline//jar", "bind": "jar/jline/jline"},
# duplicates in org.codehaus.mojo:animal-sniffer-annotations promoted to 1.17
# - com.google.guava:guava:23.0 wanted version 1.14
# - io.grpc:grpc-core:1.16.0 wanted version 1.17
    {"artifact": "org.codehaus.mojo:animal-sniffer-annotations:1.17", "lang": "java", "sha1": "f97ce6decaea32b36101e37979f8b647f00681fb", "sha256": "92654f493ecfec52082e76354f0ebf87648dc3d5cec2e3c3cdb947c016747a53", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.17/animal-sniffer-annotations-1.17.jar", "source": {"sha1": "8fb5b5ad9c9723951b9fccaba5bb657fa6064868", "sha256": "2571474a676f775a8cdd15fb9b1da20c4c121ed7f42a5d93fca0e7b6e2015b40", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.17/animal-sniffer-annotations-1.17-sources.jar"} , "name": "org-codehaus-mojo-animal-sniffer-annotations", "actual": "@org-codehaus-mojo-animal-sniffer-annotations//jar", "bind": "jar/org/codehaus/mojo/animal-sniffer-annotations"},
    {"artifact": "org.hamcrest:hamcrest-core:1.3", "lang": "java", "sha1": "42a25dc3219429f0e5d060061f71acb49bf010a0", "sha256": "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar", "source": {"sha1": "1dc37250fbc78e23a65a67fbbaf71d2e9cbc3c0b", "sha256": "e223d2d8fbafd66057a8848cc94222d63c3cedd652cc48eddc0ab5c39c0f84df", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3-sources.jar"} , "name": "org-hamcrest-hamcrest-core", "actual": "@org-hamcrest-hamcrest-core//jar", "bind": "jar/org/hamcrest/hamcrest-core"},
    {"artifact": "org.hamcrest:hamcrest-library:1.3", "lang": "java", "sha1": "4785a3c21320980282f9f33d0d1264a69040538f", "sha256": "711d64522f9ec410983bd310934296da134be4254a125080a0416ec178dfad1c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar", "source": {"sha1": "047a7ee46628ab7133129cd7cef1e92657bc275e", "sha256": "1c0ff84455f539eb3c29a8c430de1f6f6f1ba4b9ab39ca19b195f33203cd539c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3-sources.jar"} , "name": "org-hamcrest-hamcrest-library", "actual": "@org-hamcrest-hamcrest-library//jar", "bind": "jar/org/hamcrest/hamcrest-library"},
    {"artifact": "org.slf4j:slf4j-api:1.7.20", "lang": "java", "sha1": "867d63093eff0a0cb527bf13d397d850af3dcae3", "sha256": "2967c337180f6dca88a8a6140495b9f0b8a85b8527d02b0089bdbf9cdb34d40b", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.7.20/slf4j-api-1.7.20.jar", "source": {"sha1": "a12636375205fa54af1ec30d1ca2e6dbb96bf9bd", "sha256": "3bb14e45d8431c2bb35ffff82324763d1bed6e9b8782d48943b163e8fee2134c", "repository": "https://repo.maven.apache.org/maven2/", "url": "https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/1.7.20/slf4j-api-1.7.20-sources.jar"} , "name": "org-slf4j-slf4j-api", "actual": "@org-slf4j-slf4j-api//jar", "bind": "jar/org/slf4j/slf4j-api"},
    ]

def maven_dependencies(callback = jar_artifact_callback):
    for hash in list_dependencies():
        callback(hash)
