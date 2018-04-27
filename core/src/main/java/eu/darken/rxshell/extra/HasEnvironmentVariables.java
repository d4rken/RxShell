package eu.darken.rxshell.extra;

import java.util.Collection;

public interface HasEnvironmentVariables {

    Collection<Pair<String, String>> getEnvironmentVariables(boolean root);
}
