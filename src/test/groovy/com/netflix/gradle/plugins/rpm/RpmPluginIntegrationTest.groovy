package com.netflix.gradle.plugins.rpm

import com.google.common.io.Files
import com.netflix.gradle.plugins.utils.GradleUtils
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.apache.commons.io.FileUtils
import spock.lang.Issue
import spock.lang.Unroll

import static org.redline_rpm.header.Header.HeaderTag.DESCRIPTION
import static org.redline_rpm.header.Header.HeaderTag.NAME
import static org.redline_rpm.payload.CpioHeader.*

class RpmPluginIntegrationTest extends IntegrationSpec {
    @Issue("https://github.com/nebula-plugins/gradle-ospackage-plugin/issues/82")
    def "rpm task is marked up-to-date when setting arch or os property"() {

            given:
        buildFile << '''
apply plugin: 'com.github.prokod.rpm-build'

task buildRpm(type: Rpm) {
    packageName = 'rpmIsUpToDate'
    arch = NOARCH
    os = LINUX
}
'''
        when:
        runTasksSuccessfully('buildRpm')

        and:
        def result = runTasksSuccessfully('buildRpm')

        then:
        result.wasUpToDate(':buildRpm')
    }


    @Issue("https://github.com/nebula-plugins/gradle-ospackage-plugin/issues/48")
    def "Does not throw UnsupportedOperationException when copying external artifact with createDirectoryEntry option"() {
        given:
        String testCoordinates = 'com.netflix.nebula:a:1.0.0'
        DependencyGraph graph = new DependencyGraph([testCoordinates])
        File reposRootDir = directory('build/repos')
        GradleDependencyGenerator generator = new GradleDependencyGenerator(graph, reposRootDir.absolutePath)
        generator.generateTestMavenRepo()

        when:
        buildFile << """
apply plugin: 'com.github.prokod.rpm-build'

configurations {
    myConf
}

dependencies {
    myConf ${GradleUtils.quotedIfPresent(testCoordinates)}
}

repositories {
    maven {
        url {
            "file://${reposRootDir}/mavenrepo"
        }
    }
}

task buildRpm(type: Rpm) {
    packageName = 'bleah'
    
    from(configurations.myConf) {
        createDirectoryEntry = true
        into('root/lib')
    }
}
"""

        then:
        runTasksSuccessfully('buildRpm')
    }


    @Issue("https://github.com/nebula-plugins/gradle-ospackage-plugin/issues/58")
    def 'preserve symlinks without closure'() {
        given:
        File packageDir = directory("package")
        packageDir.mkdirs()
        File target = new File(packageDir,"my-script.sh")
        target.createNewFile()
        File file = new File(packageDir,'bin/my-symlink')
        Files.createParentDirs(file)
        java.nio.file.Files.createSymbolicLink(file.toPath(), target.toPath())
        buildFile << """
apply plugin: 'com.github.prokod.rpm-build'

task buildRpm(type: Rpm) {
    packageName = 'example'
    version '3'
    from 'package'
}
"""

        when:
        runTasksSuccessfully('buildRpm', '--warning-mode', 'none')

        then:
        def scan = Scanner.scan(this.file('build/distributions/example-3.noarch.rpm'))
        def symlink = scan.files.find { it.name == './bin/my-symlink' }
        symlink.header.type == SYMLINK
    }

    @Issue("https://github.com/nebula-plugins/gradle-ospackage-plugin/issues/58")
    def 'preserve symlinks with closure'() {
        given:
        File packageDir = directory("package")
        packageDir.mkdirs()
        File target = new File(packageDir,"my-script.sh")
        target.createNewFile()
        File file = new File(packageDir,'bin/my-symlink')
        Files.createParentDirs(file)
        java.nio.file.Files.createSymbolicLink(file.toPath(), target.toPath())
        buildFile << """
apply plugin: 'com.github.prokod.rpm-build'

task buildRpm(type: Rpm) {
    packageName = 'example'
    version '4'
    from('package') {
        into '/lib'
    }
}
"""

        when:
        runTasksSuccessfully('buildRpm', '--warning-mode', 'none')

        then:
        def scan = Scanner.scan(this.file('build/distributions/example-4.noarch.rpm'))
        def symlink = scan.files.find { it.name == './lib/bin/my-symlink' }
        symlink.header.type == SYMLINK
    }
}
