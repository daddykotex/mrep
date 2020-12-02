# Background

This project is a sample functional Scala application. In this project, I explore different programming techniques while still working on something that can be useful.

# Why

Ebox provides no warning when your consumption reaches specific threshold and thus you are likely going to pay extra charges if you go above 100%. This CLI could be hooked into a cronjob to notify you if your consumption is getting close to a 100%.

## help

```
> ebox-cli/target/native-image/ebox-cli
Missing expected command (get-usage)!

Usage: ebox-cli get-usage

Simple CLI tool get info on your ebox account

Options and flags:
    --help
        Display this help text.
    --version, -v
        Print the version number and exit.

Subcommands:
    get-usage
        Returns a percentage of your bandwidth usage.
```

## get-usage

```
> ebox-cli/target/native-image/ebox-cli get-usage
Missing expected positional argument!

Usage: ebox-cli get-usage <account-number> <password>

Returns a percentage of your bandwidth usage.

Options and flags:
    --help
        Display this help text.
```

# Techniques

## GraalVM

Scala is a JVM language and while it has it's upside, it also has downsides. One of them, slow startup, is most likely why JVM languages are most of the time discarded when writing a CLI tool. Loading the VM adds significant latency when issueing a command to a CLI and thus, user experience is poor.

Graal VM (and it's native-image tool) provides a way to build executable that embeds a different VM to provide better start up. This project uses this technology to build a CLI that is responsive.

See this [commit](https://github.com/daddykotex/ebox/commit/ceace200a0f829ae7184dd90cac71b2de2b2d5eb) for more details.
