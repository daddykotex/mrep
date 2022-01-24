# Background

This projects intends to help with the management of multiple repositories.

# Usage

`mrep` can be used in two modes via the CLI:

- single operation
- configured operations

## Single Operation

The single operation is a subset of the configured operations mode. It's a mode in which one operation can be run directly via the CLI without a configuration.

To learn more about operations. See [operations](#operations) below.

## Configured operations

In this mode, you use `mrep` with a configuration file. This mode is the most flexible and will allow `mrep` to perform multiple operations of multiple repositories.

# Operations

An operation is defined as a set of command to run into a repository. An operation will result in a commit.

# Configuration

`mrep` stores it's global configuration in the $HOME directory. This can be overriden via a flag.

## Use cases

### Delete a file in multiple repo

I have a tree with a bunch of repositories. These repositories are very similar. In this case, they're GitOps repository describing the same distributed application in multiple clusters. I want to remove one file that exists in all of the repository and open a MR for each.

### Run a command in multiple repository

I have a directory that contains multiple repositories. Most of these are scala projects and I would like to open each project generate a dependency list and dump it in a json file.

- gate to filter scala projects
- command to run at the repository root

### Search

I want to run a search tool over multiple repositories: `mrep search --config tata.json` or `mrep search folder1/ folder2/`. it pops a browser with an interactive UI to search through your folders via `rga`: https://github.com/phiresky/ripgrep-all.
