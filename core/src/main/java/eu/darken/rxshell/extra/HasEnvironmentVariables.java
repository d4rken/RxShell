package eu.darken.rxshell.extra;

import java.util.Collection;

public interface HasEnvironmentVariables {

    Collection<EnvVar<String, String>> getEnvironmentVariables(boolean root);
}
