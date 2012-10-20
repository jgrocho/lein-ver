# lein-ver

A Leiningen plugin for handling a project's version.

It should be easy to update a project's version.
It should be easy to refer to a project's version from within the code.
This lein plugin, helps a developer realize these two goals.
It does so in a way that is consistent with
[semantic versioning](http://semver.org/).

## Usage

Put `[lein-ver "1.0.0"]`
into the `:plugins` vector of your `:user` profile.
If you're on Leiningen 1.x do `lein plugin install lein-ver 1.0.0`.

`lein-ver` is used to manage a project's version.
It stores this information in two places:
`project.clj` and `resources/VERSION`.
The version found in `resources/VERSION` is considered authoritative.
While, `projcet.clj` is a clojure file,
and should be able to load version information from `resources/VERSION`,
I found that not all tools treat it as such.
So it is better to specify the version as a string inside `project.clj`.
Therefore, `lein-ver` operates on both files.

The first time you use `lein-ver` on a project, run

    $ lein ver init

This will create a file (`src/project_name/version.clj`),
which takes care of loading `resources/VERSION`
and exposing its version information to the project.
Since you probably already have a version specified in `project.clj`,
this will also use that to write a version to `resources/VERSION`.
This is the only command that treats `project.clj` as authoritative,
and should only be run once for each project.

As per the semantic versioning specification,
`lein-ver` makes use of three numeric components:
major, minor, and patch;
and two alphanumeric (plus dash) components:
pre-release and build.
(Please see the [spec](http://semver.org/)
for more information on the meaning of each component.)

Now you can run `lein ver write`
to specify each of these five components.
Any component not specified, defaults to `nil`,
meaning it will not be a part of the version.
For example, to set a version of "1.3.2-rc.2", run

    $ lein ver write :major 1 :minor 3 :patch 2 :pre-release rc.2

The major, minor, and patch components can only be numeric.
It is not an error to give non-numeric data,
but that component will default to nil.
Any string can be given for the pre-release or build components.
The only string that receives special treatment is "nil",
which will be interpreted as thought that component were not given.
Thus it is not possible to use the string literal "nil",
for any component, without editing `resources/VERSION` by hand.

The command `lein ver bump` can be used to increase one of the
major, minor, or patch components
while simultaneously resetting the lower ones.
The `bump` command takes exactly one of
`:major`, `:minor`, or `:patch`.
When `:major` is given, it is increased by `1`,
and `:minor` and `:patch` are set to `0`.
When `:minor` is given, it is increased by `1`,
and `:patch` is set to `0`.
When `:patch` is given, it is increased by `1`.
In all cases, `:pre-release` and `:build` are set to nil.
For example, if the current version is "1.3.2", running

    $ lein ver bump :minor

results in a version of "1.4.0".

Also available is `lein ver set`, which functions similarly to `write`,
but instead of defaulting any unspecified components to nil,
they are preserved.
This makes it easy to modify the pre-release or build components,
(as they can't be changed by `bump`)
without altering the major, minor, or patch components.
For example, if the current version is "1.3.2-rc.2+build.13", running

    $ lein ver set :minor 5

results in a version of "1.5.2-rc.2+build.13".

At any time, running

    $ lein ver

prints out the current version in the format specified by semver.
That is like

    X.Y.Z-pre-release+build

where X, Y, Z, pre-release, and build are the
major, minor, patch, pre-release, and build components, respectively.
This is the format that can be found in `project.clj`.

The `lein ver check` command, can be used to verify that
`project.clj` and `resources\VERSION` have the same version.
If they do match, nothing is output and the command returns `0`.
Otherwise, a message is output on standard error, and it returns `1`.

### In your project

After running `lein ver init`,
the file `src\project_name\version.clj`
takes care of loading `resources\VERSION`
and exposes version information inside your project.

To use, just `use` or `require` the `project-name.version` namespace.
That namespace defines a map `version` as well as the vars
`major`, `minor`, `patch`, `pre-release`, and `build`.
It also defines `string` which returns a string of the version,
in the same format as given by `lein ver` or found in `project.clj`.

## License

Copyright © 2012 Jonathan Grochowski

Distributed under the Eclipse Public License, the same as Clojure.
