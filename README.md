# OkcAgent

![Build status](https://github.com/DDoSolitary/OkcAgent/workflows/.github/workflows/build.yml/badge.svg)

A utility that makes OpenKeychain available in your Termux shell.

## Features

This tool acts as a bridge/proxy between Termux and OpenKeychain, enabling you to perform crypto operations in Termux using your keys stored in OpenKeychain, like:

- authenticate SSH connections
- sign/verify/encrypt/decrypt messages

This tool implements the existing protocols in this field so you can seamlessly integrate it with other command line utilities like `ssh` and `git`.

## Demonstration

![](https://i.ibb.co/xsSP7X7/okc-ssh-agent-demo.gif)
![](https://i.ibb.co/DYFcYqD/okc-gpg-demo.gif)

## Install

This project consists of two components: the OkcAgent app, and command line utilities to be used in the Termux shell. You need to install both of them to make it work.

### The OkcAgent app

- Stable releases
  - [GitHub Releases](https://github.com/DDoSolitary/OkcAgent/releases)
  - [Google Play Store](https://play.google.com/store/apps/details?id=org.ddosolitary.okcagent)
  - [F-Droid](https://f-droid.org/packages/org.ddosolitary.okcagent/)
- [Dev releases](https://dl.bintray.com/ddosolitary/dev-releases/OkcAgent/)

### Command line utilities

- [Stable releases](https://github.com/DDoSolitary/okc-agents/releases)
- [Dev releases](https://dl.bintray.com/ddosolitary/dev-releases/okc-agents/)

## How to use

1. Install OpenKeychain, Termux and the necessary components mentioned above.
2. Open the app and configure the keys to be used for crypto operations.
3. Use the command line utilities.
    - `okc-ssh-agent` acts as an SSH agent. You can specify path of the agent socket with its first argument, and set `SSH_AUTH_SOCK` to that path to inform programs like `ssh` to connect to it.
    - `okc-gpg` supports a limited set of GPG options so you can use it to perform some crypto operations. Read [GpgArguments.kt](https://github.com/DDoSolitary/OkcAgent/blob/master/app/src/main/java/org/ddosolitary/okcagent/gpg/GpgArguments.kt) for a complete list of supported options.


## Notes about the app

This app is available in Play Store at the price of $1. I didn't intend to make profit from this project and simply consider it as a way of donation. If you don't want to pay, you can always download the dev releases for free using the links mentioned above, or even build the app from its source code. However, please note that the APK files from these two sources are signed with different keys, which means that you have to uninstall the existing app first if you want to switch between them.
