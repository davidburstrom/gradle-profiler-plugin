/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.profiler.internal;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class EnableProfilingTask extends DefaultTask {

    private Property<String> asyncProfilerLocation;
    private ListProperty<String> asyncProfilerParameters;

    void apply(ProfilerConfigurationExtension configuration) {
        asyncProfilerLocation = configuration.getAsyncProfilerLocation();
        asyncProfilerParameters = configuration.getAsyncProfilerParameters();
    }

    @TaskAction
    public void enableProfiling() {
        verifyConfiguration();
        try {
            writeProfileScriptToGradleInit();
            appendRootLocationToPreferences();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyConfiguration() {
        if (!new File(asyncProfilerLocation.get()).exists()) {
            throw new org.gradle.api.InvalidUserCodeException("Async Profiler location not declared.\n\n" +
                    "Download Async Profiler (https://github.com/jvm-profiling-tools/async-profiler) and place it in the " + asyncProfilerLocation.get() + " directory.\n" +
                    "Use the following snippet to configure custom installation paths:\n\n" +
                    "profiler {\n" +
                    "  asyncProfilerLocation = \"/path/to/async/profiler\"\n" +
                    "}");
        }
    }

    private void writeProfileScriptToGradleInit() throws IOException {
        File profileScript = new File(getProject().getGradle().getGradleUserHomeDir(), Constants.LOCATION_CUSTOM_INIT_SCRIPT);
        profileScript.getParentFile().mkdirs();
        String profileScriptContent = CharStreams.toString(new InputStreamReader(getClass().getResource("/profiler.gradle").openStream(), Charsets.UTF_8));
        profileScriptContent = profileScriptContent
                .replaceAll("%async.profiler.location%", asyncProfilerLocation.get())
                .replaceAll("%global.preferences.file%", Constants.LOCATION_GLOBAL_PREFERENCES_FILE)
                .replaceAll("%async.profiler.parameters%", asyncProfilerParameters.get().stream().collect(Collectors.joining(" ")));
        Files.asCharSink(profileScript, Charsets.UTF_8).write(profileScriptContent);
    }

    private void appendRootLocationToPreferences() throws IOException {
        File preferences = new File(Constants.LOCATION_GLOBAL_PREFERENCES_FILE);
        preferences.getParentFile().mkdirs();
        String content = preferences.exists() ? CharStreams.toString(new FileReader(preferences)) : "";
        String rootLocation = getProject().getRootProject().getProjectDir().getAbsolutePath();
        if (!content.contains(rootLocation)) {
            Files.asCharSink(preferences, Charsets.UTF_8, FileWriteMode.APPEND).write(Constants.LINE_SEPARATOR + rootLocation);
        }
    }
}