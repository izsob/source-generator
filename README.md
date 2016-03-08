# source-generator

The purpose of this project is to _generate automatically_ Java codes based on the existing 
source. Currently the following is supported:
- Hamcrest matcher generator for data classes
- builder generator for data classes

## Building from source

The org.sourcegenerator project is a simple Java library, build the JAR with `mvn package`. 

For the source-generator-plugin-idea project, see [the IDEA help](http://www.jetbrains.org/intellij/sdk/docs/basics/checkout_and_build_community.html) 
for setting up the environment. Finally run Build | Prepare Plugin Module to generate source-generator-plugin-idea.zip.

## Install plug-in
1. File | Settings | Plugins | Install plugin from disk...
2. Choose the previously built file.
3. Restart.

## Uninstall plug-in
1. File | Settings | Plugins
2. Find _Source Generator_
3. _Uninstall plugin_
4. _Restart_

## Setting the matcher rules
By default the `IsAnything` matcher is applied for any actual value. If an expected value is set, the 
default `is` matcher is applied. To set custom matchers for a data type, a 
`customMatchers.properties` file must be present at the source file's folder, or any parent folder. Example 
customMatchers.properties file:
```
BigDecimal = expected == null ? new IsNull<BigDecimal>() : closeTo(expected, new BigDecimal("0.001"));
Date = comparesEqualTo(expected)
```

## Usage
1. Find a Java class with public fields in Project view.
2. Right click | Generate matcher for data class.
3. Close the package and re-open to see the newly generated class in IDEA. For example for a UserData.java file UserDataMatcher.java will be generated.
4. Check and complete or fix the generated file.

## Limitations
- Currently getters and setters are not supported, data fields must be public.
- Primitive types are not supported. (The code will be generated, but will contain compile errors, manual fix is required.)
- Matchers from the _Matchers_ class is supported by default. Other code will be generated, but the imports must be corrected manually.

WARNING: the code is in alpha status, use with caution! It may eat your source files.
