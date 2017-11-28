package eu.darken.rxshell.extra;


import android.support.v4.util.Pair;

import java.util.Collection;

public interface HasEnvironmentVariables {

    Collection<Pair<String, String>> getEnvironmentVariables(boolean root);
}
