package dev.taskflow.cli;

import picocli.CommandLine.IVersionProvider;

/** Reads the version stamped into the jar manifest by the build; "dev" when run from classes. */
public final class VersionProvider implements IVersionProvider {
  @Override
  public String[] getVersion() {
    String version = VersionProvider.class.getPackage().getImplementationVersion();
    return new String[] {"taskflow " + (version == null ? "dev" : version)};
  }
}
