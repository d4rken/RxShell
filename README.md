# RxShell
A library that helps your app interact with shells on Android.

## How to use
Include the library in your `build.gradle` file:
```groovy
compile 'eu.darken.rxshell:core:+'
```

Now your project is ready to use the library, let's quickly talk about a few general points:

1. You construct a shell using `RxCmdShell.builder()`.

2. The shell has to be opened before use: `shell.open()`. This will launch the necessary processes and threads and give you a an `RxCmdShell.Session` object.

3. Build your commands with `Cmd.builder("your command")`.

4. Run the commands through your shell session with `session.submit(command)` or use the convenience methods `cmd.submit(session)` or `cmd.execute(session)`.
You can also pass a shell builder instead of a session if a new session should be opened and closed for the command.

5. Remember to `close()` the session to release resources after you are done.


### Examples
If you pass a shell builder to the command it will be used for a single shot execution.
A shell is created and opened, used and closed.
`Cmd.execute(...)` is shorthand for `Cmd.submit(...).blockingGet()`, so don't run it from a UI thread.

```java
Cmd.Result result = Cmd.builder("echo hello").execute(RxCmdShell.builder());
```

If you want to issue multiple commands reuse the shell as it's faster and uses less resources.

```java
RxCmdShell.Session session = RxCmdShell.builder().build().open().blockingGet();

Cmd.Result result1 = Cmd.builder("echo straw").execute(session); // Blocking
Single<Cmd.Result> result2 = Cmd.builder("echo berry").submit(session); // Async

shell.close().blockingGet();
```

The default shell is opened using `sh`, if you want to open a root shell (using `su`) tell the ShellBuilder!
```java
Cmd.Result result = Cmd.builder("echo hello").execute(RxCmdShell.builder().root(true));
```

## Used by
* [SD Maid](https://github.com/d4rken/sdmaid-public) which was also the motivation for this library.

## Alternatives
While this is obviously :^) the best library, there are alternatives you could be interested in:

* [@Chainfire's](https://twitter.com/ChainfireXDA) [libsuperuser](https://github.com/Chainfire/libsuperuser)
* [@SpazeDog's](https://github.com/SpazeDog) [rootfw](https://github.com/SpazeDog/rootfw)


