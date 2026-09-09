Skinny WAR Maven Plugin
======================

> **Unmaintained — historical/reference use only.** This project is retired and is
> not recommended for new projects. Its original packaging and Spring/WebLogic
> load-time weaving needs are now better addressed by the approaches below.
> The existing code and dependencies are old and untested on modern Maven, JDK,
> and WebLogic stacks; this is not a claim that the plugin cannot work on them.

This Maven plugin **enhances/repackages an already-built EAR** to produce skinny
modules, using either a standard EAR shared-library layout or a WebLogic-specific
communal WAR layout. It does not generate an EAR from scratch. Its `ear` goal
opens `${project.build.directory}/${project.build.finalName}.ear` and modifies it
in place, after an EAR packager such as `maven-ear-plugin` has created it.

## Why this project is retired and what to use instead

The plugin was created to reduce configuration for shared dependencies and support
the Spring/WebLogic 12 load-time weaving (LTW) arrangement demonstrated in
[AspectJ LTW in WebLogic 12](https://github.com/asegner/spring-ltw-weblogic).
That specific LTW motivation is now largely obsolete: modern Spring no longer
includes the WebLogic-specific `WebLogicLoadTimeWeaver`. The removal is covered by
the [WebLogic Spring migration recipe](https://docs.openrewrite.org/recipes/oracle/weblogic/rewrite/spring/framework/replaceweblogicloadtimeweaver),
and current [Spring LTW documentation](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
describes the supported instrumentation options.

For new projects, prefer:

* **Standard skinny packaging:** use the maintained
  [Maven EAR Plugin's `skinnyModules` or `skinnyWars` options](https://maven.apache.org/plugins/maven-ear-plugin/ear-mojo.html).
  Configure the shared dependencies in the EAR project and package them in its
  library directory (for example, `lib/` via `defaultLibBundleDir`). Follow the
  [skinny modules example](https://maven.apache.org/plugins/maven-ear-plugin/examples/skinny-modules.html)
  for dependency declarations and manifest handling. This replaces the usual
  packaging need without a separate archive-rewriting plugin.
* **Ordinary shared libraries:** use the EAR's application library directory, or
  WebLogic's documented `APP-INF/lib` for application-level shared utility JARs,
  instead of introducing a communal WAR just to share dependencies. See
  [WebLogic application classloading](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/14.1.2/wlprg/classloading.html).
* **AspectJ LTW when still required:** use Spring's current LTW configuration with
  [`InstrumentationLoadTimeWeaver`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/instrument/classloading/InstrumentationLoadTimeWeaver.html)
  and the `spring-instrument` JVM agent (`-javaagent:/path/to/spring-instrument.jar`)
  where JVM startup options are under your control. Configure the aspects and
  weaving scope using the Spring LTW documentation; shared packaging alone does
  not enable weaving.

The communal WAR technique remains valid as a **WebLogic classloader-layout
mechanism**. Oracle still documents custom `<classloader-structure>` hierarchies;
retiring this plugin does not mean that mechanism has disappeared. Maven EAR's
skinny packaging does not automatically reproduce this plugin's communal WAR
hierarchy. If an existing application depends on that hierarchy, review its moved
libraries, generated descriptor, class identity, and weaving scope before migrating.
A deliberately configured WebLogic descriptor remains an option when a custom
hierarchy is actually required. The historical implementation is retained here as
a reference, rather than maintained as the default solution for those needs.

## How the plugin works

1. Open the existing EAR and inventory JARs in its recognized application modules.
2. Identify duplicates by **JAR filename**, not Maven coordinates, checksums, or
   contents. Different JARs with the same filename can therefore be treated as
   duplicates; identical JARs with different filenames are not matched.
3. For a filename found in more than one module, keep one copy in the configured
   `communalWar` under `WEB-INF/lib`, copying it there if necessary, and remove the
   other copies. The communal WAR must already be present in the EAR. Without
   `communalWar`, shared JARs go to the EAR's library directory instead. Pinned
   libraries stay in place; `earLibraries` and the default `forceAspectJLibToEar`
   rule send matching unpinned libraries to the EAR even in communal mode.
4. In communal mode, with `generateWeblogicLtwMetadata=true` (the default), create
   or rewrite `META-INF/weblogic-application.xml`. The plugin moves the communal
   WAR's module reference into a new outermost `<classloader-structure>`, nests any
   remaining existing structure beneath it, adds unlisted EJB modules at that
   outer level, and adds other unlisted modules in child structures.

For example, with no pre-existing custom hierarchy, `sharedwar.war`, an EJB, and
two application WARs produce a structure equivalent to:

```xml
<classloader-structure>
    <module-ref>
        <module-uri>sharedwar.war</module-uri>
    </module-ref>
    <module-ref>
        <module-uri>service-ejb.jar</module-uri>
    </module-ref>
    <classloader-structure>
        <module-ref>
            <module-uri>app1.war</module-uri>
        </module-ref>
    </classloader-structure>
    <classloader-structure>
        <module-ref>
            <module-uri>app2.war</module-uri>
        </module-ref>
    </classloader-structure>
</classloader-structure>
```

In WebLogic, the outermost structure represents the application classloader.
Assigning the communal WAR there makes its libraries available through the parent
loader to child modules. Merely placing JARs in another WAR's `WEB-INF/lib` does
not provide this visibility: ordinary sibling WAR classloaders are isolated.
See [Oracle's classloader hierarchy documentation](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/14.1.2/wlprg/classloading.html).

This layout provides common visibility and class identity for shared classes.
It does not, by itself, register a transformer or guarantee that a transformer on
the parent weaves classes defined by child loaders. `addToManifestClasspath`
defaults to `false`; the communal layout relies on the WebLogic hierarchy for
visibility. If metadata generation is disabled, supply an appropriate hierarchy
yourself.

Implementation references:
[`SkinnyWarEarEnhancer`](src/main/java/net/segner/maven/plugins/communal/enhancer/SkinnyWarEarEnhancer.java),
[`WeblogicApplicationXml`](src/main/java/net/segner/maven/plugins/communal/weblogic/WeblogicApplicationXml.java),
and [`WeblogicClassloaderStructure`](src/main/java/net/segner/maven/plugins/communal/weblogic/WeblogicClassloaderStructure.java).

## Historical usage examples

The examples below are retained for existing users and historical context, not as
a recommendation for new builds. First configure the EAR packager, then run this
plugin after it in the `package` phase. After installing this plugin locally, add
one of the following configurations to the EAR project's build plugins. The
`extensions` form adds the `ear` goal execution; it does not replace EAR packaging.


For a communal skinny WAR layout:
```xml
    <plugin>
        <groupId>net.segner.maven.plugins</groupId>
        <artifactId>skinnywar-maven-plugin</artifactId>
        <configuration>
            <communalWar>sharedwar.war</communalWar>
        </configuration>
        <extensions>true</extensions>
    </plugin>

```

For a standard skinny WAR layout

```xml
    <plugin>
        <groupId>net.segner.maven.plugins</groupId>
        <artifactId>skinnywar-maven-plugin</artifactId>
        <extensions>true</extensions>
    </plugin>

```

Without setting extension to true, the plugin may still be used with the slightly more verbose:

```xml
    <plugin>
        <groupId>net.segner.maven.plugins</groupId>
        <artifactId>skinnywar-maven-plugin</artifactId>
        <configuration>
            <communalWar>sharedwar.war</communalWar>
        </configuration>
        <executions>
            <execution>
                <phase>package</phase>
                <goals>
                    <goal>ear</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

```


## Using EJB
--------------------------------------------------

The original communal LTW demo used the following additional POM configuration to
place EJB dependencies in the intended locations. These are historical setup
instructions for that layout, not general requirements for modern Spring LTW.

For each EJB:
1. the EJB dependency scope must be marked ```provided``` in the EAR level pom
    ```xml
        <dependencies>
        ...
            <dependency>
                <groupId>net.segner.poc.ejb</groupId>
                <artifactId>net-segner-poc-service</artifactId>
                <type>ejb</type>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    ```
1. the EJB dependency with type ```pom``` must be added as a dependency of the Communal WAR
    ```xml
        <dependencies>
        ...
            <dependency>
                <groupId>net.segner.poc.ejb</groupId>
                <artifactId>net-segner-poc-service</artifactId>
                <type>pom</type>
            </dependency>
        </dependencies>
    ```


## Installing Plugin Locally
--------------------------------------------------

The historical build declares Maven 3.3.3 as its minimum and targets Java 8.
These are original build settings, not a current compatibility guarantee.
The snapshot repository configuration below is retained as historical context;
availability of these old snapshot artifacts has not been verified.

```xml
    <pluginRepositories>
    ...
        <pluginRepository>
            <id>ossrh</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        </pluginRepository>
    </pluginRepositories>
```

The original local build/install command is shown below. A successful build installs
the plugin into the local Maven repository; building on modern stacks may require
dependency or build-tool updates.

`$ mvn clean install`


## Plugin Usage
--------------------------------------------------

* `communalWar`
  * Filename of an existing WAR module in the EAR to receive shared JARs in `WEB-INF/lib`
  * With metadata generation enabled, assigns that WAR to the parent/application classloader in WebLogic
  * Historical motivation: [AspectJ LTW in WebLogic 12](https://github.com/asegner/spring-ltw-weblogic)
* `generateWeblogicLtwMetadata`
  * `true` | `false` (default: `true`)
  * Create or rewrite the `<classloader-structure>` section of the EAR's `META-INF/weblogic-application.xml`, creating the file if absent
  * Configures the communal classloader hierarchy; does not enable weaving by itself
  * Ignored if the communal war layout is not in use
* `warningBreaksBuild`
  * `true` | `false` (default: `true`)
  * Force a warning to fail a build
* `forceAspectJLibToEar`
  * `true` | `false` (default: `true`)
  * Add AspectJ related libraries to the ear libraries list below
* `addToManifestClasspath`
  * `true` | `false` (default: `false`)
  * Inserts references to the shared libraries into the beginning of the MANIFEST.MF Class-Path attribute
* `earLibraries`
  * List of libraries that must be relocated to the EAR, if found (rarely needed)
```xml
<earLibraries>
    <libraryPrefixFilter>spring-webmvc</libraryPrefixFilter>
    <libraryPrefixFilter>spring-web-</libraryPrefixFilter>
</earLibraries>
```
* `pinnedLibraries`
  * List of libraries that should not be relocated. List should be specified in the same manner as ear libraries.


## Demo Project
--------------------------------------------------
The original WebLogic 12/Spring LTW demonstration is preserved as historical context:
[Plugin Demo Project](https://github.com/asegner/spring-ltw-weblogic).


## License
--------------------------------------------------
* [MIT License](http://www.opensource.org/licenses/mit-license.php)
