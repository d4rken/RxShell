# RxShell
[ ![Build Status](https://travis-ci.org/d4rken/RxShell.svg?branch=master)](https://travis-ci.org/d4rken/RxShell)
[ ![Download](https://api.bintray.com/packages/darken/maven/rxshell/images/download.svg) ](https://bintray.com/darken/maven/rxshell/_latestVersion)
[![Coverage Status](https://coveralls.io/repos/github/d4rken/RxShell/badge.svg?branch=pr-coveralls)](https://coveralls.io/github/d4rken/RxShell?branch=pr-coveralls)

A library that helps your app interact with shells on Android.

## Quickstart
Include the library in your modules `build.gradle` file:
```groovy
implementation 'eu.darken.rxshell:core:0.9.2'
```

Now your project is ready to use the library, let's quickly talk about a few core concepts:

1. You construct a shell using `RxCmdShell.builder()`.
2. The shell has to be opened before use (`shell.open()`), which will launch the process and give you a `RxCmdShell.Session` to work with.
3. Build your commands with `Cmd.builder("your command")`.
4. Commands are run with `session.submit(command)`, `cmd.submit(session)` or `cmd.execute(session)`.
5. Remember to `close()` the session to release resources.

### Examples
#### Single-Shot execution
If you pass a shell builder to the command it will be used for a single shot execution.

A shell is created and opened, used and closed.

`Cmd.execute(...)` is shorthand for `Cmd.submit(...).blockingGet()`, so don't run it from a UI thread.

```java
Cmd.Result result = Cmd.builder("echo hello").execute(RxCmdShell.builder());
```

#### Reusing a shell
If you want to issue multiple commands, you can reuse the shell which is faster and uses less resources.

```java
RxCmdShell.Session session = RxCmdShell.builder().build().open().blockingGet();
// Blocking
Cmd.Result result1 = Cmd.builder("echo straw").execute(session);
// Async
Cmd.builder("echo berry").submit(session).subscribe(result -> Log.i("ExitCode: " + result.getExitCode()));
shell.close().blockingGet();
```

The default shell process is launched using `sh`, if you want to open a root shell (using `su`) tell the ShellBuilder!
```java
Cmd.Result result = Cmd.builder("echo hello").execute(RxCmdShell.builder().root(true));
```

## Used by
* [SD Maid](https://github.com/d4rken/sdmaid-public), which was also the motivation for this library.

## Alternatives
While this is obviously :^) the best library, there are alternatives you could be interested in:

* [@Chainfire's](https://twitter.com/ChainfireXDA) [libsuperuser](https://github.com/Chainfire/libsuperuser)
* [@SpazeDog's](https://github.com/SpazeDog) [rootfw](https://github.com/SpazeDog/rootfw)
