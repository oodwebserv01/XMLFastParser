# **XMLFastParser Build and Packaging Instructions**

## **Overview**

This document provides comprehensive instructions for building, testing, and packaging the **xmlFastParser** library. The library is designed to be a high-performance, multi-threaded XML parser with predictive shortcuts, and serves as a core dependency for the **XML2CSV** tool.

## **Prerequisites**

### **Development Environment**
- **Java 17+** (JDK 17 or higher)
- **Gradle** (Version 8.x or higher)
- **Maven** (Optional, for testing)
- **Git** (for version control)

### **Required Directories**
```
XMLFastParser/
├── xmlFastParser/                    # Source code
│   ├── build.gradle                 # Gradle build file
│   └── src/                         # Source Java files
│       ├── xmlFastParser/            # Package directory
│       └── main/java/                 # Main application source
├── build/                           # Gradle build directory
├── xmlFastParser.jar                # Output JAR file
├── documentation/                    # API documentation
└── examples/                         # Usage examples
```

## **Building the Library**

### **Method 1: Using Gradle Wrapper**

If `gradlew` is available in your system:

```bash
# Navigate to the xmlFastParser directory
cd XMLFastParser/xmlFastParser

# Build with tests
cd /c/tmp/contents/XMLFastReader && ./gradlew build

# Build only JAR (faster)
cd /c/tmp/contents/XMLFastReader && ./gradlew jar

# Clean build
cd /c/tmp/contents/XMLFastReader && ./gradlew clean build

# Build with Java 21 compatibility
cd /c/tmp/contents/XMLFastReader && ./gradlew jar --java-version 21
```

### **Method 2: Using Gradle Directly**

If Gradle is installed globally:

```bash
# Navigate to the xmlFastParser directory
cd XMLFastParser/xmlFastParser

# Build with tests
gradle build --no-daemon

# Build only JAR (faster)
gradle jar --no-daemon

# Clean build
gradle clean build --no-daemon

# Build with Java 21 compatibility
gradle jar --no-daemon --java-version 21
```

### **Method 3: Using Maven (if available)**

```bash
# Navigate to xmlFastParser directory
cd XMLFastParser/xmlFastParser

# Build using Maven (requires pom.xml)
mvn clean package -DskipTests
```

## **Build Output Structure**

After successful build, the following files will be generated:

```
xmlFastParser/  # Source directory
├── build/          # Gradle build output
│   ├── classes/    # Compiled Java classes
│   └── libs/       # Library dependencies
├── xmlFastParser.jar  # Main JAR file with manifest
├── javadoc/          # Javadoc documentation
└── reports/          # Build reports (if configured)
```

### **JAR File Details**

The main `xmlFastParser.jar` contains:

- **Main Class**: `xmlFastParser.xmlBluePrint`
- **Dependencies**: All required libraries bundled
- **Manifest**: Includes classpath and main entry point
- **Package**: `xmlFastParser.*` classes

## **Configuration Options**

### **Gradle Build Configuration**

The `build.gradle` file contains several configurable options:

```gradle
plugins {
    id 'java'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

tasks.named('jar') {
    manifest {
        attributes(
            'Main-Class': 'xmlFastParser.xmlBluePrint'
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Optional: Code formatting
formatting {
    gradleWrappersVersion = '8.5'
}
```

### **Build Variants**

You can create different build variants by modifying the Gradle configuration:

```bash
# Production build (optimized)
gradle build -x test -Poptimize=true

# Debug build (with debug symbols)
gradle jar --debug

# Development build (with source and javadoc)
gradle jar sourcesJar javadocJar
```

## **Testing**

### **Running Tests**

```bash
# Navigate to the build directory
cd XMLFastParser/xmlFastParser

# Run all tests
gradle test

# Run specific test classes
gradle test --tests "*TestClassName*"

# Run tests with custom options
gradle test -Dtest.suite=performance -Dtest.threads=4
```

### **Test Output**

Test results are stored in:
```
xmlFastParser/build/test-results/
├── junit/           # JUnit test results
├── performance/     # Performance test results
└── integration/    # Integration test results
```

## **Integration with XML2CSV**

### **Dependency Setup**

XML2CSV depends on xmlFastParser through the following Gradle configuration:

```gradle
repositories {
    mavenCentral()
    mavenLocal()
    flatDir {
        dirs '../xmlFastParser/xmlFastParser/build/libs'
    }
}

dependencies {
    // Local xmlFastParser module
    implementation files('../xmlFastParser/xmlFastParser/build/libs/xmlFastParser.jar')
}
```

### **Manual Integration**

If building XML2CSV manually:

1. **Ensure xmlFastParser is built**: `cd XMLFastParser/xmlFastParser && gradle jar`
2. **Copy the JAR to XML2CSV libs**: `cp XMLFastParser/xmlFastParser/build/libs/xmlFastParser.jar XML2CSV/build/libs/`
3. **Update classpath**: Include `XML2CSV/build/libs/xmlFastParser.jar` in the classpath

## **Verification Steps**

### **Post-Build Verification**

1. **Check JAR File**: Ensure `xmlFastParser.jar` exists in `xmlFastParser/build/libs/`
2. **Verify Dependencies**: Check that all required libraries are bundled
3. **Test Basic Functionality**: Run simple XML parsing test
4. **Check Manifest**: Ensure manifest specifies the correct main class

### **XML2CSV Integration Test**

1. **Build XML2CSV**: Run `cd XML2CSV && gradle jar` (or `mvn package`)
2. **Run Integration Test**: Execute a sample conversion
3. **Verify Output**: Check that the output CSV is correct
4. **Performance Check**: Ensure parsing performance is acceptable

## **Distribution**

### **Packaging for Distribution**

For library distribution, create a ZIP package:

```bash
# Create distribution package
cd XMLFastParser
zip -r xmlFastParser-distribution.zip \
    xmlFastParser/ \
    build/libs/xmlFastParser.jar \
    documentation/ \
    examples/ \
    LICENSE \
    README.md
```

### **Maven Central Integration**

To publish to Maven Central, you need:

1. **Sonatype Nexus Repository Manager** account
2. **Gradle publication plugin**
3. **GPG key** for signing artifacts
4. **POM file** with proper metadata

Example `build.gradle` for Maven Central:

```gradle
plugins {
    id 'maven-publish'
    id 'signing'
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            pom {
                name = 'xmlFastParser'
                description = 'High-performance XML parser with predictive shortcuts'
                url = 'https://github.com/yourusername/xmlFastParser'
                licenses {
                    license {
                        name = 'MIT License'
                        url = 'https://opensource.org/licenses/MIT'
                    }
                }
                developers {
                    developer {
                        id = 'yourusername'
                        name = 'Your Name'
                        email = 'your.email@example.com'
                    }
                }
            }
        }
    }
    repositories {
        maven {
            url = "https://s01.oss.sonatype.org/service/local/staging"
            credentials {
                username = System.getenv('OSSRH_USERNAME')
                password = System.getenv('OSSRH_PASSWORD')
            }
        }
    }
}

// Signing
 signing {
    sign publishing.publications.mavenJava
}
```

## **Troubleshooting**

### **Common Build Issues**

1. **Java Version Mismatch**:
   ```bash
   # Fix Java version
   sdk use java 21.0.2
   # or
   export JAVA_HOME=/path/to/jdk-21
   ```

2. **Gradle Not Found**:
   ```bash
   # Install Gradle
   wget https://services.gradle.org/distributions/gradle-8.5-bin.zip
   sudo unzip gradle-8.5-bin.zip -d /opt/gradle
   sudo ln -s /opt/gradle/gradle-8.5/bin/gradle /usr/local/bin/gradle
   ```

3. **Missing Dependencies**:
   ```bash
   # Update dependencies in build.gradle
   dependencies {
       implementation 'org.slf4j:slf4j-api:2.0.13'
   }
   ```

### **Debugging Build Issues**

```bash
# Detailed build output with debugging information
cd XMLFastParser/xmlFastParser
gradle jar --stacktrace

# Show dependency resolution
gradle dependencies

# Clean build with verbose output
gradle clean jar --refresh-dependencies
```

## **Maintenance**

### **Updating Dependencies**

To update library dependencies, edit the `build.gradle` file:

```gradle
dependencies {
    implementation 'org.slf4j:slf4j-api:2.0.13'
    implementation 'org.apache.commons:commons-lang3:3.14.0'
}

// Apply dependency updates
gradle dependencyUpdates
```

### **Regular Build Maintenance**

```bash
# Weekly maintenance
gradle clean build test

# Monthly full build with documentation
gradle clean build javadoc test -Pgenerate-docs=true

# Performance testing
gradle clean build -Pprofile=true -Pperf-tests=true
```

## **Quick Start Guide**

1. **Clone Repository**: `git clone <repository-url>`
2. **Navigate to xmlFastParser**: `cd XMLFastReader/xmlFastParser/xmlFastParser`
3. **Build**: `./gradlew jar`
4. **Verify**: Check `xmlFastParser/build/libs/xmlFastParser.jar`
5. **Integrate**: Copy JAR to XML2CSV `libs/` directory
6. **Build XML2CSV**: `cd XMLFastReader/XML2CSV && ./gradlew jar`

## **Contributing**

### **Adding Features**

1. **Fork the repository**
2. **Create feature branch**
3. **Implement and test**
4. **Run full test suite**
5. **Create pull request**

### **Reporting Issues**

- **File Issues**: [GitHub Issues](https://github.com/yourusername/xmlFastParser/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/xmlFastParser/discussions)
- **Email**: `support@yourcompany.com`

## **License**

This software is licensed under the MIT License. See `LICENSE` file for details.

## **Contact**

For build issues or questions:
- **Documentation**: `XMLFastParser/documentation/README.md`
- **Support**: File an issue on GitHub
- **Team**: `dev-team@yourcompany.com`

---

*This document is maintained by the xmlFastParser development team. Last updated: $(date -Iseconds)*